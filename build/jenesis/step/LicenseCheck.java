package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Json;
import build.jenesis.License;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDependencyKey;

public class LicenseCheck implements BuildStep {

    public enum Unknown {
        IGNORE, WARN, FAIL
    }

    private final SequencedSet<String> allowed;
    private final SequencedSet<String> denied;
    private final Unknown unknown;
    private final Map<String, String> overrides;

    public LicenseCheck() {
        this(null,
                null,
                Unknown.FAIL,
                Map.of());
    }

    private LicenseCheck(SequencedSet<String> allowed,
                         SequencedSet<String> denied,
                         Unknown unknown,
                         Map<String, String> overrides) {
        this.allowed = allowed;
        this.denied = denied;
        this.unknown = unknown;
        this.overrides = overrides;
    }

    public LicenseCheck allowed(SequencedSet<String> allowed) {
        return new LicenseCheck(allowed, denied, unknown, overrides);
    }

    public LicenseCheck denied(SequencedSet<String> denied) {
        return new LicenseCheck(allowed, denied, unknown, overrides);
    }

    public LicenseCheck unknown(Unknown unknown) {
        return new LicenseCheck(allowed, denied, unknown, overrides);
    }

    public LicenseCheck overrides(Map<String, String> overrides) {
        return new LicenseCheck(allowed, denied, unknown, overrides);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, List<License>> licensesByCoordinate = new TreeMap<>();
        SequencedMap<String, Path> jarByCoordinate = new LinkedHashMap<>();
        SequencedSet<String> strict = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path index = argument.folder().resolve(DEPENDENCIES);
            if (!Files.exists(index)) {
                continue;
            }
            SequencedProperties dependencies = SequencedProperties.ofFiles(index);
            Path sidecar = argument.folder().resolve(Dependencies.LICENSES);
            SequencedProperties licenses = Files.exists(sidecar)
                    ? SequencedProperties.ofFiles(sidecar)
                    : new SequencedProperties();
            Map<String, SequencedMap<Integer, String>> licensesByKey = new HashMap<>();
            for (String key : licenses.stringPropertyNames()) {
                int fieldHash = key.lastIndexOf('#');
                if (fieldHash < 0) {
                    continue;
                }
                int indexHash = key.lastIndexOf('#', fieldHash - 1);
                if (indexHash < 0) {
                    continue;
                }
                int position;
                try {
                    position = Integer.parseInt(key.substring(indexHash + 1, fieldHash));
                } catch (NumberFormatException _) {
                    continue;
                }
                licensesByKey.computeIfAbsent(key.substring(0, indexHash), _ -> new TreeMap<>())
                        .putIfAbsent(position, key.substring(0, fieldHash + 1));
            }
            for (String key : dependencies.stringPropertyNames()) {
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1), third = key.indexOf('/', second + 1);
                if (third < 0 || !key.substring(0, first).equals("main")) {
                    continue;
                }
                String coordinate = key.substring(third + 1);
                if (coordinate.substring(coordinate.lastIndexOf('/') + 1).endsWith("-SNAPSHOT")
                        || licensesByCoordinate.containsKey(coordinate)) {
                    continue;
                }
                licensesByCoordinate.put(coordinate, licensesByKey.getOrDefault(key.substring(second + 1), Collections.emptySortedMap())
                        .values()
                        .stream()
                        .map(prefix -> new License(null, null, licenses.getProperty(prefix + "name"), licenses.getProperty(prefix + "url")))
                        .toList());
                if (key.substring(second + 1, third).equals("maven")) {
                    strict.add(coordinate);
                }
                String value = dependencies.getProperty(key);
                int space = value.indexOf(' ');
                jarByCoordinate.put(coordinate,
                        argument.folder().resolve(space < 0 ? value : value.substring(0, space)).normalize());
            }
        }
        List<String> violations = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<License>> entry : licensesByCoordinate.entrySet()) {
            String coordinate = entry.getKey();
            List<License> licenses = resolve(coordinate, entry.getValue(), jarByCoordinate.get(coordinate), overrides);
            String verdict;
            if (licenses.isEmpty()) {
                if (!strict.contains(coordinate)) {
                    continue;
                }
                verdict = switch (unknown) {
                    case FAIL -> "MISSING";
                    case WARN -> "WARN";
                    case IGNORE -> "UNKNOWN";
                };
                if (unknown == Unknown.FAIL) {
                    violations.add(coordinate + " (no license)");
                }
            } else if (acceptable(licenses)) {
                verdict = "OK";
            } else {
                verdict = "DENIED";
                violations.add(coordinate + " " + describe(licenses));
            }
            builder.append(coordinate).append(" [").append(verdict).append("] ").append(describe(licenses)).append("\n");
        }
        Path report = Files.createDirectories(context.next().resolve(REPORTS + "compliance"));
        Files.writeString(report.resolve("licenses.txt"), builder.toString());
        if (!violations.isEmpty()) {
            throw new IllegalStateException("Disallowed dependency licenses: " + String.join(", ", violations));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static List<License> resolve(String coordinate, List<License> declared, Path jar, Map<String, String> overrides) {
        String override = override(coordinate, overrides);
        if (override != null) {
            return List.of(new License(override, null, null, null));
        }
        if (!declared.isEmpty()) {
            return declared;
        }
        return jarLicenses(jar);
    }

    private static String override(String coordinate, Map<String, String> overrides) {
        String full = "maven/" + coordinate;
        if (overrides.containsKey(full)) {
            return overrides.get(full);
        }
        try {
            MavenDependencyKey.Versioned parsed = MavenDependencyKey.tryParse(coordinate);
            return overrides.get("maven/" + parsed.key().groupId() + "/" + parsed.key().artifactId());
        } catch (RuntimeException _) {
            return null;
        }
    }

    private boolean acceptable(List<License> licenses) {
        for (License license : licenses) {
            Set<String> tokens = tokens(license.id() == null ? license.name() : license.id(), license.url());
            boolean rejected = denied != null && matches(tokens, denied);
            boolean permitted = allowed == null || matches(tokens, allowed);
            if (!rejected && permitted) {
                return true;
            }
        }
        return false;
    }

    private static boolean matches(Set<String> tokens, SequencedSet<String> policy) {
        for (String entry : policy) {
            String lower = entry.toLowerCase(Locale.ROOT);
            for (String token : tokens) {
                if (token.contains(lower)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static Set<String> tokens(String name, String url) {
        Set<String> tokens = new HashSet<>();
        if (name != null && !name.isBlank()) {
            tokens.add(name.toLowerCase(Locale.ROOT));
        }
        if (url != null && !url.isBlank()) {
            tokens.add(url.toLowerCase(Locale.ROOT));
        }
        License spdx = identify(name, url);
        if (spdx != null) {
            tokens.add(spdx.id().toLowerCase(Locale.ROOT));
            tokens.add(spdx.category());
        }
        return tokens;
    }

    private static License identify(String name, String url) {
        String text = ((name == null ? "" : name) + " " + (url == null ? "" : url)).toLowerCase(Locale.ROOT);
        if (text.isBlank()) {
            return null;
        }
        if (text.contains("affero")) {
            return new License("AGPL-3.0", "network-copyleft", null, null);
        }
        if (text.contains("lesser general public") || text.contains("lgpl")) {
            return new License("LGPL", "weak-copyleft", null, null);
        }
        if (text.contains("general public license") || text.contains("/gpl")) {
            return new License("GPL", "strong-copyleft", null, null);
        }
        if (text.contains("apache")) {
            return new License("Apache-2.0", "permissive", null, null);
        }
        if (text.contains("eclipse distribution")) {
            return new License("BSD-3-Clause", "permissive", null, null);
        }
        if (text.contains("eclipse public") || text.contains("/epl")) {
            return new License("EPL-2.0", "weak-copyleft", null, null);
        }
        if (text.contains("mozilla public") || text.contains("mpl")) {
            return new License("MPL-2.0", "weak-copyleft", null, null);
        }
        if (text.contains("common development and distribution") || text.contains("cddl")) {
            return new License("CDDL-1.1", "weak-copyleft", null, null);
        }
        if (text.contains("bsd")) {
            return new License("BSD", "permissive", null, null);
        }
        if (text.contains("mit license") || text.contains("licenses/mit") || text.contains("(mit)")) {
            return new License("MIT", "permissive", null, null);
        }
        if (text.contains("boost software")) {
            return new License("BSL-1.0", "permissive", null, null);
        }
        if (text.contains("unlicense")) {
            return new License("Unlicense", "permissive", null, null);
        }
        if (text.contains("cc0") || text.contains("public domain")) {
            return new License("CC0-1.0", "permissive", null, null);
        }
        if (text.contains("isc")) {
            return new License("ISC", "permissive", null, null);
        }
        return null;
    }

    private static List<License> jarLicenses(Path jar) {
        if (jar == null || !Files.isRegularFile(jar)) {
            return List.of();
        }
        try (JarFile file = new JarFile(jar.toFile())) {
            Manifest manifest = file.getManifest();
            if (manifest != null) {
                List<License> embedded = sbomLicenses(file, manifest);
                if (!embedded.isEmpty()) {
                    return embedded;
                }
                String bundle = bundleLicense(manifest);
                if (bundle != null) {
                    return List.of(new License(bundle, null, null, null));
                }
            }
            String text = licenseFile(file);
            return text == null ? List.of() : List.of(new License(text, null, null, null));
        } catch (IOException _) {
            return List.of();
        }
    }

    private static List<License> sbomLicenses(JarFile file, Manifest manifest) {
        String location = manifest.getMainAttributes().getValue("Sbom-Location");
        if (location == null || location.isBlank()) {
            return List.of();
        }
        JarEntry entry = file.getJarEntry(location.trim());
        if (entry == null) {
            return List.of();
        }
        Object document;
        try (InputStream in = file.getInputStream(entry)) {
            document = Json.parse(new String(in.readNBytes(1 << 24), StandardCharsets.UTF_8));
        } catch (IOException | RuntimeException _) {
            return List.of();
        }
        if (!(document instanceof Map<?, ?> root)
                || !(root.get("metadata") instanceof Map<?, ?> metadata)
                || !(metadata.get("component") instanceof Map<?, ?> component)
                || !(component.get("licenses") instanceof List<?> licenses)) {
            return List.of();
        }
        List<License> result = new ArrayList<>();
        for (Object element : licenses) {
            if (!(element instanceof Map<?, ?> wrapper)) {
                continue;
            }
            if (wrapper.get("license") instanceof Map<?, ?> license) {
                String id = string(license.get("id"));
                String name = string(license.get("name"));
                String url = string(license.get("url"));
                if (id != null) {
                    result.add(new License(id, null, null, url));
                } else if (name != null || url != null) {
                    result.add(new License(null, null, name, url));
                }
            } else {
                String expression = string(wrapper.get("expression"));
                if (expression != null) {
                    result.add(new License(expression, null, null, null));
                }
            }
        }
        return result;
    }

    private static String bundleLicense(Manifest manifest) {
        String value = manifest.getMainAttributes().getValue("Bundle-License");
        if (value == null || value.isBlank()) {
            return null;
        }
        int semicolon = value.indexOf(';');
        return (semicolon < 0 ? value : value.substring(0, semicolon)).trim();
    }

    private static String licenseFile(JarFile file) throws IOException {
        for (String name : List.of("META-INF/LICENSE", "META-INF/LICENSE.txt", "META-INF/LICENSE.md", "LICENSE", "LICENSE.txt")) {
            JarEntry entry = file.getJarEntry(name);
            if (entry == null) {
                continue;
            }
            License spdx;
            try (InputStream in = file.getInputStream(entry)) {
                spdx = identify(new String(in.readNBytes(1 << 20), StandardCharsets.UTF_8), null);
            }
            if (spdx != null) {
                return spdx.id();
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value instanceof String text && !text.isBlank() ? text : null;
    }

    private static String describe(List<License> licenses) {
        List<String> rendered = new ArrayList<>();
        for (License license : licenses) {
            String label = license.id() == null ? license.name() : license.id();
            if (label != null && !label.isBlank()) {
                rendered.add(label);
            } else if (license.url() != null && !license.url().isBlank()) {
                rendered.add(license.url());
            }
        }
        return String.join("; ", rendered);
    }
}

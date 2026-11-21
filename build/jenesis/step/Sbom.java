package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.CycloneDx;
import build.jenesis.HashDigestFunction;
import build.jenesis.License;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDependencyKey;
import java.util.jar.Attributes;

public class Sbom implements BuildStep {

    private static final Pattern CONNECTION = Pattern.compile("scm([:|])([a-z0-9]+)\\1([a-zA-Z][a-zA-Z0-9+.-]*://.+)");

    private final CycloneDx.Format format;
    private final boolean swhid;
    private final String type;
    private final String graalvmLicense;

    public Sbom() {
        this(CycloneDx.Format.JSON, false, "library", null);
    }

    private Sbom(CycloneDx.Format format, boolean swhid, String type, String graalvmLicense) {
        this.format = format;
        this.swhid = swhid;
        this.type = type;
        this.graalvmLicense = graalvmLicense;
    }

    public Sbom format(CycloneDx.Format format) {
        return new Sbom(format, swhid, type, graalvmLicense);
    }

    public Sbom swhid(boolean swhid) {
        return new Sbom(format, swhid, type, graalvmLicense);
    }

    public Sbom type(String type) {
        return new Sbom(format, swhid, type, graalvmLicense);
    }

    public Sbom graalvmLicense(String graalvmLicense) {
        return new Sbom(format, swhid, type, graalvmLicense);
    }

    public static Sbom configured(Path properties) throws IOException {
        SequencedProperties configuration = properties == null ? new SequencedProperties() : SequencedProperties.ofFiles(properties);
        String format = configuration.value("format", "json");
        boolean swhid = configuration.flag("swhid", false);
        return switch (format.toLowerCase(Locale.ROOT)) {
            case "json" -> new Sbom().format(CycloneDx.Format.JSON).swhid(swhid);
            case "xml" -> new Sbom().format(CycloneDx.Format.XML).swhid(swhid);
            case "none" -> null;
            default -> throw new IllegalArgumentException("Unknown SBOM format: " + format);
        };
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(DEPENDENCIES),
                Path.of(METADATA),
                Path.of(Dependencies.GRAPH),
                Path.of(Dependencies.LICENSES),
                Path.of(Dependencies.ARCHIVES),
                Path.of(Dependencies.RESOLVED),
                Path.of(Dependencies.SPDX),
                Path.of(RELEASE))
                || swhid && argument.hasChanged(Path.of(SOURCES), Path.of(RESOURCES)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        List<Path> folders = arguments.values().stream()
                .filter(argument -> !argument.removed())
                .map(BuildStepArgument::folder)
                .toList();
        SequencedProperties metadata = SequencedProperties.ofFolders(folders, METADATA);
        String groupId = metadata.getProperty("project");
        String artifactId = metadata.getProperty("artifact");
        String version = metadata.getProperty("version");
        HashDigestFunction hash = new HashDigestFunction("SHA-256");
        SequencedMap<String, CycloneDx.Component> components = new LinkedHashMap<>();
        List<Path> graphFiles = new ArrayList<>();
        SequencedSet<String> platforms = new LinkedHashSet<>();
        Set<Path> described = new HashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path graphFile = argument.folder().resolve(Dependencies.GRAPH);
            if (Files.isRegularFile(graphFile)) {
                graphFiles.add(graphFile);
            }
            Path release = argument.folder().resolve(RELEASE);
            if (Files.isRegularFile(release)) {
                SequencedProperties runtime = SequencedProperties.ofFiles(release);
                String implementor = unquote(runtime.value("IMPLEMENTOR")),
                        graalvm = unquote(runtime.value("GRAALVM_VERSION", runtime.value("JAVA_RUNTIME_VERSION")));
                String ref = (implementor == null ? "" : implementor + "/") + "GraalVM" + (graalvm == null ? "" : "/" + graalvm);
                if (platforms.add(ref)) {
                    components.put(ref, new CycloneDx.Component("platform",
                            ref,
                            implementor,
                            "GraalVM",
                            graalvm,
                            null,
                            List.of(),
                            null,
                            graalvmLicense == null ? List.of() : List.of(new License(graalvmLicense, null, null, null)),
                            null,
                            List.of(),
                            List.of(),
                            List.of(),
                            null,
                            null,
                            null,
                            null,
                            "required"));
                }
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
            Path located = argument.folder().resolve(Dependencies.ARCHIVES);
            SequencedProperties archives = Files.exists(located)
                    ? SequencedProperties.ofFiles(located)
                    : new SequencedProperties();
            Set<Path> runtime = new HashSet<>();
            Map<Path, String> recorded = new HashMap<>();
            for (String key : dependencies.stringPropertyNames()) {
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                String value = dependencies.getProperty(key);
                int space = value.indexOf(' ');
                Path jar = argument.folder().resolve(space < 0 ? value : value.substring(0, space)).normalize();
                if (second > 0 && key.substring(first + 1, second).equals("runtime")) {
                    runtime.add(jar);
                }
                int slash = space < 0 ? -1 : value.indexOf('/', space + 1);
                if (slash > 0 && value.substring(space + 1, slash).equalsIgnoreCase(hash.algorithm())) {
                    recorded.putIfAbsent(jar, HexFormat.of().formatHex(HexFormat.of().parseHex(value, slash + 1, value.length())));
                }
            }
            for (boolean maven : new boolean[] {true, false}) {
                for (String key : dependencies.stringPropertyNames()) {
                    int first = key.indexOf('/'), second = key.indexOf('/', first + 1), third = key.indexOf('/', second + 1);
                    if (first < 0 || second < 0 || third < 0 || maven != key.substring(second + 1, third).equals("maven")) {
                        continue;
                    }
                    String coordinate = key.substring(third + 1), licenseKey = key.substring(second + 1);
                    String value = dependencies.getProperty(key);
                    int space = value.indexOf(' ');
                    Path jar = argument.folder().resolve(space < 0 ? value : value.substring(0, space)).normalize();
                    if (components.containsKey(coordinate) || Files.isRegularFile(jar) && !described.add(jar)) {
                        continue;
                    }
                    CycloneDx.Component component = component(coordinate,
                            !Files.exists(jar) ? null
                                    : recorded.containsKey(jar) ? recorded.get(jar)
                                    : HexFormat.of().formatHex(hash.hash(jar)),
                            readLicenses(licenses, licenseKey)).scope(runtime.contains(jar) ? "required" : "excluded");
                    String source = archives.getProperty(licenseKey);
                    components.put(coordinate, source == null
                            ? component
                            : component.externalReferences(List.of(
                                    new CycloneDx.ExternalReference("source-distribution", source))));
                }
            }
        }
        CycloneDx.Component project = null;
        String projectRef = null;
        if (artifactId != null) {
            projectRef = (groupId == null ? "" : groupId + "/") + artifactId + (version == null ? "" : "/" + version);
            String purl = groupId == null
                    ? null
                    : "pkg:maven/" + groupId + "/" + artifactId + (version == null ? "" : "@" + version);
            String tag = metadata.value("scm.tag"), revision = metadata.value("scm.revision");
            if ("HEAD".equals(tag)) {
                tag = null;
            }
            List<CycloneDx.Property> properties = new ArrayList<>();
            List<String> swhids = new ArrayList<>();
            if (tag != null) {
                properties.add(new CycloneDx.Property("jenesis:scm:tag", tag));
            }
            if (revision != null) {
                properties.add(new CycloneDx.Property("jenesis:scm:revision", revision));
            }
            String tree = metadata.value("scm.tree");
            if (tree != null) {
                if (!tree.matches("[0-9a-f]{40}")) {
                    throw new IllegalArgumentException("The tree of a release must be the 40-character id of a Git"
                            + " tree, as `git rev-parse HEAD^{tree}` prints it: " + tree);
                }
                swhids.add("swh:1:dir:" + tree);
            }
            if (swhid) {
                List<Path> roots = new ArrayList<>();
                for (Path folder : folders) {
                    for (Path root : List.of(folder.resolve(SOURCES), folder.resolve(RESOURCES))) {
                        if (Files.isDirectory(root)) {
                            roots.add(root);
                        }
                    }
                }
                String identifier = swhid(roots);
                if (identifier != null) {
                    swhids.add(identifier);
                }
            }
            SequencedSet<String> declared = new LinkedHashSet<>();
            for (String key : metadata.stringPropertyNames()) {
                if (key.startsWith("license.") && key.lastIndexOf('.') > "license.".length()) {
                    declared.add(key.substring(0, key.lastIndexOf('.')));
                }
            }
            Map<String, String> aliases = Dependencies.aliases(folders);
            List<License> licenses = new ArrayList<>();
            for (String license : declared) {
                licenses.add(new License(null,
                        null,
                        metadata.getProperty(license + ".name"),
                        metadata.getProperty(license + ".url")).identified(aliases));
            }
            project = new CycloneDx.Component(type,
                    projectRef,
                    groupId,
                    artifactId,
                    version,
                    purl,
                    swhids,
                    null,
                    licenses,
                    metadata.getProperty("description"),
                    developers(metadata),
                    references(metadata, revision == null ? tag : revision),
                    properties,
                    organization(metadata, "organization"),
                    metadata.value("copyright"),
                    organization(metadata, "manufacturer"),
                    metadata.value("publisher"),
                    null);
        }
        List<CycloneDx.Dependency> dependencies = relationships(projectRef, components.keySet(), platforms, graphFiles);
        String document = new CycloneDx().emit(format, project, new ArrayList<>(components.values()), dependencies);

        String base = artifactId == null ? "bom" : artifactId,
                location = "META-INF/sbom/" + (groupId == null ? "" : groupId + "/") + base + "." + format.extension();
        Path embedded = context.next().resolve(RESOURCES).resolve(location);
        Files.createDirectories(embedded.getParent());
        Files.writeString(embedded, document);
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Sbom-Format", "CycloneDX");
        manifest.getMainAttributes().putValue("Sbom-Location", location);
        try (OutputStream out = Files.newOutputStream(context.next().resolve(Versions.MANIFEST))) {
            manifest.write(out);
        }
        Path standalone = Files.createDirectories(context.next().resolve(REPORTS + "sbom"));
        Files.writeString(standalone.resolve(base + (version == null ? "" : "-" + version) + "." + format.extension()), document);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static CycloneDx.Component component(String coordinate, String sha256, List<License> licenses) {
        try {
            MavenDependencyKey.Versioned parsed = MavenDependencyKey.tryParse(coordinate);
            if (parsed.version() != null) {
                MavenDependencyKey key = parsed.key();
                StringBuilder purl = new StringBuilder("pkg:maven/")
                        .append(key.groupId()).append("/").append(key.artifactId()).append("@").append(parsed.version());
                List<String> qualifiers = new ArrayList<>();
                if (key.type() != null && !key.type().equals("jar")) {
                    qualifiers.add("type=" + key.type());
                }
                if (key.classifier() != null) {
                    qualifiers.add("classifier=" + key.classifier());
                }
                if (!qualifiers.isEmpty()) {
                    purl.append("?").append(String.join("&", qualifiers));
                }
                return new CycloneDx.Component(coordinate,
                        key.groupId(),
                        key.artifactId(),
                        parsed.version(),
                        purl.toString(),
                        sha256,
                        licenses);
            }
        } catch (RuntimeException _) {
        }
        int last = coordinate.lastIndexOf('/');
        return new CycloneDx.Component(coordinate,
                null,
                last < 0 ? coordinate : coordinate.substring(0, last),
                last < 0 ? "" : coordinate.substring(last + 1),
                null,
                sha256,
                licenses);
    }

    private static List<CycloneDx.Dependency> relationships(String projectRef,
                                                                   Set<String> componentRefs,
                                                                   SequencedSet<String> platforms,
                                                                   List<Path> graphFiles) throws IOException {
        if (graphFiles.isEmpty() && (platforms.isEmpty() || projectRef == null)) {
            return List.of();
        }
        SequencedMap<String, SequencedSet<String>> dependsOn = new LinkedHashMap<>();
        if (projectRef != null) {
            dependsOn.put(projectRef, new LinkedHashSet<>(platforms));
        }
        for (String ref : componentRefs) {
            dependsOn.put(ref, new LinkedHashSet<>());
        }
        for (Resolver.Resolution resolution : Dependencies.graph(graphFiles, List.of()).values()) {
            SequencedMap<String, Resolver.Vertex> vertices = resolution.vertices();
            Map<String, String> vertexKeyByCoordinate = new HashMap<>();
            for (Resolver.Edge edge : resolution.edges()) {
                vertexKeyByCoordinate.put(edge.coordinate(), stripVersion(edge.coordinate(), edge.version()));
            }
            for (Resolver.Edge edge : resolution.edges()) {
                if (!edge.followed()) {
                    continue;
                }
                String childRef = ref(stripVersion(edge.coordinate(), edge.version()), vertices);
                if (childRef == null || !componentRefs.contains(childRef)) {
                    continue;
                }
                String parentRef;
                if (edge.parent() == null) {
                    parentRef = projectRef;
                    if (parentRef == null) {
                        continue;
                    }
                } else {
                    String parentKey = vertexKeyByCoordinate.get(edge.parent());
                    parentRef = parentKey == null ? null : ref(parentKey, vertices);
                    if (parentRef == null || !dependsOn.containsKey(parentRef)) {
                        continue;
                    }
                }
                dependsOn.get(parentRef).add(childRef);
            }
        }
        List<CycloneDx.Dependency> result = new ArrayList<>();
        dependsOn.forEach((ref, on) -> result.add(new CycloneDx.Dependency(ref, new ArrayList<>(on))));
        return result;
    }

    private static String unquote(String value) {
        String unquoted = value != null && value.length() > 1 && value.startsWith("\"") && value.endsWith("\"")
                ? value.substring(1, value.length() - 1).trim()
                : value;
        return unquoted == null || unquoted.isEmpty() ? null : unquoted;
    }

    private static String ref(String vertexKey, SequencedMap<String, Resolver.Vertex> vertices) {
        Resolver.Vertex vertex = vertices.get(vertexKey);
        if (vertex == null || vertex.resolvedVersion() == null) {
            return null;
        }
        int slash = vertexKey.indexOf('/');
        return (slash < 0 ? vertexKey : vertexKey.substring(slash + 1)) + "/" + vertex.resolvedVersion();
    }

    private static String stripVersion(String coordinate, String version) {
        return version != null && !version.isEmpty() && coordinate.endsWith("/" + version)
                ? coordinate.substring(0, coordinate.length() - version.length() - 1)
                : coordinate;
    }

    private static List<License> readLicenses(SequencedProperties licenses, String licenseKey) {
        SequencedMap<Integer, String> byIndex = new TreeMap<>();
        String prefix = licenseKey + "#";
        for (String key : licenses.stringPropertyNames()) {
            if (!key.startsWith(prefix)) {
                continue;
            }
            String rest = key.substring(prefix.length());
            int hash = rest.indexOf('#');
            if (hash < 0) {
                continue;
            }
            int index;
            try {
                index = Integer.parseInt(rest.substring(0, hash));
            } catch (NumberFormatException _) {
                continue;
            }
            byIndex.putIfAbsent(index, prefix + rest.substring(0, hash + 1));
        }
        return byIndex.values().stream().map(entry -> new License(licenses.getProperty(entry + "id"),
                licenses.getProperty(entry + "category"),
                licenses.getProperty(entry + "name"),
                licenses.getProperty(entry + "url"))).toList();
    }

    private static CycloneDx.Organization organization(SequencedProperties metadata, String prefix) {
        String name = metadata.value(prefix + ".name"), url = metadata.value(prefix + ".url");
        return name == null && url == null ? null : new CycloneDx.Organization(name, url);
    }

    private static List<CycloneDx.Author> developers(SequencedProperties metadata) {
        SequencedSet<String> developers = new LinkedHashSet<>();
        for (String key : metadata.stringPropertyNames()) {
            if (key.startsWith("developer.") && key.lastIndexOf('.') > "developer.".length()) {
                developers.add(key.substring(0, key.lastIndexOf('.') + 1));
            }
        }
        return developers.stream()
                .map(developer -> new CycloneDx.Author(metadata.getProperty(developer + "name"), metadata.getProperty(developer + "email")))
                .filter(author -> author.name() != null || author.email() != null)
                .toList();
    }

    private static List<CycloneDx.ExternalReference> references(SequencedProperties metadata, String reference) {
        List<CycloneDx.ExternalReference> references = new ArrayList<>();
        String url = metadata.getProperty("url");
        if (url != null) {
            references.add(new CycloneDx.ExternalReference("website", url));
        }
        String scm = metadata.getProperty("scm.url");
        if (scm != null) {
            references.add(new CycloneDx.ExternalReference("vcs", scm));
        }
        String connection = metadata.value("scm.connection");
        if (reference != null && connection != null) {
            Matcher matcher = CONNECTION.matcher(connection);
            if (matcher.matches()) {
                references.add(new CycloneDx.ExternalReference("vcs",
                        matcher.group(2) + "+" + matcher.group(3) + "@" + reference));
            }
        }
        return references;
    }

    private static String swhid(List<Path> roots) throws IOException {
        Directory tree = new Directory();
        SequencedMap<String, Path> origins = new TreeMap<>();
        for (Path root : roots) {
            List<Path> files;
            try (Stream<Path> walk = Files.walk(root)) {
                files = walk.filter(Files::isRegularFile).toList();
            }
            for (Path file : files) {
                String name = root.relativize(file).toString().replace(File.separatorChar, '/');
                Path origin = origins.putIfAbsent(name, file);
                if (origin != null) {
                    if (Files.mismatch(origin, file) != -1) {
                        throw new IllegalStateException("Two source roots contain " + name + " with different content: "
                                + origin + " and " + file);
                    }
                    continue;
                }
                Directory directory = tree;
                String[] segments = name.split("/");
                for (int index = 0; index < segments.length - 1; index++) {
                    directory = directory.directories().computeIfAbsent(segments[index], _ -> new Directory());
                }
                directory.files().put(segments[segments.length - 1], file);
            }
        }
        return origins.isEmpty() ? null : "swh:1:dir:" + HexFormat.of().formatHex(tree.id());
    }

    private static byte[] blobId(Path file) throws IOException {
        MessageDigest digest = sha1();
        digest.update(("blob " + Files.size(file) + "\0").getBytes(StandardCharsets.US_ASCII));
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(OutputStream.nullOutputStream());
        }
        return digest.digest();
    }

    private static MessageDigest sha1() {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-1 is required to compute a SWHID", e);
        }
    }

    private record Directory(SequencedMap<String, Directory> directories, SequencedMap<String, Path> files) {

        private Directory() {
            this(new TreeMap<>(), new TreeMap<>());
        }

        private byte[] id() throws IOException {
            List<String> names = new ArrayList<>(files.keySet());
            directories.keySet().forEach(name -> names.add(name + "/"));
            names.sort((left, right) -> Arrays.compareUnsigned(
                    left.getBytes(StandardCharsets.UTF_8),
                    right.getBytes(StandardCharsets.UTF_8)));
            ByteArrayOutputStream content = new ByteArrayOutputStream();
            for (String name : names) {
                byte[] id;
                if (name.endsWith("/")) {
                    name = name.substring(0, name.length() - 1);
                    content.write("40000 ".getBytes(StandardCharsets.US_ASCII));
                    id = directories.get(name).id();
                } else {
                    content.write("100644 ".getBytes(StandardCharsets.US_ASCII));
                    id = blobId(files.get(name));
                }
                content.write(name.getBytes(StandardCharsets.UTF_8));
                content.write(0);
                content.write(id);
            }
            MessageDigest digest = sha1();
            digest.update(("tree " + content.size() + "\0").getBytes(StandardCharsets.US_ASCII));
            digest.update(content.toByteArray());
            return digest.digest();
        }
    }
}

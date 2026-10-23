package build.jenesis;

import module java.base;
import java.util.jar.Attributes;

public enum PathPlacement {

    CLASS_PATH(false) {
        @Override
        public boolean test(Path path) {
            return false;
        }

        @Override
        public PathPlacement forModuleInfo(boolean moduleInfoPresent) {
            return moduleInfoPresent ? INFERRED : this;
        }
    },

    MODULE_PATH(true) {
        @Override
        public boolean test(Path path) {
            return true;
        }
    },

    INFERRED(true) {
        @Override
        public boolean test(Path path) {
            return moduleDescriptor(path) != null;
        }
    };

    public static final String ALIASES = "Jenesis-Aliases",
            OVERRIDES = "Jenesis-Overrides",
            LAYERS = "Jenesis-Layer",
            NATIVE_ACCESS = "Jenesis-Native-Access",
            PREVIEW = "Jenesis-Preview";
    private static final Pattern DERIVED_VERSION = Pattern.compile("\\d+(\\..*)?");

    private final boolean modular;

    PathPlacement(boolean modular) {
        this.modular = modular;
    }

    public boolean modular() {
        return modular;
    }

    public abstract boolean test(Path path) throws IOException;

    public static boolean automatic(Path file) {
        ModuleDescriptor descriptor = moduleDescriptor(file);
        return descriptor != null && descriptor.isAutomatic();
    }

    public PathPlacement forModuleInfo(boolean moduleInfoPresent) {
        return moduleInfoPresent ? this : CLASS_PATH;
    }

    public static String fileName(String coordinate) {
        return stem(coordinate) + ".jar";
    }

    public static String stem(String coordinate) {
        return Arrays.stream(coordinate.split("/", -1))
                .map(BuildExecutorModule::encode)
                .collect(Collectors.joining("~"));
    }

    public static String fileName(String coordinate, String module, boolean declared) {
        String version = coordinate.substring(coordinate.lastIndexOf('/') + 1);
        if (coordinate.indexOf('/') < 0 || !declared && !derivable(version)) {
            return BuildExecutorModule.encode(module) + ".jar";
        }
        return BuildExecutorModule.encode(module) + "-" + BuildExecutorModule.encode(version) + ".jar";
    }

    private static boolean derivable(String version) {
        if (!DERIVED_VERSION.matcher(version).matches()) {
            return false;
        }
        try {
            ModuleDescriptor.Version.parse(version);
            return true;
        } catch (IllegalArgumentException _) {
            return false;
        }
    }

    public static Path declaration(Path jar) {
        return jar.resolveSibling(jar.getFileName() + ".module");
    }

    public static ModuleDescriptor moduleDescriptor(Path path) {
        try {
            if (Files.isDirectory(path)) {
                Path moduleInfo = path.resolve("module-info.class");
                if (!Files.exists(moduleInfo)) {
                    return null;
                }
                try (InputStream input = Files.newInputStream(moduleInfo)) {
                    return ModuleDescriptor.read(input);
                }
            }
            try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
                JarEntry moduleInfo = jar.getJarEntry("module-info.class");
                if (moduleInfo != null) {
                    try (InputStream input = jar.getInputStream(moduleInfo)) {
                        return ModuleDescriptor.read(input);
                    }
                }
                Manifest manifest = jar.getManifest();
                String automatic = manifest == null
                        ? null
                        : manifest.getMainAttributes().getValue("Automatic-Module-Name");
                if (automatic != null) {
                    return ModuleDescriptor.newAutomaticModule(automatic).build();
                }
            }
            Path declaration = declaration(path);
            return Files.isRegularFile(declaration)
                    ? ModuleDescriptor.newAutomaticModule(Files.readString(declaration).trim()).build()
                    : null;
        } catch (IOException | RuntimeException _) {
            return null;
        }
    }

    public record Declarations(SequencedMap<String, String> aliases,
                               SequencedMap<String, SequencedSet<String>> overrides) {
    }

    public static Declarations declarations(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return new Declarations(Collections.emptyNavigableMap(), Collections.emptyNavigableMap());
        }
        String aliases, overrides;
        try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            Manifest manifest = jar.getManifest();
            aliases = manifest == null ? null : manifest.getMainAttributes().getValue(ALIASES);
            overrides = manifest == null ? null : manifest.getMainAttributes().getValue(OVERRIDES);
        } catch (ZipException _) {
            return new Declarations(Collections.emptyNavigableMap(), Collections.emptyNavigableMap());
        }
        return new Declarations(aliases(aliases, path.toString()), overrides(overrides, path.toString()));
    }

    public static SequencedMap<String, String> aliases(Path path) throws IOException {
        return declarations(path).aliases();
    }

    public static SequencedMap<String, SequencedSet<String>> layers(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return Collections.emptyNavigableMap();
        }
        String declaration;
        try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            Manifest manifest = jar.getManifest();
            declaration = manifest == null ? null : manifest.getMainAttributes().getValue(LAYERS);
        } catch (ZipException _) {
            return Collections.emptyNavigableMap();
        }
        return layers(declaration, path.toString());
    }

    public static SequencedSet<String> nativeAccess(Path path) throws IOException {
        SequencedSet<String> tokens = new LinkedHashSet<>();
        if (Files.isDirectory(path)) {
            return tokens;
        }
        String declaration;
        try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            Manifest manifest = jar.getManifest();
            declaration = manifest == null ? null : manifest.getMainAttributes().getValue(NATIVE_ACCESS);
        } catch (ZipException _) {
            return tokens;
        }
        if (declaration != null) {
            for (String token : declaration.split(",")) {
                if (!token.isBlank()) {
                    tokens.add(token.trim());
                }
            }
        }
        return tokens;
    }

    public static String preview(Path path) throws IOException {
        if (Files.isDirectory(path)) {
            return null;
        }
        try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            Manifest manifest = jar.getManifest();
            return manifest == null ? null : manifest.getMainAttributes().getValue(PREVIEW);
        } catch (ZipException _) {
            return null;
        }
    }

    public static SequencedMap<String, SequencedSet<String>> overrides(String declaration, String origin) {
        SequencedMap<String, SequencedSet<String>> overrides = new LinkedHashMap<>();
        if (declaration == null || declaration.isBlank()) {
            return overrides;
        }
        for (String entry : declaration.split(",")) {
            String pair = entry.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String module = equals < 0 ? "" : pair.substring(0, equals).trim();
            String declared = equals < 0 ? "" : pair.substring(equals + 1).trim();
            if (module.isEmpty() || declared.isEmpty()) {
                throw new IllegalArgumentException("Malformed " + OVERRIDES + " entry '"
                        + pair
                        + "' in "
                        + origin
                        + ": expected <module-name>=<module-name>[ <module-name>...]");
            }
            SequencedSet<String> carriers = new LinkedHashSet<>();
            for (String carrier : declared.split(" ")) {
                if (!carrier.isBlank()) {
                    carriers.add(carrier.trim());
                }
            }
            SequencedSet<String> previous = overrides.putIfAbsent(module, carriers);
            if (previous != null && !previous.equals(carriers)) {
                throw new IllegalArgumentException("Conflicting " + OVERRIDES + " entries for "
                        + module
                        + " in "
                        + origin
                        + ": "
                        + previous
                        + " and "
                        + carriers);
            }
        }
        return overrides;
    }

    public static SequencedMap<String, SequencedSet<String>> layers(String declaration, String origin) {
        SequencedMap<String, SequencedSet<String>> layers = new LinkedHashMap<>();
        if (declaration == null || declaration.isBlank()) {
            return layers;
        }
        for (String entry : declaration.split(",")) {
            String pair = entry.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String name = equals < 0 ? "" : pair.substring(0, equals).trim();
            String tokens = equals < 0 ? "" : pair.substring(equals + 1).trim();
            if (name.isEmpty() || tokens.isEmpty()) {
                throw new IllegalArgumentException("Malformed " + LAYERS + " entry '"
                        + pair
                        + "' in "
                        + origin
                        + ": expected <name>=<api-module> <coordinate>...");
            }
            SequencedSet<String> declared = new LinkedHashSet<>(List.of(tokens.split(" ")));
            SequencedSet<String> previous = layers.putIfAbsent(name, declared);
            if (previous != null && !previous.equals(declared)) {
                throw new IllegalArgumentException("Conflicting " + LAYERS + " entries for "
                        + name
                        + " in "
                        + origin
                        + ": "
                        + previous
                        + " and "
                        + declared);
            }
        }
        return layers;
    }

    public static SequencedMap<String, String> aliases(String declaration, String origin) {
        SequencedMap<String, String> aliases = new LinkedHashMap<>();
        if (declaration == null || declaration.isBlank()) {
            return aliases;
        }
        for (String entry : declaration.split(",")) {
            String pair = entry.trim();
            if (pair.isEmpty()) {
                continue;
            }
            int equals = pair.indexOf('=');
            String alias = equals < 0 ? "" : pair.substring(0, equals).trim();
            String target = equals < 0 ? "" : pair.substring(equals + 1).trim();
            if (alias.isEmpty() || target.isEmpty() || target.indexOf('/') < 1) {
                throw new IllegalArgumentException("Malformed " + ALIASES + " entry '"
                        + pair
                        + "' in "
                        + origin
                        + ": expected <module-name>=<groupId>/<artifactId>[/<type>[/<classifier>]]");
            }
            String previous = aliases.putIfAbsent(alias, target);
            if (previous != null && !previous.equals(target)) {
                throw new IllegalArgumentException("Conflicting " + ALIASES + " entries for "
                        + alias
                        + " in "
                        + origin
                        + ": "
                        + previous
                        + " and "
                        + target);
            }
        }
        return aliases;
    }

    public static void aliased(Path file, String alias, String origin) {
        Set<ModuleReference> found;
        try {
            found = ModuleFinder.of(file).findAll();
        } catch (FindException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalArgumentException(origin
                    + " cannot be aliased as "
                    + alias
                    + ": "
                    + cause.getMessage(), e);
        }
        ModuleDescriptor descriptor = found.size() == 1 ? found.iterator().next().descriptor() : null;
        if (descriptor == null || !descriptor.isAutomatic() || !descriptor.name().equals(alias)) {
            throw new IllegalArgumentException(origin
                    + " cannot be aliased as "
                    + alias
                    + ": "
                    + file.getFileName()
                    + (descriptor == null
                    ? " does not describe a single module"
                    : " describes the "
                    + (descriptor.isAutomatic() ? "automatic" : "named")
                    + " module "
                    + descriptor.name()));
        }
    }

    public static String mainClass(Path path) throws IOException {
        try (JarFile jar = new JarFile(path.toFile(), true, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
            Manifest manifest = jar.getManifest();
            return manifest == null ? null : manifest.getMainAttributes().getValue(Attributes.Name.MAIN_CLASS);
        }
    }
}

package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.PathPlacement;
import build.jenesis.TestFramework;
import build.jenesis.TestTags;

public record JUnitPlatform(String console) implements TestFramework {

    private static final String LAUNCHER = "org/junit/platform/console/ConsoleLauncher.class",
            JUNIT4 = "junit",
            JUPITER_API = "org.junit.jupiter.api",
            JUPITER_ENGINE = "org.junit.jupiter.engine",
            PLATFORM_COMMONS = "org.junit.platform.commons",
            PLATFORM_CONSOLE = "org.junit.platform.console",
            PLATFORM_ENGINE = "org.junit.platform.engine",
            VINTAGE_ENGINE = "org.junit.vintage.engine";
    private static final ModuleDescriptor.Version BANNER = ModuleDescriptor.Version.parse("1.5"),
            SUBCOMMANDS = ModuleDescriptor.Version.parse("1.10");

    public JUnitPlatform() {
        this(null);
    }

    public JUnitPlatform console(String console) {
        return new JUnitPlatform(console);
    }

    @Override
    public JUnitPlatform runningOn(List<Path> jars) throws IOException {
        for (Path jar : jars) {
            if (!Files.isRegularFile(jar)) {
                continue;
            }
            try (JarFile file = new JarFile(jar.toFile())) {
                if (file.getEntry(LAUNCHER) == null) {
                    continue;
                }
                Manifest manifest = file.getManifest();
                String version = manifest == null ? null : manifest.getMainAttributes().getValue("Implementation-Version");
                if (version == null) {
                    ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                    version = descriptor == null ? null : descriptor.rawVersion().orElse(null);
                }
                return console(version);
            } catch (ZipException _) {
                continue;
            }
        }
        return this;
    }

    @Override
    public String runnerModule() {
        return PLATFORM_CONSOLE;
    }

    @Override
    public boolean isMarkedBy(ModuleDescriptor module) {
        return module.name().equals(PLATFORM_ENGINE) || module.name().equals(JUPITER_API);
    }

    @Override
    public SequencedMap<String, String> missingCoordinates(List<ModuleDescriptor> modules) {
        SequencedMap<String, String> coordinates = new LinkedHashMap<>();
        if (!contains(modules, PLATFORM_CONSOLE)) {
            artifact(coordinates,
                    PLATFORM_CONSOLE,
                    "org.junit.platform/junit-platform-console",
                    version(modules, PLATFORM_ENGINE, PLATFORM_COMMONS));
        }
        if (contains(modules, JUPITER_API) && !contains(modules, JUPITER_ENGINE)) {
            artifact(coordinates,
                    JUPITER_ENGINE,
                    "org.junit.jupiter/junit-jupiter-engine",
                    version(modules, JUPITER_API));
        }
        if (contains(modules, JUPITER_API) && contains(modules, JUNIT4) && !contains(modules, VINTAGE_ENGINE)) {
            artifact(coordinates,
                    VINTAGE_ENGINE,
                    "org.junit.vintage/junit-vintage-engine",
                    version(modules, JUPITER_API));
        }
        return coordinates;
    }

    @Override
    public Set<String> reflectingModules() {
        return Set.of(PLATFORM_COMMONS, JUNIT4);
    }

    @Override
    public String runnerClass() {
        return "org.junit.platform.console.ConsoleLauncher";
    }

    @Override
    public List<String> arguments(Path supplement,
                                  Path output,
                                  SequencedSet<String> classes,
                                  SequencedMap<String, SequencedSet<String>> methods,
                                  boolean parallel,
                                  boolean reporting) {
        ModuleDescriptor.Version version = console == null ? null : ModuleDescriptor.Version.parse(console);
        if (version != null && version.compareTo(BANNER) < 0) {
            throw new IllegalStateException("The JUnit Platform console launcher " + console + " on the test path is older"
                    + " than " + BANNER + ", the first whose command line the build writes - raise the JUnit Platform to "
                    + BANNER + " or newer, as by importing a newer org.junit:junit-bom in dependencyManagement,"
                    + " or by pinning org.junit.platform/junit-platform-console and junit-platform-launcher to one");
        }
        List<String> commands = new ArrayList<>();
        if (version == null || version.compareTo(SUBCOMMANDS) >= 0) {
            commands.add("execute");
        }
        commands.addAll(List.of("--disable-banner", "--disable-ansi-colors"));
        if (parallel) {
            commands.add("--config=junit.jupiter.execution.parallel.enabled=true");
            commands.add("--config=junit.jupiter.execution.parallel.mode.default=concurrent");
        }
        if (reporting) {
            Path reports = output.resolve(BuildStep.REPORTS + "tests");
            commands.add("--reports-dir=" + reports);
            commands.add("--config=junit.platform.reporting.open.xml.enabled=true");
            commands.add("--config=junit.platform.reporting.output.dir=" + reports);
        }
        for (String className : classes) {
            commands.add("--select-class=" + className);
        }
        for (Map.Entry<String, SequencedSet<String>> entry : methods.entrySet()) {
            for (String method : entry.getValue()) {
                commands.add("--select-method=" + entry.getKey() + "#" + method);
            }
        }
        return commands;
    }

    @Override
    public List<String> tags(List<String> arguments, TestTags requested, List<TestTags> ran) {
        List<String> conditions = new ArrayList<>();
        if (!requested.all()) {
            conditions.add(disjunction(requested));
        }
        for (TestTags earlier : ran) {
            if (!earlier.all()) {
                conditions.add("!" + disjunction(earlier));
            }
        }
        List<String> selection = new ArrayList<>(arguments);
        if (!conditions.isEmpty()) {
            selection.add("--include-tag=" + String.join(" & ", conditions));
        }
        return selection;
    }

    @Override
    public List<String> engines(List<String> arguments, String engines) {
        if (engines == null) {
            return arguments;
        }
        List<String> selection = new ArrayList<>(arguments);
        for (String entry : engines.split(",")) {
            String engine = entry.strip(), id = engine.startsWith("-") ? engine.substring(1).strip() : engine;
            if (id.isEmpty() || id.chars().anyMatch(Character::isWhitespace)) {
                throw new IllegalArgumentException("The engines key of test.properties names JUnit Platform engines by their id,"
                        + " comma-separated, a leading - leaving one out, as junit-jupiter,-junit-vintage: "
                        + engines);
            }
            selection.add((engine.startsWith("-") ? "--exclude-engine=" : "--include-engine=") + id);
        }
        return selection;
    }

    private static void artifact(SequencedMap<String, String> coordinates,
                                 String module,
                                 String maven,
                                 String version) {
        coordinates.put("module/" + module, version);
        coordinates.put("maven/" + maven, version == null ? "RELEASE" : version);
    }

    private static boolean contains(List<ModuleDescriptor> modules, String name) {
        return modules.stream().anyMatch(module -> module.name().equals(name));
    }

    private static String version(List<ModuleDescriptor> modules, String... names) {
        return Stream.of(names)
                .flatMap(name -> modules.stream().filter(module -> module.name().equals(name)))
                .flatMap(module -> module.rawVersion().stream())
                .findFirst()
                .orElse(null);
    }

    private static String condition(String literal) {
        return literal.startsWith("-") ? "!" + literal.substring(1) : literal;
    }

    private static String disjunction(TestTags tags) {
        return tags.alternatives().stream()
                .map(alternative -> TestTags.literals(alternative).size() == 1
                        ? condition(alternative)
                        : TestTags.literals(alternative).stream()
                                .map(JUnitPlatform::condition)
                                .collect(Collectors.joining(" & ", "(", ")")))
                .collect(Collectors.joining(" | ", "(", ")"));
    }
}

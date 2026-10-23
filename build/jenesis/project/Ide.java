package build.jenesis.project;

import module java.base;
import module java.xml;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;

public class Ide implements BuildExecutorModule {

    public static final String IDEA = "idea", VSCODE = "vscode", ECLIPSE = "eclipse";
    private static final List<String> MAIN_SOURCES = List.of(
            "src/main/java", "src/main/kotlin", "src/main/scala", "src/main/groovy", "sources", "src");
    private static final List<String> TEST_SOURCES = List.of(
            "src/test/java", "src/test/kotlin", "src/test/scala", "src/test/groovy", "tests", "test");

    private final Path root;

    public Ide(Path root) {
        this.root = root;
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(IDEA, new Idea(root), inherited.sequencedKeySet());
        buildExecutor.addStep(VSCODE, new VsCode(root), inherited.sequencedKeySet());
        buildExecutor.addStep(ECLIPSE, new Eclipse(root), inherited.sequencedKeySet());
    }

    public record Idea(Path root) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path base = root.toAbsolutePath().normalize();
            idea(read(arguments.values(), base), base);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    public record VsCode(Path root) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path base = root.toAbsolutePath().normalize();
            vscode(read(arguments.values(), base), base);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    public record Eclipse(Path root) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            eclipse(read(arguments.values(), root.toAbsolutePath().normalize()));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static List<Module> read(Collection<BuildStepArgument> arguments, Path base) throws IOException {
        List<Raw> raws = new ArrayList<>();
        SequencedMap<String, String> identities = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String prefix : Inventory.prefixes(inventory)) {
                if (inventory.value(prefix + ".packaging") != null) {
                    continue;
                }
                String path = inventory.getProperty(prefix + ".path");
                Path content = path.isEmpty() ? base : base.resolve(path).normalize();
                String module = inventory.getProperty(prefix + ".module");
                boolean modular = module != null && !module.isEmpty();
                String name = modular ? module : name(path, base);
                String declared = inventory.getProperty(prefix + ".release");
                Integer release = declared == null || declared.isEmpty() ? null : Integer.valueOf(declared);
                boolean fixture = inventory.getProperty(prefix + ".abstract") != null;
                boolean test = !fixture && inventory.getProperty(prefix + ".test") != null;
                List<String> coordinates = new ArrayList<>();
                List<Path> jars = new ArrayList<>();
                for (int index = 0; ; index++) {
                    String value = inventory.getProperty(prefix + ".dependency." + index);
                    if (value == null) {
                        break;
                    }
                    String group = inventory.getProperty(prefix + ".dependency." + index + ".group");
                    String[] parts = value.split(" ");
                    if (group != null && !group.equals("main") || parts[0].contains("/pom/")) {
                        continue;
                    }
                    coordinates.add(parts[0]);
                    jars.add(argument.folder().resolve(parts[1]).toAbsolutePath().normalize());
                }
                for (String key : inventory.stringPropertyNames()) {
                    if (key.startsWith(prefix + ".identity.")) {
                        String coordinate = inventory.getProperty(key);
                        if (!coordinate.contains("/pom/")) {
                            identities.putIfAbsent(coordinate, name);
                        }
                    }
                }
                raws.add(new Raw(name,
                        content,
                        modular,
                        test,
                        release,
                        coordinates,
                        jars,
                        Inventory.paths(inventory, argument.folder(), prefix + ".generated").stream()
                                .map(generated -> generated.toAbsolutePath().normalize())
                                .toList()));
            }
        }
        Map<Path, Raw> folded = new HashMap<>();
        Set<Path> mains = raws.stream().filter(raw -> !raw.test()).map(Raw::content).collect(Collectors.toSet());
        for (Raw raw : raws) {
            if (raw.test() && mains.contains(raw.content())) {
                folded.put(raw.content(), raw);
            }
        }
        List<Module> modules = new ArrayList<>();
        for (Raw raw : raws) {
            if (folded.get(raw.content()) == raw) {
                continue;
            }
            SequencedSet<Path> libraries = new LinkedHashSet<>(), testLibraries = new LinkedHashSet<>();
            SequencedSet<String> moduleDependencies = new LinkedHashSet<>();
            for (Raw half : Stream.of(raw, folded.get(raw.content())).filter(Objects::nonNull).toList()) {
                for (int index = 0; index < half.coordinates().size(); index++) {
                    String coordinate = half.coordinates().get(index), internal = identities.get(coordinate);
                    if (internal == null) {
                        if (half == raw) {
                            libraries.add(half.jars().get(index));
                        } else if (!raw.coordinates().contains(coordinate)) {
                            testLibraries.add(half.jars().get(index));
                        }
                    } else if (!internal.equals(raw.name())) {
                        moduleDependencies.add(internal);
                    }
                }
            }
            List<Path> mainSources = new ArrayList<>();
            List<Path> testSources = new ArrayList<>();
            sources(raw.content(), raw.test(), mainSources, testSources);
            List<Path> testGenerated = Stream.of(raw.test() ? raw : null, folded.get(raw.content()))
                    .filter(Objects::nonNull)
                    .flatMap(half -> half.generated().stream())
                    .toList();
            modules.add(new Module(raw.name(),
                    raw.content(),
                    raw.modular(),
                    raw.release(),
                    mainSources,
                    testSources,
                    raw.test() ? List.of() : raw.generated(),
                    testGenerated,
                    new ArrayList<>(libraries),
                    new ArrayList<>(testLibraries),
                    new ArrayList<>(moduleDependencies)));
        }
        return modules;
    }

    private static void sources(Path content, boolean test, List<Path> mainSources, List<Path> testSources) {
        for (String candidate : MAIN_SOURCES) {
            Path directory = content.resolve(candidate);
            if (Files.isDirectory(directory)) {
                (test ? testSources : mainSources).add(directory);
            }
        }
        for (String candidate : TEST_SOURCES) {
            Path directory = content.resolve(candidate);
            if (Files.isDirectory(directory)) {
                testSources.add(directory);
            }
        }
        List<Path> found = Stream.concat(mainSources.stream(), testSources.stream()).toList();
        Predicate<Path> enclosing = directory -> found.stream()
                .anyMatch(other -> !other.equals(directory) && other.startsWith(directory));
        mainSources.removeIf(enclosing);
        testSources.removeIf(enclosing);
        if (test && testSources.isEmpty()) {
            testSources.add(content);
        } else if (!test && mainSources.isEmpty()) {
            mainSources.add(content);
        }
    }

    private static String name(String path, Path base) {
        if (path.isEmpty()) {
            Path name = base.getFileName();
            return name == null ? "root" : name.toString();
        }
        return path.replace('/', '.').replace('\\', '.');
    }

    private static void idea(List<Module> modules, Path base) throws IOException {
        Path folder = Files.createDirectories(base.resolve(".idea"));
        int feature = modules.stream()
                .map(Module::release)
                .filter(Objects::nonNull)
                .max(Integer::compareTo)
                .orElseGet(() -> Runtime.version().feature());
        List<String> entries = new ArrayList<>();
        for (Module module : modules) {
            Path file = module.content().resolve(module.name() + ".iml");
            Files.writeString(file, module.iml(base, feature));
            String relative = base.relativize(file).toString().replace(File.separatorChar, '/');
            entries.add("      <module fileurl=\"file://$PROJECT_DIR$/" + relative
                    + "\" filepath=\"$PROJECT_DIR$/" + relative + "\"/>");
        }
        Files.writeString(folder.resolve("modules.xml"), """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="ProjectModuleManager">
                    <modules>
                %s
                    </modules>
                  </component>
                </project>
                """.formatted(String.join("\n", entries)));
        Path misc = folder.resolve("misc.xml");
        String jdk = projectJdk(misc);
        Files.writeString(misc, """
                <?xml version="1.0" encoding="UTF-8"?>
                <project version="4">
                  <component name="ProjectRootManager" version="2" languageLevel="%s" project-jdk-name="%s" project-jdk-type="JavaSDK">
                    <output url="file://$PROJECT_DIR$/target/.idea"/>
                  </component>
                </project>
                """.formatted(languageLevel(feature), escape(jdk == null ? version(feature) : jdk)));
    }

    private static String projectJdk(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            NodeList nodes = DocumentBuilderFactory.newInstance()
                    .newDocumentBuilder()
                    .parse(file.toFile())
                    .getElementsByTagName("component");
            for (int index = 0; index < nodes.getLength(); index++) {
                String name = ((Element) nodes.item(index)).getAttribute("project-jdk-name");
                if (!name.isEmpty()) {
                    return name;
                }
            }
        } catch (ParserConfigurationException | SAXException _) {
            return null;
        }
        return null;
    }

    private static String version(int release) {
        return release < 9 ? "1." + release : Integer.toString(release);
    }

    private static String languageLevel(int release) {
        return "JDK_" + version(release).replace('.', '_');
    }

    private static void eclipse(List<Module> modules) throws IOException {
        for (Module module : modules) {
            Files.writeString(module.content().resolve(".project"), """
                    <?xml version="1.0" encoding="UTF-8"?>
                    <projectDescription>
                      <name>%s</name>
                      <comment></comment>
                      <projects></projects>
                      <buildSpec>
                        <buildCommand>
                          <name>org.eclipse.jdt.core.javabuilder</name>
                          <arguments></arguments>
                        </buildCommand>
                      </buildSpec>
                      <natures>
                        <nature>org.eclipse.jdt.core.javanature</nature>
                      </natures>
                    %s</projectDescription>
                    """.formatted(escape(module.name()), module.linkedResources()));
            Files.writeString(module.content().resolve(".classpath"), module.classpath());
        }
    }

    private static String container(Integer release) {
        return release == null ? "org.eclipse.jdt.launching.JRE_CONTAINER"
                : "org.eclipse.jdt.launching.JRE_CONTAINER"
                + "/org.eclipse.jdt.internal.debug.ui.launcher.StandardVMType"
                + "/JavaSE-" + version(release);
    }

    private static void entry(StringBuilder content, String head, List<String> flags) {
        content.append("  <classpathentry ").append(head);
        if (flags.isEmpty()) {
            content.append("/>\n");
            return;
        }
        content.append(">\n").append("    <attributes>\n");
        for (String flag : flags) {
            content.append("      <attribute name=\"").append(flag).append("\" value=\"true\"/>\n");
        }
        content.append("    </attributes>\n").append("  </classpathentry>\n");
    }

    private static void vscode(List<Module> modules, Path base) throws IOException {
        Path folder = Files.createDirectories(base.resolve(".vscode"));
        SequencedSet<String> sourcePaths = new LinkedHashSet<>();
        SequencedMap<String, String> libraries = new LinkedHashMap<>();
        for (Module module : modules) {
            for (Path source : module.mainSources()) {
                sourcePaths.add(workspace(base, source));
            }
            for (Path source : module.testSources()) {
                sourcePaths.add(workspace(base, source));
            }
            for (Path source : module.generated()) {
                sourcePaths.add(workspace(base, source));
            }
            for (Path library : Stream.concat(module.libraries().stream(), module.testLibraries().stream()).toList()) {
                libraries.putIfAbsent(library.getFileName().toString(), workspace(base, library));
            }
        }
        StringBuilder content = new StringBuilder();
        content.append("{\n");
        content.append("  \"java.project.sourcePaths\": [\n");
        content.append(array(sourcePaths));
        content.append("  ],\n");
        content.append("  \"java.project.outputPath\": \"target/.vscode\",\n");
        content.append("  \"java.project.referencedLibraries\": [\n");
        content.append(array(libraries.values()));
        content.append("  ]\n");
        content.append("}\n");
        Files.writeString(folder.resolve("settings.json"), content.toString());
    }

    private static String array(Collection<String> values) {
        StringBuilder content = new StringBuilder();
        int index = 0;
        for (String value : values) {
            content.append("    \"").append(value.replace("\\", "\\\\").replace("\"", "\\\"")).append('"');
            content.append(++index < values.size() ? ",\n" : "\n");
        }
        return content.toString();
    }

    private static String workspace(Path base, Path target) {
        Path normalized = target.toAbsolutePath().normalize();
        return (normalized.startsWith(base)
                ? base.relativize(normalized).toString()
                : normalized.toString()).replace(File.separatorChar, '/');
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private record Module(String name,
                          Path content,
                          boolean modular,
                          Integer release,
                          List<Path> mainSources,
                          List<Path> testSources,
                          List<Path> mainGenerated,
                          List<Path> testGenerated,
                          List<Path> libraries,
                          List<Path> testLibraries,
                          List<String> moduleDependencies) {

        private String iml(Path base, int feature) {
            StringBuilder content = new StringBuilder();
            content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            content.append("<module type=\"JAVA_MODULE\" version=\"4\">\n");
            content.append("  <component name=\"NewModuleRootManager\"");
            if (release() != null && release() != feature) {
                content.append(" LANGUAGE_LEVEL=\"").append(languageLevel(release())).append('"');
            }
            content.append(" inherit-compiler-output=\"true\">\n");
            content.append("    <exclude-output/>\n");
            content.append("    <content url=\"file://$MODULE_DIR$\">\n");
            for (Path source : mainSources()) {
                content.append("      <sourceFolder url=\"file://$MODULE_DIR$")
                        .append(relative(source))
                        .append("\" isTestSource=\"false\"/>\n");
            }
            for (Path source : testSources()) {
                content.append("      <sourceFolder url=\"file://$MODULE_DIR$")
                        .append(relative(source))
                        .append("\" isTestSource=\"true\"/>\n");
            }
            for (Path source : generated()) {
                if (source.startsWith(content())) {
                    content.append("      ").append(generatedFolder(base, source)).append('\n');
                }
            }
            content.append("    </content>\n");
            for (Path source : generated()) {
                if (!source.startsWith(content())) {
                    content.append("    <content url=\"file://")
                            .append(escape(url(base, source)))
                            .append("\">\n");
                    content.append("      ").append(generatedFolder(base, source)).append('\n');
                    content.append("    </content>\n");
                }
            }
            content.append("    <orderEntry type=\"inheritedJdk\"/>\n");
            content.append("    <orderEntry type=\"sourceFolder\" forTests=\"false\"/>\n");
            for (String dependency : moduleDependencies()) {
                content.append("    <orderEntry type=\"module\" module-name=\"")
                        .append(escape(dependency))
                        .append("\"/>\n");
            }
            for (Path library : Stream.concat(libraries().stream(), testLibraries().stream()).toList()) {
                content.append("    <orderEntry type=\"module-library\"")
                        .append(testLibraries().contains(library) ? " scope=\"TEST\"" : "")
                        .append(">\n");
                content.append("      <library>\n");
                content.append("        <CLASSES>\n");
                content.append("          <root url=\"jar://")
                        .append(escape(url(base, library)))
                        .append("!/\"/>\n");
                content.append("        </CLASSES>\n");
                content.append("        <JAVADOC/>\n");
                content.append("        <SOURCES/>\n");
                content.append("      </library>\n");
                content.append("    </orderEntry>\n");
            }
            content.append("  </component>\n");
            content.append("</module>\n");
            return content.toString();
        }

        private List<Path> generated() {
            return Stream.concat(mainGenerated().stream(), testGenerated().stream()).toList();
        }

        private String generatedFolder(Path base, Path source) {
            return "<sourceFolder url=\"file://" + escape(url(base, source)) + "\" isTestSource=\""
                    + testGenerated().contains(source) + "\" generated=\"true\"/>";
        }

        private String relative(Path source) {
            String relative = content().relativize(source).toString().replace(File.separatorChar, '/');
            return relative.isEmpty() ? "" : "/" + relative;
        }

        private String url(Path base, Path path) {
            return path.startsWith(base)
                    ? "$MODULE_DIR$/" + content().relativize(path).toString().replace(File.separatorChar, '/')
                    : path.toString().replace(File.separatorChar, '/');
        }

        private String classpath() {
            List<String> onModulePath = modular() ? List.of("module") : List.of();
            StringBuilder content = new StringBuilder();
            content.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            content.append("<classpath>\n");
            for (Path source : mainSources()) {
                entry(content, "kind=\"src\"" + sourcePath(source), List.of());
            }
            for (Path source : testSources()) {
                entry(content,
                        "kind=\"src\" output=\".eclipse/test-classes\"" + sourcePath(source),
                        List.of("test"));
            }
            for (Path source : mainGenerated()) {
                entry(content,
                        "kind=\"src\" path=\"" + escape(linked(source)) + "\"",
                        List.of("optional", "ignore_optional_problems"));
            }
            for (Path source : testGenerated()) {
                entry(content,
                        "kind=\"src\" output=\".eclipse/test-classes\" path=\"" + escape(linked(source)) + "\"",
                        List.of("optional", "ignore_optional_problems", "test"));
            }
            entry(content, "kind=\"con\" path=\"" + container(release()) + "\"", onModulePath);
            for (String dependency : moduleDependencies()) {
                entry(content,
                        "combineaccessrules=\"false\" kind=\"src\" path=\"/" + escape(dependency) + "\"",
                        List.of());
            }
            for (Path library : libraries()) {
                entry(content,
                        "kind=\"lib\" path=\"" + escape(library.toString().replace(File.separatorChar, '/')) + "\"",
                        onModulePath);
            }
            for (Path library : testLibraries()) {
                entry(content,
                        "kind=\"lib\" path=\"" + escape(library.toString().replace(File.separatorChar, '/')) + "\"",
                        Stream.concat(onModulePath.stream(), Stream.of("test")).toList());
            }
            content.append("  <classpathentry kind=\"output\" path=\".eclipse/classes\"/>\n");
            content.append("</classpath>\n");
            return content.toString();
        }

        private String linked(Path source) {
            return source.startsWith(content())
                    ? content().relativize(source).toString().replace(File.separatorChar, '/')
                    : "generated-" + generated().indexOf(source);
        }

        private String linkedResources() {
            List<Path> outside = generated().stream()
                    .filter(source -> !source.startsWith(content()))
                    .toList();
            if (outside.isEmpty()) {
                return "";
            }
            StringBuilder content = new StringBuilder("  <linkedResources>\n");
            for (Path source : outside) {
                content.append("    <link>\n")
                        .append("      <name>").append(escape(linked(source))).append("</name>\n")
                        .append("      <type>2</type>\n")
                        .append("      <location>")
                        .append(escape(source.toString().replace(File.separatorChar, '/')))
                        .append("</location>\n")
                        .append("    </link>\n");
            }
            return content.append("  </linkedResources>\n").toString();
        }

        private String sourcePath(Path source) {
            String relative = escape(content()
                    .relativize(source)
                    .toString()
                    .replace(File.separatorChar, '/'));
            return (relative.isEmpty() ? " excluding=\".eclipse/\"" : "") + " path=\"" + relative + "\"";
        }
    }

    private record Raw(String name,
                       Path content,
                       boolean modular,
                       boolean test,
                       Integer release,
                       List<String> coordinates,
                       List<Path> jars,
                       List<Path> generated) {
    }
}

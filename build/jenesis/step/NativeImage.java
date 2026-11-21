package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class NativeImage extends EnvironmentalProcessBuildStep {

    public static final String NATIVE = "native/", METADATA = "nativeimage/", LICENSES = "licenses";
    private static final Set<String> COMPILER = Set.of("INCLUDE", "LIB", "LIBPATH",
            "CPATH", "C_INCLUDE_PATH", "LIBRARY_PATH", "SDKROOT");

    private final PathPlacement pathPlacement;
    private final String group;

    public NativeImage(PathPlacement pathPlacement) {
        this(pathPlacement, ProcessHandler.OfProcess.ofCommand("native-image"));
    }

    public NativeImage(PathPlacement pathPlacement, Function<List<String>, ? extends ProcessHandler> factory) {
        this(pathPlacement, factory, "main", new Terms());
    }

    public static NativeImage ofEnvironment(Environment environment,
                                            PathPlacement pathPlacement) {
        return ofEnvironment(environment, pathPlacement, ProcessHandler.OfProcess.ofCommand("native-image"));
    }

    public static NativeImage ofEnvironment(Environment environment,
                                            PathPlacement pathPlacement,
                                            Function<List<String>, ? extends ProcessHandler> factory) {
        return new NativeImage(pathPlacement, factory, "main", Terms.ofEnvironment(environment, "native-image"));
    }

    private NativeImage(PathPlacement pathPlacement,
                        Function<List<String>, ? extends ProcessHandler> factory,
                        String group,
                        Terms terms) {
        super("native-image", factory, terms);
        this.pathPlacement = pathPlacement;
        this.group = group;
    }

    public NativeImage group(String group) {
        return new NativeImage(pathPlacement, factory, group, terms);
    }

    public NativeImage verbose(BiConsumer<Boolean, String> printing) {
        return new NativeImage(pathPlacement, factory, group, terms.printing(printing));
    }

    @Override
    protected boolean inherits(String variable) {
        return COMPILER.contains(variable.toUpperCase(Locale.ROOT));
    }

    @Override
    protected ProcessHandler handler(BuildStepContext context, List<String> commands) throws IOException {
        ProcessHandler handler = super.handler(context, commands);
        for (String command : handler.commands()) {
            if (command.startsWith("-")) {
                break;
            }
            Path program = Path.of(command).toAbsolutePath();
            if (Files.isRegularFile(program) && program.getFileName().toString().startsWith("native-image")) {
                Set<Path> visited = new HashSet<>();
                while (visited.add(program)) {
                    Path home = program.getParent().getParent();
                    if (home != null && Files.isRegularFile(home.resolve(RELEASE))) {
                        Files.copy(home.resolve(RELEASE), context.next().resolve(RELEASE));
                        SequencedProperties release = SequencedProperties.ofFiles(home.resolve(RELEASE));
                        String version = release.value("GRAALVM_VERSION", release.value("JAVA_RUNTIME_VERSION", ""))
                                .replace("\"", "");
                        Path licenses = context.next().resolve(NATIVE).resolve(LICENSES)
                                .resolve(version.isEmpty() ? "graalvm" : "graalvm-" + version);
                        if (Files.exists(licenses)) {
                            throw new IllegalStateException("The notices of a jar the image needs are named "
                                    + licenses.getFileName() + ", where the licence of the GraalVM that compiles it"
                                    + " belongs - rename that jar");
                        }
                        try (DirectoryStream<Path> files = Files.newDirectoryStream(home)) {
                            for (Path file : files) {
                                String name = file.getFileName().toString().toUpperCase(Locale.ROOT);
                                if (Files.isRegularFile(file) && (name.contains("LICENSE") || name.contains("NOTICE"))) {
                                    Files.createDirectories(licenses);
                                    BuildStep.linkOrCopy(licenses.resolve(file.getFileName().toString()), file.toRealPath());
                                }
                            }
                        }
                        break;
                    }
                    if (!Files.isSymbolicLink(program)) {
                        break;
                    }
                    program = program.getParent().resolve(Files.readSymbolicLink(program)).normalize();
                }
                break;
            }
        }
        return handler;
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        boolean modular = pathPlacement.modular();
        ModuleGraph graph = new ModuleGraph();
        String launcher = null, name = null;
        List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
        List<Path> notices = new ArrayList<>();
        Path config = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path descriptor = argument.folder().resolve("launcher.properties");
            if (Files.isRegularFile(descriptor)) {
                SequencedProperties launcherProperties = SequencedProperties.ofFiles(descriptor);
                if (name == null) {
                    String value = launcherProperties.getProperty("name");
                    if (value != null && !value.isEmpty()) {
                        name = value;
                    }
                }
                if (launcher == null) {
                    String mainClass = launcherProperties.getProperty("mainClass");
                    String mainModule = launcherProperties.getProperty("mainModule");
                    if (mainClass != null && !mainClass.isEmpty()) {
                        launcher = modular && mainModule != null ? mainModule + "/" + mainClass : mainClass;
                    }
                }
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        graph.place(pathPlacement, file, modulePath, classPath);
                    }
                }
            }
            for (Path file : Dependencies.select(argument.folder(), group, "runtime")) {
                graph.place(pathPlacement, file, modulePath, classPath);
            }
            Path candidate = argument.folder().resolve("native-image");
            if (Files.isDirectory(candidate)) {
                config = candidate;
            }
            Path legal = argument.folder().resolve(Legal.LEGAL);
            if (Files.isDirectory(legal)) {
                notices.add(legal);
            }
        }
        if (launcher == null || (modulePath.isEmpty() && classPath.isEmpty())) {
            return CompletableFuture.completedStage(null);
        }
        SequencedSet<String> layers = Layers.declared(arguments).sequencedKeySet();
        if (!layers.isEmpty()) {
            throw new IllegalStateException("Layers " + layers + " cannot be defined in a native image,"
                    + " as a native image resolves its module graph ahead of time - build the application"
                    + " for a Java runtime, or keep the layers' modules on the application's module path");
        }
        for (List<String> entries : List.of(modulePath, classPath)) {
            for (String entry : entries) {
                if (entry.indexOf(File.pathSeparatorChar) != -1) {
                    throw new IllegalArgumentException(
                            "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                }
            }
        }
        List<String> commands = new ArrayList<>();
        commands.add("--no-fallback");
        if (config != null) {
            commands.add("-H:ConfigurationFileDirectories=" + config);
        }
        Path output = Files.createDirectories(context.next().resolve(NATIVE));
        for (Path legal : notices) {
            try (Stream<Path> files = Files.walk(legal)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    Path target = output.resolve(LICENSES).resolve(legal.relativize(file).toString());
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target)) {
                        BuildStep.linkOrCopy(target, file);
                    }
                }
            }
        }
        commands.add("-o");
        commands.add(output.resolve(name == null ? "image" : name).toString());
        if (!modulePath.isEmpty()) {
            commands.add("--module-path");
            commands.add(String.join(File.pathSeparator, modulePath));
        }
        if (!classPath.isEmpty()) {
            commands.add("-cp");
            commands.add(String.join(File.pathSeparator, classPath));
        }
        commands.addAll(graph.arguments());
        if (modular) {
            commands.add("--module");
            commands.add(launcher);
        } else {
            commands.add(launcher);
        }
        return CompletableFuture.completedStage(commands);
    }
}

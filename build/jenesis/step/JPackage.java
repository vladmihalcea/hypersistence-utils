package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;

public class JPackage extends ProcessBuildStep {

    public static final String PACKAGES = "packages/";
    private static final String FAKEROOT = """
            #!/bin/sh
            case "$1" in
              -v|--version) echo "fakeroot (dpkg-deb --root-owner-group)"; exit 0 ;;
              dpkg-deb) shift; exec dpkg-deb --root-owner-group "$@" ;;
            esac
            echo "fakeroot: Jenesis runs dpkg-deb alone, with --root-owner-group" >&2
            exit 1
            """;

    private final String type;
    private final String group;

    public JPackage(ProcessHandler.Factory factory) {
        this(factory.apply("jpackage", "bin/jpackage"),
             null,
             "main",
             new Terms());
    }

    public static JPackage ofEnvironment(Environment environment,
                                         ProcessHandler.Factory factory) {
        return new JPackage(factory.apply("jpackage", "bin/jpackage"),
                null,
                "main",
                Terms.ofEnvironment(environment, "jpackage"));
    }

    private JPackage(Function<List<String>, ? extends ProcessHandler> factory, String type, String group, Terms terms) {
        super("jpackage", factory, terms);
        this.type = type;
        this.group = group;
    }

    public JPackage type(String type) {
        return new JPackage(factory, type, group, terms);
    }

    public JPackage group(String group) {
        return new JPackage(factory, type, group, terms);
    }

    public JPackage verbose(BiConsumer<Boolean, String> printing) {
        return new JPackage(factory, type, group, terms.printing(printing));
    }

    @Override
    protected List<String> configurations() {
        return type == null || super.configurations().isEmpty()
                ? super.configurations()
                : List.of("jpackage", "jpackage-" + type);
    }

    @Override
    protected ProcessHandler handler(BuildStepContext context, List<String> commands) throws IOException {
        ProcessHandler handler = super.handler(context, commands);
        if (!"deb".equals(type) || !FileSystems.getDefault().supportedFileAttributeViews().contains("posix")) {
            return handler;
        }
        Path folder = Files.createDirectories(context.supplement().resolve("fakeroot"));
        Files.writeString(folder.resolve("fakeroot"), FAKEROOT);
        Files.setPosixFilePermissions(folder.resolve("fakeroot"), PosixFilePermissions.fromString("rwxr-xr-x"));
        ProcessHandler.OfProcess process = switch (handler) {
            case ProcessHandler.OfProcess forked -> forked;
            case ProcessHandler.OfTool _ -> ProcessHandler.OfProcess.ofJavaHome("bin/jpackage").apply(commands);
        };
        SortedMap<String, String> environment = new TreeMap<>(process.environment());
        environment.merge("PATH", folder.toString(), (path, shim) -> shim + File.pathSeparator + path);
        return process.environment(environment);
    }

    @Override
    protected SequencedMap<String, SequencedMap<String, String>> properties(
            SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, SequencedMap<String, String>> properties = super.properties(arguments);
        if (type != null && !type.equals("app-image")
                && properties.values().stream().noneMatch(folder -> folder.containsKey("--license-file"))) {
            Path license = null;
            for (BuildStepArgument argument : arguments.values()) {
                Path legal = argument.removed() ? null : argument.folder().resolve(Legal.LEGAL);
                if (license == null && legal != null && Files.isDirectory(legal)) {
                    try (Stream<Path> files = Files.list(legal)) {
                        license = files.filter(Files::isRegularFile)
                                .filter(file -> file.getFileName().toString().toUpperCase(Locale.ROOT).startsWith("LICENSE"))
                                .sorted()
                                .findFirst()
                                .orElse(null);
                    }
                }
            }
            if (license != null) {
                properties.computeIfAbsent(Legal.LEGAL, _ -> new LinkedHashMap<>()).put("--license-file", license.toString());
            }
        }
        for (SequencedMap<String, String> folder : properties.values()) {
            folder.computeIfPresent("--app-version", (_, version) -> {
                String dotted = version.split("[^0-9.]", 2)[0];
                while (dotted.endsWith(".")) {
                    dotted = dotted.substring(0, dotted.length() - 1);
                }
                return dotted.isEmpty() ? version : dotted;
            });
        }
        return properties;
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        boolean modular = properties.values().stream().anyMatch(folder -> folder.containsKey("--module"));
        if (!modular && properties.values().stream().noneMatch(folder -> folder.containsKey("--main-jar"))) {
            return CompletableFuture.completedStage(null);
        }
        Path runtime = null;
        ModuleGraph graph = new ModuleGraph();
        SequencedSet<Path> granted = new LinkedHashSet<>();
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
        SequencedMap<String, Path> pool = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(JLink.RUNTIME);
            if (runtime == null && Files.isDirectory(candidate)) {
                runtime = candidate;
            }
            granted.addAll(Inventory.nativeAccess(argument.folder()));
            layers.putAll(Layers.membership(argument.folder()));
            for (Path jar : Dependencies.all(argument.folder())) {
                pool.putIfAbsent(jar.getFileName().toString(), jar);
            }
        }
        List<String> layered = new ArrayList<>();
        if (!layers.isEmpty()) {
            Path content = Files.createDirectories(context.supplement().resolve("content").resolve("layers"));
            for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
                for (String name : layer.getValue().all()) {
                    Path jar = pool.get(name);
                    if (jar == null) {
                        throw new IllegalStateException("Layer " + layer.getKey() + " names " + name
                                + ", which was not resolved for this application");
                    }
                    if (!Files.exists(content.resolve(name))) {
                        BuildStep.linkOrCopy(content.resolve(name), jar);
                    }
                }
                layered.add("--java-options");
                layered.add("-Djlayer.modulepath." + layer.getKey() + "=" + layer.getValue().modulepath().stream()
                        .map(name -> String.join(File.separator, "$APPDIR", "..", "layers", name))
                        .collect(Collectors.joining(File.pathSeparator)));
                if (!layer.getValue().classpath().isEmpty()) {
                    layered.add("--java-options");
                    layered.add("-Djlayer.classpath." + layer.getKey() + "=" + layer.getValue().classpath().stream()
                            .map(name -> String.join(File.separator, "$APPDIR", "..", "layers", name))
                            .collect(Collectors.joining(File.pathSeparator)));
                }
                layer.getValue().nativeAccess(pool, granted)
                        .forEach((jar, module) -> graph.enableNativeAccess(layer.getKey(), jar, module));
            }
            layered.add("--app-content");
            layered.add(content.toString());
        }
        if (runtime != null) {
            List<String> commands = new ArrayList<>();
            if (type != null) {
                commands.add("--type");
                commands.add(type);
            }
            commands.add("--runtime-image");
            commands.add(runtime.toString());
            commands.addAll(layered);
            for (String option : graph.options()) {
                commands.add("--java-options");
                commands.add(option);
            }
            commands.add("--dest");
            commands.add(Files.createDirectory(context.next().resolve(PACKAGES)).toString());
            return CompletableFuture.completedStage(commands);
        }
        Path input = Files.createDirectory(context.supplement().resolve("input"));
        SequencedMap<String, Path> staged = new LinkedHashMap<>();
        SequencedMap<String, Path> jmods = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            Path folder = argument.removed() ? null : argument.folder().resolve(JMod.JMODS);
            if (modular && folder != null && Files.isDirectory(folder)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(folder, "*.jmod")) {
                    for (Path file : files) {
                        String name = file.getFileName().toString();
                        jmods.put(name.substring(0, name.length() - ".jmod".length()), file);
                    }
                }
            }
        }
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            List<Path> jars = new ArrayList<>();
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        jars.add(file);
                    }
                }
            }
            jars.addAll(Dependencies.select(argument.folder(), group, "runtime"));
            for (Path file : jars) {
                ModuleDescriptor descriptor = modular ? PathPlacement.moduleDescriptor(file) : null;
                Path linked = descriptor == null ? file : jmods.getOrDefault(descriptor.name(), file);
                String name = linked.getFileName().toString();
                Path previous = staged.putIfAbsent(name, linked);
                if (previous != null) {
                    throw new IllegalStateException("Cannot stage two jars with the same file name '"
                            + name + "' into a single jpackage input: " + previous + " and " + file);
                }
                if (modular) {
                    graph.module(file);
                } else {
                    graph.place(PathPlacement.CLASS_PATH, file);
                }
                if (granted.contains(file.toAbsolutePath().normalize())) {
                    graph.enableNativeAccess(file, modular);
                }
                BuildStep.linkOrCopy(input.resolve(name), linked);
            }
        }
        if (staged.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        List<String> commands = new ArrayList<>();
        if (type != null) {
            commands.add("--type");
            commands.add(type);
        }
        if (modular) {
            commands.add("@" + ProcessBuildStep.argumentFile(
                    context.supplement().resolve("jpackage.args"),
                    List.of("--module-path", staged.values().stream()
                            .map(file -> input.resolve(file.getFileName().toString()).toString())
                            .collect(Collectors.joining(File.pathSeparator)))));
            SequencedSet<String> platform = new TreeSet<>();
            layers.values().forEach(membership -> platform.addAll(membership.platform(pool)));
            if (!platform.isEmpty()) {
                commands.add("--add-modules");
                commands.add(String.join(",", platform));
            }
        } else {
            commands.add("--input");
            commands.add(input.toString());
        }
        commands.addAll(layered);
        for (String option : graph.options()) {
            commands.add("--java-options");
            commands.add(option);
        }
        commands.add("--dest");
        commands.add(Files.createDirectory(context.next().resolve(PACKAGES)).toString());
        return CompletableFuture.completedStage(commands);
    }
}

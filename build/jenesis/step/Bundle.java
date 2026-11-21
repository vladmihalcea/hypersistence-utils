package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.ModuleGraph;
import build.jenesis.Palette;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class Bundle implements BuildStep {

    public static final String BUNDLE = "bundle/";
    private static final Set<String> ARCHIVES = Set.of(".jar", ".zip", ".jmod", ".war", ".ear", ".gz", ".tgz", ".xz", ".bz2");

    private final String group;
    private final OffsetDateTime timestamp;
    private final transient Consumer<String> out;
    private final transient Palette palette;

    public Bundle() {
        this("main",
             BuildStep.timestamp(),
             null,
             Palette.NONE);
    }

    public static Bundle ofEnvironment(Environment environment) {
        return new Bundle()
                .timestamp(BuildStep.timestamp(environment))
                .printing(environment.out(), Palette.ofEnvironment(environment));
    }

    private Bundle(String group, OffsetDateTime timestamp, Consumer<String> out, Palette palette) {
        this.group = group;
        this.timestamp = timestamp;
        this.out = out;
        this.palette = palette;
    }

    public Bundle group(String group) {
        return new Bundle(group, timestamp, out, palette);
    }

    public Bundle timestamp(OffsetDateTime timestamp) {
        return new Bundle(group, timestamp, out, palette);
    }

    public Bundle printing(Consumer<String> out, Palette palette) {
        return new Bundle(group, timestamp, out, palette);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String mainClass = null, mainModule = null, artifact = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path properties = argument.folder().resolve("launcher.properties");
            if (!Files.isRegularFile(properties)) {
                continue;
            }
            SequencedProperties launcher = SequencedProperties.ofFiles(properties);
            if (mainClass == null) {
                mainClass = launcher.getProperty("mainClass");
            }
            if (mainModule == null) {
                mainModule = launcher.getProperty("mainModule");
            }
            if (artifact == null) {
                artifact = launcher.getProperty("name");
            }
        }
        if (mainClass == null) {
            if (out != null) {
                out.accept(("%s%-11s%s %s builds no bundle, as it names no main class: name one with @jenesis.main"
                        + " <class> in its module-info.java, or with a <mainClass> property in its pom.xml")
                        .formatted(palette.warning(),
                                "[SKIPPED]",
                                palette.reset(),
                                artifact == null ? "The module" : artifact));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> jars = new TreeMap<>();
        SequencedSet<Path> granted = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        jars.putIfAbsent(file.getFileName().toString(), file);
                    }
                }
            }
            for (String scope : List.of("runtime", "agent")) {
                for (Path file : Dependencies.select(argument.folder(), group, scope)) {
                    jars.putIfAbsent(file.getFileName().toString(), file);
                }
            }
            granted.addAll(Inventory.nativeAccess(argument.folder()));
        }
        if (jars.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        ModuleGraph graph = new ModuleGraph();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            boolean placed = graph.place(mainModule == null ? PathPlacement.CLASS_PATH : PathPlacement.INFERRED, entry.getValue());
            (placed ? modulepath : classpath).put(entry.getKey(), entry.getValue());
            if (granted.contains(entry.getValue().toAbsolutePath().normalize())) {
                graph.enableNativeAccess(entry.getValue(), placed);
            }
        }
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
        SequencedMap<String, String> agents = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            layers.putAll(Layers.membership(argument.folder()));
            agents.putAll(Inventory.agents(argument.folder()));
        }
        if (layers.values().stream().anyMatch(membership -> !membership.classpath().isEmpty())) {
            graph.unnamed();
        }
        SequencedSet<String> named = new LinkedHashSet<>();
        layers.values().forEach(membership -> named.addAll(membership.all()));
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.all(argument.folder())) {
                if (named.contains(jar.getFileName().toString())) {
                    jars.putIfAbsent(jar.getFileName().toString(), jar);
                }
            }
        }
        layers.forEach((layer, membership) -> membership.nativeAccess(jars, granted)
                .forEach((jar, module) -> graph.enableNativeAccess(layer, jar, module)));
        agents.keySet().retainAll(jars.sequencedKeySet());
        List<String> javaOptions = javaOptions(arguments);
        SequencedMap<String, Path> descriptors = new LinkedHashMap<>();
        for (Map.Entry<String, String> platform : List.of(
                Map.entry("unix", ":"),
                Map.entry("windows", ";")
        )) {
            descriptors.put("application." + platform.getKey() + ".args", ProcessBuildStep.argumentFile(
                    context.supplement().resolve("application." + platform.getKey() + ".args"),
                    command(javaOptions,
                            mainClass,
                            mainModule,
                            graph.arguments(),
                            classpath.sequencedKeySet(),
                            modulepath.sequencedKeySet(),
                            layers,
                            agents,
                            platform.getValue())));
        }
        SequencedMap<String, Path> stored = new TreeMap<>(classpath);
        stored.putAll(modulepath);
        for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
            for (String name : layer.getValue().all()) {
                Path jar = jars.get(name);
                if (jar == null) {
                    throw new IllegalStateException("Layer " + layer.getKey() + " names " + name
                            + ", which was not resolved for this application");
                }
                stored.putIfAbsent(name, jar);
            }
        }
        Path zip = Files.createDirectory(context.next().resolve(BUNDLE)).resolve("bundle.zip");
        try (ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(zip))) {
            for (Map.Entry<String, Path> entry : descriptors.entrySet()) {
                writeEntry(out, entry.getKey(), entry.getValue());
            }
            for (Map.Entry<String, Path> entry : stored.entrySet()) {
                writeEntry(out, "jars/" + entry.getKey(), entry.getValue());
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    static List<String> javaOptions(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        List<String> javaOptions = new ArrayList<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path process = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, "java");
            if (process != null) {
                SequencedProperties.ofFiles(process).forEachProperty((option, values) -> {
                    for (String value : values.split("\n")) {
                        javaOptions.add(option);
                        if (!value.isEmpty()) {
                            javaOptions.addAll(List.of(value.split("\t")));
                        }
                    }
                });
            }
        }
        return javaOptions;
    }

    private static List<String> command(List<String> javaOptions,
                                        String mainClass,
                                        String mainModule,
                                        List<String> relaxations,
                                        SequencedSet<String> classpath,
                                        SequencedSet<String> modulepath,
                                        SequencedMap<String, Layers.Membership> layers,
                                        SequencedMap<String, String> agents,
                                        String separator) {
        List<String> command = new ArrayList<>(javaOptions);
        agents.forEach((jar, options) -> command.add("-javaagent:jars/" + jar
                + (options.isEmpty() ? "" : "=" + options)));
        layers.forEach((name, membership) -> {
            command.add("-Djlayer.modulepath." + name + "="
                    + path(membership.modulepath(), separator));
            if (!membership.classpath().isEmpty()) {
                command.add("-Djlayer.classpath." + name + "="
                        + path(membership.classpath(), separator));
            }
        });
        if (!classpath.isEmpty()) {
            command.add("--class-path");
            command.add(path(classpath, separator));
        }
        if (modulepath.isEmpty()) {
            command.addAll(relaxations);
            command.add(mainClass);
        } else {
            command.add("--module-path");
            command.add(path(modulepath, separator));
            command.addAll(relaxations);
            command.add("--module");
            command.add(mainModule + "/" + mainClass);
        }
        return command;
    }

    private static String path(SequencedSet<String> names, String separator) {
        return names.stream().map(name -> "jars/" + name).collect(Collectors.joining(separator));
    }

    private void writeEntry(ZipOutputStream out, String name, Path file) throws IOException {
        ZipEntry entry = new ZipEntry(name);
        if (timestamp == null) {
            entry.setTime(Files.getLastModifiedTime(file).toMillis());
        } else {
            entry.setTimeLocal(timestamp.toLocalDateTime());
        }
        int dot = name.lastIndexOf('.');
        if (dot >= 0 && ARCHIVES.contains(name.substring(dot).toLowerCase(Locale.ROOT))) {
            try (CheckedInputStream in = new CheckedInputStream(Files.newInputStream(file), new CRC32())) {
                long size = in.transferTo(OutputStream.nullOutputStream());
                entry.setMethod(ZipEntry.STORED);
                entry.setSize(size);
                entry.setCompressedSize(size);
                entry.setCrc(in.getChecksum().getValue());
            }
        }
        out.putNextEntry(entry);
        Files.copy(file, out);
        out.closeEntry();
    }
}

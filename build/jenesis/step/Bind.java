package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class Bind implements BuildStep {

    private static final String BOUND = "bound";
    private static final Set<String> IDE_FILES = Set.of(".classpath", ".project", ".settings", ".factorypath", ".eclipse");

    private final Map<Path, Path> paths;
    private final Set<String> extensions;
    private final boolean ide;

    public Bind(Map<Path, Path> paths) {
        this(paths, null, true);
    }

    private Bind(Map<Path, Path> paths, Set<String> extensions, boolean ide) {
        this.paths = paths;
        this.extensions = extensions;
        this.ide = ide;
    }

    public Bind extensions(Set<String> extensions) {
        return new Bind(paths, extensions, ide);
    }

    public Bind ide(boolean ide) {
        return new Bind(paths, extensions, ide);
    }

    public static Bind asSources() {
        return new Bind(Map.of(Path.of("."), Path.of(SOURCES))).ide(false);
    }

    public static Bind asResources() {
        return new Bind(Map.of(Path.of("."), Path.of(RESOURCES))).ide(false);
    }

    public static Bind asIdentity(String name) {
        return new Bind(Map.of(Path.of(name == null ? IDENTITY : name), Path.of(IDENTITY)));
    }

    public static Bind asRequires(String name) {
        return new Bind(Map.of(Path.of(name), Path.of(REQUIRES)));
    }

    public static Bind asMetadata() {
        return new Bind(Map.of(Path.of(""), Path.of(METADATA)));
    }

    public static Path contained(Path root, Path path, String origin) {
        Path project = root.toAbsolutePath().normalize(), located = path.toAbsolutePath().normalize();
        boolean inside = located.startsWith(project);
        if (inside && Files.exists(located)) {
            try {
                inside = located.toRealPath().startsWith(project.toRealPath());
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot resolve " + located, e);
            }
        }
        if (!inside) {
            throw new IllegalArgumentException(origin + " names " + located + ", which lies outside the project "
                    + project + " - name a folder the project holds");
        }
        return located;
    }

    public static BuildExecutorModule asInputs(SequencedMap<String, SequencedMap<Path, Path>> inputs) {
        return (buildExecutor, _) -> {
            for (Map.Entry<String, SequencedMap<Path, Path>> input : inputs.entrySet()) {
                buildExecutor.addModule(input.getKey(), new BuildExecutorModule() {
                    @Override
                    public Optional<String> resolve(String path) {
                        return path.equals(BOUND) ? Optional.of("") : Optional.empty();
                    }

                    @Override
                    public void accept(BuildExecutor bound, SequencedMap<String, Path> inherited) {
                        SequencedSet<String> sources = new LinkedHashSet<>();
                        for (Map.Entry<Path, Path> binding : input.getValue().entrySet()) {
                            String source = "source-" + sources.size();
                            bound.addSource(source, new Bind(Map.of(Path.of(""), binding.getKey())), binding.getValue());
                            sources.add(source);
                        }
                        bound.addStep(BOUND, new Bind(Map.of(Path.of(""), Path.of(""))), sources);
                    }
                });
            }
        };
    }

    public static <M extends BuildExecutorModule> boolean configured(BuildExecutor buildExecutor,
                                                                     SequencedSet<String> inputs,
                                                                     String name,
                                                                     Function<M, BuildExecutorModule> configurator,
                                                                     Path configurationFile,
                                                                     Supplier<M> module) {
        return configured(buildExecutor,
                inputs,
                name,
                configurator,
                configurationFile == null
                        ? Collections.emptyNavigableMap()
                        : new TreeMap<>(Map.of(configurationFile.getFileName(), configurationFile)),
                module);
    }

    public static <M extends BuildExecutorModule> boolean configured(BuildExecutor buildExecutor,
                                                                     SequencedSet<String> inputs,
                                                                     String name,
                                                                     Function<M, BuildExecutorModule> configurator,
                                                                     SequencedMap<Path, Path> files,
                                                                     Supplier<M> module) {
        if (configurator == null || files.isEmpty()) {
            return false;
        }
        BuildExecutorModule configured = configurator.apply(module.get());
        if (configured == null) {
            return false;
        }
        buildExecutor.addModule(name, (nested, inherited) -> {
            if (files.size() == 1) {
                Map.Entry<Path, Path> file = files.firstEntry();
                nested.addSource("configuration", new Bind(Map.of(Path.of(""), file.getKey())), file.getValue());
            } else {
                SequencedSet<String> sources = new LinkedHashSet<>();
                for (Map.Entry<Path, Path> file : files.entrySet()) {
                    String source = "configuration-" + sources.size();
                    nested.addSource(source, new Bind(Map.of(Path.of(""), file.getKey())), file.getValue());
                    sources.add(source);
                }
                nested.addStep("configuration", new Bind(Map.of(Path.of(""), Path.of(""))), sources);
            }
            SequencedSet<String> toolInputs = new LinkedHashSet<>();
            toolInputs.add("configuration");
            toolInputs.addAll(inherited.sequencedKeySet());
            nested.addModule("tool", configured, toolInputs);
        }, inputs);
        return true;
    }

    public static <M extends BuildExecutorModule> boolean configuredByProperties(BuildExecutor buildExecutor,
                                                                              SequencedSet<String> inputs,
                                                                              String name,
                                                                              Function<M, BuildExecutorModule> configurator,
                                                                              Path configurationProperties,
                                                                              Function<SequencedProperties, M> module)
            throws IOException {
        if (configurator == null || configurationProperties == null || !Files.isRegularFile(configurationProperties)) {
            return false;
        }
        M candidate = module.apply(SequencedProperties.ofFiles(configurationProperties));
        if (candidate == null) {
            return false;
        }
        BuildExecutorModule configured = configurator.apply(candidate);
        if (configured == null) {
            return false;
        }
        buildExecutor.addModule(name, configured, inputs);
        return true;
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(paths.keySet()));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Map.Entry<Path, Path> entry : paths.entrySet()) {
                Path source = argument.folder().resolve(entry.getKey());
                if (Files.exists(source)) {
                    Path target = context.next().resolve(entry.getValue());
                    if (!Objects.equals(target.getParent(), context.next())) {
                        Files.createDirectories(target.getParent());
                    }
                    boolean filtered = extensions != null && Files.isDirectory(source);
                    Files.walkFileTree(source,
                            Set.of(FileVisitOption.FOLLOW_LINKS),
                            Integer.MAX_VALUE,
                            new SimpleFileVisitor<>() {
                                @Override
                                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                                        throws IOException {
                                    if (!ide && source.equals(dir.getParent())
                                            && IDE_FILES.contains(dir.getFileName().toString())
                                            || !dir.equals(source) && Files.exists(dir.resolve(BuildExecutor.SKIP_MARKER))) {
                                        return FileVisitResult.SKIP_SUBTREE;
                                    }
                                    if (!filtered) {
                                        Files.createDirectories(target.resolve(source.relativize(dir)));
                                    }
                                    return FileVisitResult.CONTINUE;
                                }

                                @Override
                                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                                        throws IOException {
                                    String name = file.getFileName().toString();
                                    if (filtered && extensions.stream().noneMatch(name::endsWith)
                                            || name.equals(BuildExecutor.SKIP_MARKER)
                                            || !ide && source.equals(file.getParent())
                                            && (IDE_FILES.contains(name) || name.endsWith(".iml"))) {
                                        return FileVisitResult.CONTINUE;
                                    }
                                    Path resolved = target.resolve(source.relativize(file));
                                    if (filtered) {
                                        Files.createDirectories(resolved.getParent());
                                    }
                                    BuildStep.linkOrCopy(resolved, Files.isSymbolicLink(file) ? file.toRealPath() : file);
                                    return FileVisitResult.CONTINUE;
                                }
                            });
                }
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

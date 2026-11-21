package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Platform;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.module.ModularProject;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Javac;
import build.jenesis.step.ProcessHandler;

public class InternalModule extends PluginModule<InternalModule> {

    public static final String SOURCE = "sources", JAVA = "java";
    private static final String MANIFESTS = "manifests", REQUIRES = "requires";
    private static final String MAIN_ARTIFACTS = JAVA + "/" + JavaToolchainModule.ARTIFACTS;

    private final String prefix;
    private final Path source;
    private final Dependencies dependencyModule;
    private final Javac javacStep;
    private final SequencedSet<String> additionalDependencies;
    private final Platform platform;

    public static InternalModule ofEnvironment(Environment environment,
                                               String prefix,
                                               String group,
                                               Path source,
                                               Map<String, Repository> repositories,
                                               Map<String, Resolver> resolvers) {
        return new InternalModule(prefix,
                source,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                Javac.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment)),
                Collections.emptyNavigableSet(),
                null,
                group,
                Platform.ofEnvironment(environment),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                repositories,
                resolvers,
                environment);
    }

    private InternalModule(String prefix,
                           Path source,
                           Dependencies dependencyModule,
                           Javac javacStep,
                           SequencedSet<String> additionalDependencies,
                           String buildModuleName,
                           String group,
                           Platform platform,
                           SequencedMap<String, String> properties,
                           SequencedMap<String, SequencedMap<Path, Path>> inputs,
                           Map<String, Repository> repositories,
                           Map<String, Resolver> resolvers,
                           Environment environment) {
        super(buildModuleName,
                group,
                PathPlacement.MODULE_PATH,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
        this.prefix = prefix;
        this.source = source;
        this.dependencyModule = dependencyModule;
        this.javacStep = javacStep;
        this.additionalDependencies = additionalDependencies;
        this.platform = platform;
    }

    public InternalModule dependencies(String... dependencies) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                new LinkedHashSet<>(List.of(dependencies)),
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule dependencies(SequencedSet<String> dependencies) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                new LinkedHashSet<>(dependencies),
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule buildModuleName(String name) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                name,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule platform(Platform platform) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule pinning(Pinning pinning) {
        return new InternalModule(prefix,
                source,
                dependencyModule.pinning(pinning),
                javacStep,
                additionalDependencies,
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule properties(SequencedMap<String, String> properties) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule inputs(SequencedMap<String, SequencedMap<Path, Path>> inputs) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule repositories(Map<String, Repository> repositories) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public InternalModule resolvers(Map<String, Resolver> resolvers) {
        return new InternalModule(prefix,
                source,
                dependencyModule,
                javacStep,
                additionalDependencies,
                buildModuleName,
                group,
                platform,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public BuildExecutorModule resolution() {
        return (buildExecutor, inherited) -> {
            buildExecutor.addSource(SOURCE, Bind.asSources(), source);
            buildExecutor.addStep(MANIFESTS, ModularProject.manifests(group, prefix, platform), SOURCE);
            buildExecutor.addStep(REQUIRES, new Inherited(group, additionalDependencies), inherited.sequencedKeySet());
            buildExecutor.addModule(DEPENDENCIES,
                    dependencyModule.pathPlacement(pathPlacement),
                    MANIFESTS,
                    REQUIRES);
        };
    }

    @Override
    public BuildExecutorModule preparation() {
        return (buildExecutor, inherited) -> {
            resolution().accept(buildExecutor, inherited);
            buildExecutor.addModule(JAVA,
                    new JavaToolchainModule().compiler(javacStep.group(group).asModule("javac")),
                    SOURCE,
                    MANIFESTS,
                    DEPENDENCIES);
        };
    }

    @Override
    String identity() {
        return source.toAbsolutePath().normalize().toString();
    }

    @Override
    SequencedSet<String> prepared() {
        return new LinkedHashSet<>(List.of(MAIN_ARTIFACTS, DEPENDENCIES));
    }

    @Override
    List<Path> artifacts(SequencedMap<String, Path> prepared) throws IOException {
        List<Path> artifacts = new ArrayList<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(prepared.get(PREVIOUS + MAIN_ARTIFACTS)
                .resolve(BuildStep.ARTIFACTS))) {
            for (Path file : files) {
                artifacts.add(file);
            }
        }
        artifacts.addAll(Dependencies.select(prepared.get(PREVIOUS + DEPENDENCIES), group, "runtime"));
        artifacts.sort(null);
        return artifacts;
    }

    private record Inherited(String group, SequencedSet<String> additionalDependencies) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return arguments.values().stream().anyMatch(argument -> argument.hasChanged(Path.of(BuildStep.VERSIONS)));
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            for (String dependency : additionalDependencies) {
                requires.setProperty(group + "/compile/" + dependency, "");
                requires.setProperty(group + "/runtime/" + dependency, "");
            }
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            SequencedProperties versions = new SequencedProperties();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(BuildStep.VERSIONS);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties.ofFiles(file).forEachProperty(versions::putIfAbsent);
            }
            if (!versions.isEmpty()) {
                versions.store(context.next().resolve(BuildStep.VERSIONS));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

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
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;

public class ExternalModule extends PluginModule<ExternalModule> {

    public static final String COORDINATE = "coordinate";
    private static final String MAVEN = "maven";

    private final String coordinate;
    private final Dependencies dependencyModule;
    private final SequencedSet<String> additionalDependencies;

    public static ExternalModule ofEnvironment(Environment environment,
                                               String coordinate,
                                               String group,
                                               Map<String, Repository> repositories,
                                               Map<String, Resolver> resolvers) {
        return new ExternalModule(coordinate,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                Collections.emptyNavigableSet(),
                Resolver.base(coordinate.substring(0, coordinate.indexOf('/'))).equals(MAVEN)
                        ? PathPlacement.CLASS_PATH
                        : PathPlacement.INFERRED,
                null,
                group,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                repositories,
                resolvers,
                environment);
    }

    public static ExternalModule official(Environment environment,
                                          String name,
                                          String provider,
                                          Map<String, Repository> repositories,
                                          Map<String, Resolver> resolvers) {
        return ofEnvironment(environment,
                "module/build.jenesis.plugin." + name,
                name,
                ProjectPlugins.repositories(Map.of(), environment),
                ProjectPlugins.resolvers(Map.of(), environment))
                .buildModuleName(provider)
                .repositories(ProjectPlugins.repositories(repositories, environment))
                .resolvers(ProjectPlugins.resolvers(resolvers, environment));
    }

    private ExternalModule(String coordinate,
                           Dependencies dependencyModule,
                           SequencedSet<String> additionalDependencies,
                           PathPlacement pathPlacement,
                           String buildModuleName,
                           String group,
                           SequencedMap<String, String> properties,
                           SequencedMap<String, SequencedMap<Path, Path>> inputs,
                           Map<String, Repository> repositories,
                           Map<String, Resolver> resolvers,
                           Environment environment) {
        super(buildModuleName, group, pathPlacement, properties, inputs, repositories, resolvers, environment);
        this.coordinate = coordinate;
        this.dependencyModule = dependencyModule;
        this.additionalDependencies = additionalDependencies;
    }

    public ExternalModule dependencies(String... dependencies) {
        return dependencies(new LinkedHashSet<>(List.of(dependencies)));
    }

    public ExternalModule dependencies(SequencedSet<String> dependencies) {
        return new ExternalModule(coordinate,
                dependencyModule,
                dependencies,
                pathPlacement,
                buildModuleName,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public ExternalModule pathPlacement(PathPlacement pathPlacement) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                pathPlacement,
                buildModuleName,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public ExternalModule buildModuleName(String name) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                pathPlacement,
                name,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public ExternalModule pinning(Pinning pinning) {
        return new ExternalModule(coordinate,
                dependencyModule.pinning(pinning),
                additionalDependencies,
                pathPlacement,
                buildModuleName,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public ExternalModule properties(SequencedMap<String, String> properties) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                pathPlacement,
                buildModuleName,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public ExternalModule inputs(SequencedMap<String, SequencedMap<Path, Path>> inputs) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                pathPlacement,
                buildModuleName,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public ExternalModule repositories(Map<String, Repository> repositories) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                pathPlacement,
                buildModuleName,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public ExternalModule resolvers(Map<String, Resolver> resolvers) {
        return new ExternalModule(coordinate,
                dependencyModule,
                additionalDependencies,
                pathPlacement,
                buildModuleName,
                group,
                properties,
                inputs,
                repositories,
                resolvers,
                environment);
    }

    public BuildExecutorModule resolution() {
        return (buildExecutor, inherited) -> {
            List<String> coordinates = new ArrayList<>(additionalDependencies.size() + 1);
            coordinates.add(coordinate);
            coordinates.addAll(additionalDependencies);
            buildExecutor.addStep(COORDINATE,
                    new WriteCoordinates(group, coordinates),
                    inherited.sequencedKeySet().stream());
            buildExecutor.addModule(DEPENDENCIES,
                    dependencyModule.pathPlacement(pathPlacement),
                    COORDINATE);
        };
    }

    @Override
    public BuildExecutorModule preparation() {
        return resolution();
    }

    @Override
    String identity() {
        return coordinate;
    }

    @Override
    SequencedSet<String> prepared() {
        return new LinkedHashSet<>(List.of(DEPENDENCIES));
    }

    @Override
    List<Path> artifacts(SequencedMap<String, Path> prepared) throws IOException {
        List<Path> artifacts = new ArrayList<>(Dependencies.select(prepared.get(PREVIOUS + DEPENDENCIES), group, "runtime"));
        artifacts.sort(null);
        return artifacts;
    }

    private record WriteCoordinates(String group, List<String> coordinates) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties properties = new SequencedProperties();
            for (String coordinate : coordinates) {
                properties.setProperty(group + "/runtime/" + coordinate, "");
            }
            properties.store(context.next().resolve(BuildStep.REQUIRES));
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

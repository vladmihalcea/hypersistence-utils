package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class InferredArtifactQualityModule implements BuildExecutorModule {

    public static final String JAPICMP = "japicmp", REVAPI = "revapi";
    private static final String CONFIGURATION = "configuration", ANALYSIS = "analysis";

    private final SequencedSet<Path> configuration;
    private final ExternalModule japicmpModule;
    private final boolean japicmp;
    private final ExternalModule revapiModule;
    private final boolean revapi;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration,
                ExternalModule.official(Environment.NONE, "japicmp", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "revapi", null, repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
    }

    public static InferredArtifactQualityModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        return new InferredArtifactQualityModule(configuration,
                ExternalModule.official(environment, "japicmp", null, repositories, resolvers),
                environment.flag("artifact.japicmp", true),
                ExternalModule.official(environment, "revapi", null, repositories, resolvers),
                environment.flag("artifact.revapi", true),
                Collections.emptyNavigableMap());
    }

    private InferredArtifactQualityModule(SequencedSet<Path> configuration,
                                          ExternalModule japicmpModule,
                                          boolean japicmp,
                                          ExternalModule revapiModule,
                                          boolean revapi,
                                          SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.japicmpModule = japicmpModule;
        this.japicmp = japicmp;
        this.revapiModule = revapiModule;
        this.revapi = revapi;
        this.custom = custom;
    }

    public InferredArtifactQualityModule japicmp(boolean japicmp) {
        return new InferredArtifactQualityModule(configuration,
                japicmpModule,
                japicmp,
                revapiModule,
                revapi,
                custom);
    }

    public InferredArtifactQualityModule revapi(boolean revapi) {
        return new InferredArtifactQualityModule(configuration,
                japicmpModule,
                japicmp,
                revapiModule,
                revapi,
                custom);
    }

    public InferredArtifactQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredArtifactQualityModule(configuration,
                japicmpModule,
                japicmp,
                revapiModule,
                revapi,
                custom);
    }

    public InferredArtifactQualityModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredArtifactQualityModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path described = BuildStep.locate(new LinkedHashSet<>(inherited.values()), BuildStep.MODULE);
        boolean test = described != null && SequencedProperties.ofFiles(described).getProperty("test") != null;
        Path japicmpFile = BuildStep.locate(configuration, "japicmp.properties");
        if (japicmpFile != null && !test && japicmp) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(japicmpFile).forEachProperty(values::put);
            buildExecutor.addModule(JAPICMP, japicmpModule.properties(values), inherited.sequencedKeySet());
        }
        Path revapiFile = BuildStep.locate(configuration, "revapi.properties");
        if (revapiFile != null && !test && revapi) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(revapiFile).forEachProperty(values::put);
            String analysis = values.remove(CONFIGURATION);
            ExternalModule module = revapiModule.properties(values);
            if (analysis != null) {
                module = module.bound(ANALYSIS, Path.of("revapi.json"), ExternalModule.named(revapiFile, CONFIGURATION, analysis));
            }
            buildExecutor.addModule(REVAPI, module, inherited.sequencedKeySet());
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}

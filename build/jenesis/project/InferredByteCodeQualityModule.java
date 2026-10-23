package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;

public class InferredByteCodeQualityModule implements BuildExecutorModule {

    public static final String JDEPS = "jdeps", JDEPRSCAN = "jdeprscan", SPOTBUGS = "spotbugs", FORBIDDENAPIS = "forbiddenapis";
    private static final String ACCEPT = "accept", REMOVAL = "removal", RELEASE = "release", SIGNATURES = "signatures";

    private final SequencedSet<Path> configuration;
    private final JDepsModule jdepsModule;
    private final Function<JDepsModule, BuildExecutorModule> jdeps;
    private final JDeprScanModule jdeprscanModule;
    private final Function<JDeprScanModule, BuildExecutorModule> jdeprscan;
    private final ExternalModule spotbugsModule;
    private final boolean spotbugs;
    private final ExternalModule forbiddenapisModule;
    private final boolean forbiddenapis;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration,
                new JDepsModule(),
                value -> value,
                new JDeprScanModule(),
                value -> value,
                ExternalModule.official(Environment.NONE, "spotbugs", null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, "forbiddenapis", null, repositories, resolvers),
                true,
                Collections.emptyNavigableMap());
    }

    public static InferredByteCodeQualityModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredByteCodeQualityModule module = new InferredByteCodeQualityModule(configuration,
                JDepsModule.ofEnvironment(environment),
                value -> value,
                JDeprScanModule.ofEnvironment(environment),
                value -> value,
                ExternalModule.official(environment, "spotbugs", null, repositories, resolvers),
                environment.flag("validate.spotbugs", true),
                ExternalModule.official(environment, "forbiddenapis", null, repositories, resolvers),
                environment.flag("validate.forbiddenapis", true),
                Collections.emptyNavigableMap());
        if (!environment.flag("validate.jdeps", true)) {
            module = module.jdeps(null);
        }
        if (!environment.flag("validate.jdeprscan", true)) {
            module = module.jdeprscan(null);
        }
        return module;
    }

    private InferredByteCodeQualityModule(SequencedSet<Path> configuration,
                                          JDepsModule jdepsModule,
                                          Function<JDepsModule, BuildExecutorModule> jdeps,
                                          JDeprScanModule jdeprscanModule,
                                          Function<JDeprScanModule, BuildExecutorModule> jdeprscan,
                                          ExternalModule spotbugsModule,
                                          boolean spotbugs,
                                          ExternalModule forbiddenapisModule,
                                          boolean forbiddenapis,
                                          SequencedMap<String, BuildExecutorModule> custom) {
        this.configuration = configuration;
        this.jdepsModule = jdepsModule;
        this.jdeps = jdeps;
        this.jdeprscanModule = jdeprscanModule;
        this.jdeprscan = jdeprscan;
        this.spotbugsModule = spotbugsModule;
        this.spotbugs = spotbugs;
        this.forbiddenapisModule = forbiddenapisModule;
        this.forbiddenapis = forbiddenapis;
        this.custom = custom;
    }

    public InferredByteCodeQualityModule jdeps(Function<JDepsModule, BuildExecutorModule> jdeps) {
        return new InferredByteCodeQualityModule(configuration,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                custom);
    }

    public InferredByteCodeQualityModule jdeprscan(Function<JDeprScanModule, BuildExecutorModule> jdeprscan) {
        return new InferredByteCodeQualityModule(configuration,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                custom);
    }

    public InferredByteCodeQualityModule spotbugs(boolean spotbugs) {
        return new InferredByteCodeQualityModule(configuration,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                custom);
    }

    public InferredByteCodeQualityModule forbiddenapis(boolean forbiddenapis) {
        return new InferredByteCodeQualityModule(configuration,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                custom);
    }

    public InferredByteCodeQualityModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredByteCodeQualityModule(configuration,
                jdepsModule,
                jdeps,
                jdeprscanModule,
                jdeprscan,
                spotbugsModule,
                spotbugs,
                forbiddenapisModule,
                forbiddenapis,
                custom);
    }

    public InferredByteCodeQualityModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredByteCodeQualityModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path spotbugsFile = BuildStep.locate(configuration, "spotbugs.properties"),
                exclusionFile = BuildStep.locate(configuration, "spotbugs-exclude.xml");
        if ((spotbugsFile != null || exclusionFile != null) && spotbugs) {
            ExternalModule module = spotbugsModule;
            if (spotbugsFile == null) {
                module = module.bound(ExternalModule.RULES, Path.of("spotbugs-exclude.xml"), exclusionFile);
            } else {
                SequencedMap<String, String> values = new LinkedHashMap<>();
                SequencedProperties.ofFiles(spotbugsFile).forEachProperty(values::put);
                String exclusion = values.remove(ExternalModule.CONFIGURATION);
                module = module.properties(values);
                if (exclusion != null && !exclusion.isBlank()) {
                    module = module.bound(ExternalModule.RULES,
                            Path.of("spotbugs-exclude.xml"),
                            ExternalModule.named(spotbugsFile, ExternalModule.CONFIGURATION, exclusion));
                }
            }
            buildExecutor.addModule(SPOTBUGS, module, inherited.sequencedKeySet());
        }
        Path forbiddenapisFile = BuildStep.locate(configuration, "forbiddenapis.properties");
        if (forbiddenapisFile != null && forbiddenapis) {
            SequencedProperties properties = SequencedProperties.ofFiles(forbiddenapisFile);
            SequencedMap<String, String> values = new LinkedHashMap<>();
            properties.forEachProperty(values::put);
            ExternalModule module = forbiddenapisModule.properties(values);
            for (String name : Objects.requireNonNullElse(properties.entries(SIGNATURES), List.<String>of())) {
                Path signature = ExternalModule.named(forbiddenapisFile, SIGNATURES, name);
                module = module.bound(SIGNATURES, signature.getFileName(), signature);
            }
            buildExecutor.addModule(FORBIDDENAPIS, module, inherited.sequencedKeySet());
        }
        Path jdepsFile = JDepsModule.configurationFile(configuration);
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                JDEPS,
                jdeps,
                jdepsFile,
                properties -> accepting(jdepsModule, jdepsFile, properties));
        Path jdeprscanFile = JDeprScanModule.configurationFile(configuration);
        Bind.configuredByProperties(buildExecutor,
                inherited.sequencedKeySet(),
                JDEPRSCAN,
                jdeprscan,
                jdeprscanFile,
                properties -> scanning(jdeprscanModule, jdeprscanFile, properties));
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    private static JDepsModule accepting(JDepsModule module, Path file, SequencedProperties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals(ACCEPT)) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes " + ACCEPT
                        + "=<the JDK internal classes or packages the classes may use> alone");
            }
        }
        return module.accepted(Objects.requireNonNullElse(properties.entries(ACCEPT), List.of()));
    }

    private static JDeprScanModule scanning(JDeprScanModule module, Path file, SequencedProperties properties) {
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals(REMOVAL) && !key.equals(RELEASE)) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes " + REMOVAL
                        + "=false to report every deprecated API rather than those deprecated for removal, and "
                        + RELEASE + "=<the Java release whose deprecations to check>");
            }
        }
        String release = properties.value(RELEASE);
        if (release != null && !release.matches("[0-9]+")) {
            throw new IllegalArgumentException(file + " names the release " + release
                    + ", but takes a Java release as a number, as 21");
        }
        return module.forRemoval(properties.flag(REMOVAL, true))
                .release(release == null ? null : Integer.valueOf(release));
    }
}

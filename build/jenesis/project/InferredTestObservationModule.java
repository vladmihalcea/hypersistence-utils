package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.ObservabilityEngine;
import build.jenesis.Observation;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.TestFramework;

public class InferredTestObservationModule implements BuildExecutorModule {

    public static final String TEST = "test",
            OBSERVERS = "observers",
            OBSERVATIONS = "observations",
            FRAMEWORKS = "frameworks",
            JACOCO = "jacoco",
            ASYNCPROFILER = "asyncprofiler",
            PITEST = "pitest",
            MUTATE = "mutate";
    private static final String FRAMEWORK = "framework", ENGINES = "engines", SCAN = "scan";
    private static final Set<String> TEST_KEYS = Set.of(FRAMEWORK, ENGINES, SCAN);

    private final SequencedSet<Path> configuration;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final String moduleName;
    private final TestModule testModule;
    private final Function<TestModule, BuildExecutorModule> test;
    private final Function<NativeImageAgentModule, BuildExecutorModule> nativeImage;
    private final UnaryOperator<Jfr> jfr;
    private final SequencedMap<String, BuildExecutorModule> custom;
    private final SequencedMap<String, PluginModule<?>> observers;
    private final SequencedMap<String, PluginModule<?>> frameworks;
    private final SequencedSet<String> untested;
    private final ExternalModule jacocoModule;
    private final boolean jacoco;
    private final ExternalModule asyncProfilerModule;
    private final boolean asyncProfiler;
    private final ExternalModule pitestModule;
    private final boolean pitest;

    public InferredTestObservationModule(SequencedSet<Path> configuration,
                                         Map<String, Repository> repositories,
                                         Map<String, Resolver> resolvers) {
        this(configuration,
                null,
                PathPlacement.CLASS_PATH,
                null,
                new TestModule(repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                ExternalModule.official(Environment.NONE, JACOCO, null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, ASYNCPROFILER, null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, PITEST, null, repositories, resolvers),
                true);
    }

    public static InferredTestObservationModule ofEnvironment(Environment environment,
                                                              SequencedSet<Path> configuration,
                                                              Map<String, Repository> repositories,
                                                              Map<String, Resolver> resolvers) {
        InferredTestObservationModule module = new InferredTestObservationModule(configuration,
                null,
                PathPlacement.CLASS_PATH,
                null,
                TestModule.ofEnvironment(environment, repositories, resolvers),
                value -> value,
                value -> value,
                value -> value,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                ExternalModule.official(environment, JACOCO, null, repositories, resolvers),
                environment.flag("observe.jacoco", true),
                ExternalModule.official(environment, ASYNCPROFILER, null, repositories, resolvers),
                environment.flag("observe.asyncprofiler", true),
                ExternalModule.official(environment, PITEST, null, repositories, resolvers),
                environment.flag("observe.pitest", true));
        Boolean nativeImage = environment.flagOrNull("observe.native");
        if (nativeImage != null) {
            module = module.nativeImage(nativeImage ? value -> value : null);
        }
        Boolean jfr = environment.flagOrNull("observe.jfr");
        if (jfr != null) {
            module = module.jfr(jfr ? value -> value : null);
        }
        return module;
    }

    private static Jfr recorded(Path file) throws IOException {
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedMap<String, String> options = new TreeMap<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.value(key);
            if (key.equals("filename")) {
                throw new IllegalArgumentException(file + " names a filename, which the build sets to the recording"
                        + " in the test step's reports/jfr/ - remove the filename line");
            } else if (value == null) {
                throw new IllegalArgumentException(file + " gives " + key + " no value, but an option of the recording"
                        + " is <key>=<value>");
            } else if (value.contains(",")) {
                throw new IllegalArgumentException(file + " gives " + key + " the value " + value + ", but the value of"
                        + " an option of the recording holds no comma");
            }
            options.put(key, value);
        }
        return new Jfr(options);
    }

    private static TestModule declared(TestModule module,
                                       Path file,
                                       SequencedMap<String, TestFramework> frameworks) throws IOException {
        if (file == null) {
            return module;
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            if (!TEST_KEYS.contains(key)) {
                throw new IllegalArgumentException("Unknown test property: " + key);
            }
        }
        String framework = properties.value(FRAMEWORK), engines = properties.value(ENGINES);
        List<String> scan = properties.entries(SCAN);
        String named = framework == null ? null : ProjectPlugins.PLUGIN + "/" + framework;
        TestModule declared = framework == null ? module
                : frameworks.containsKey(named) ? frameworks.get(named) == null ? module : module.framework(frameworks.get(named))
                : module.framework(TestFrameworks.named(framework));
        declared = scan == null ? declared : declared.scan(scan);
        return engines == null ? declared : declared.engines(engines);
    }

    private static Observation observation(Map<String, Observation> observations,
                                           Map.Entry<String, PluginModule<?>> observer,
                                           SequencedMap<String, Path> inherited) throws IOException {
        Observation observation = observations.get(observer.getKey());
        if (observation == null) {
            observation = observer.getValue().observation(PluginModule.prepared(OBSERVERS, observer.getKey(), inherited));
            observations.put(observer.getKey(), observation);
        }
        return observation;
    }

    private InferredTestObservationModule(SequencedSet<Path> configuration,
                                          Pinning pinning,
                                          PathPlacement pathPlacement,
                                          String moduleName,
                                          TestModule testModule,
                                          Function<TestModule, BuildExecutorModule> test,
                                          Function<NativeImageAgentModule, BuildExecutorModule> nativeImage,
                                          UnaryOperator<Jfr> jfr,
                                          SequencedMap<String, BuildExecutorModule> custom,
                                          SequencedMap<String, PluginModule<?>> observers,
                                          SequencedMap<String, PluginModule<?>> frameworks,
                                          SequencedSet<String> untested,
                                          ExternalModule jacocoModule,
                                          boolean jacoco,
                                          ExternalModule asyncProfilerModule,
                                          boolean asyncProfiler,
                                          ExternalModule pitestModule,
                                          boolean pitest) {
        this.configuration = configuration;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.moduleName = moduleName;
        this.testModule = testModule;
        this.test = test;
        this.nativeImage = nativeImage;
        this.jfr = jfr;
        this.custom = custom;
        this.observers = observers;
        this.frameworks = frameworks;
        this.untested = untested;
        this.jacocoModule = jacocoModule;
        this.jacoco = jacoco;
        this.asyncProfilerModule = asyncProfilerModule;
        this.asyncProfiler = asyncProfiler;
        this.pitestModule = pitestModule;
        this.pitest = pitest;
    }

    public InferredTestObservationModule pinning(Pinning pinning) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule moduleName(String moduleName) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule test(Function<TestModule, BuildExecutorModule> test) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule nativeImage(Function<NativeImageAgentModule, BuildExecutorModule> nativeImage) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule jfr(UnaryOperator<Jfr> jfr) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule observers(SequencedMap<String, ? extends BuildExecutorModule> observers) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                PluginModule.plugins("observe", "ObservabilityFactory", observers),
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule frameworks(SequencedMap<String, ? extends BuildExecutorModule> frameworks) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                PluginModule.plugins("test", "TestFrameworkFactory", frameworks),
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule untested(SequencedSet<String> untested) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule jacoco(boolean jacoco) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule asyncProfiler(boolean asyncProfiler) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule pitest(boolean pitest) {
        return new InferredTestObservationModule(configuration,
                pinning,
                pathPlacement,
                moduleName,
                testModule,
                test,
                nativeImage,
                jfr,
                custom,
                observers,
                frameworks,
                untested,
                jacocoModule,
                jacoco,
                asyncProfilerModule,
                asyncProfiler,
                pitestModule,
                pitest);
    }

    public InferredTestObservationModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredTestObservationModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public Optional<String> resolve(String path) {
        return PluginModule.exposed(Set.of(OBSERVERS, FRAMEWORKS), path);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path described = BuildStep.locate(new LinkedHashSet<>(inherited.values()), BuildStep.MODULE);
        SequencedProperties description = described == null ? null : SequencedProperties.ofFiles(described);
        if (description != null && (description.getProperty("test") == null || description.flag("abstract"))) {
            return;
        }
        SequencedMap<String, BuildExecutorModule> reports = new LinkedHashMap<>();
        List<ObservabilityEngine> engines = new ArrayList<>();
        SequencedMap<String, PluginModule<?>> observers = new LinkedHashMap<>();
        SequencedMap<String, ExternalModule> official = new LinkedHashMap<>();
        if (jacoco) {
            official.put(JACOCO, jacocoModule);
        }
        if (asyncProfiler) {
            official.put(ASYNCPROFILER, asyncProfilerModule);
        }
        for (Map.Entry<String, ExternalModule> observer : official.entrySet()) {
            Path file = BuildStep.locate(configuration, observer.getKey() + ".properties");
            if (file != null) {
                SequencedMap<String, String> values = new LinkedHashMap<>();
                SequencedProperties.ofFiles(file).forEachProperty(values::put);
                observers.put(observer.getKey(), observer.getValue().properties(values));
            }
        }
        observers.putAll(this.observers);
        if (nativeImage != null && BuildStep.locate(configuration, "graal.properties") != null) {
            BuildExecutorModule report = nativeImage.apply(new NativeImageAgentModule());
            if (report != null) {
                NativeImageAgent engine = new NativeImageAgent();
                engines.add(engine);
                reports.put(engine.name(), report);
            }
        }
        Path recording = BuildStep.locate(configuration, "jfr.properties");
        if (jfr != null && recording != null) {
            Jfr engine = jfr.apply(recorded(recording));
            if (engine != null) {
                engines.add(engine);
            }
        }
        if (test != null) {
            Path declaration = BuildStep.locate(configuration, "test.properties");
            TestModule module = testModule.observe(engines)
                    .pinning(pinning)
                    .pathPlacement(pathPlacement)
                    .moduleName(moduleName == null && description != null ? description.getProperty("module") : moduleName);
            SequencedMap<String, TestFramework> unloaded = new LinkedHashMap<>();
            frameworks.keySet().forEach(name -> unloaded.put(name, null));
            BuildExecutorModule executed = test.apply(declared(module, declaration, unloaded));
            if (executed != null) {
                SequencedSet<String> testInputs = inherited.sequencedKeySet().stream()
                        .filter(identity -> untested.stream().noneMatch(input -> identity.equals(PREVIOUS + input)
                                || identity.startsWith(PREVIOUS + input + "/")))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                SequencedSet<String> reportInputs = new LinkedHashSet<>();
                reportInputs.add(TEST);
                reportInputs.addAll(inherited.sequencedKeySet());
                if (observers.isEmpty() && frameworks.isEmpty()) {
                    buildExecutor.addModule(TEST, executed, testInputs);
                } else {
                    Map<String, Observation> observations = new ConcurrentHashMap<>();
                    for (Map.Entry<String, ? extends SequencedMap<String, ? extends PluginModule<?>>> plugins : List.of(
                            Map.entry(OBSERVERS, observers),
                            Map.entry(FRAMEWORKS, frameworks))) {
                        if (!plugins.getValue().isEmpty()) {
                            buildExecutor.addModule(plugins.getKey(), (nested, nestedInherited) -> {
                                SequencedMap<String, BuildExecutorModule> preparations = new LinkedHashMap<>();
                                plugins.getValue().forEach((name, plugin) -> preparations.put(name, plugin.preparation()));
                                PluginModule.addModules(nested, preparations, nestedInherited.sequencedKeySet());
                            }, inherited.sequencedKeySet());
                            testInputs.add(plugins.getKey());
                        }
                    }
                    buildExecutor.addModule(TEST, (tested, testInherited) -> {
                        List<ObservabilityEngine> observed = new ArrayList<>(engines);
                        for (Map.Entry<String, PluginModule<?>> observer : observers.entrySet()) {
                            observed.add(observation(observations, observer, testInherited).engine());
                        }
                        SequencedMap<String, TestFramework> loaded = new LinkedHashMap<>();
                        for (Map.Entry<String, PluginModule<?>> framework : frameworks.entrySet()) {
                            loaded.put(framework.getKey(),
                                    framework.getValue().framework(PluginModule.prepared(FRAMEWORKS, framework.getKey(), testInherited)));
                        }
                        BuildExecutorModule observing = test.apply(declared(module.observe(observed)
                                .frameworks(List.copyOf(loaded.values())), declaration, loaded));
                        if (observing != null) {
                            observing.accept(tested, PluginModule.unprepared(Set.of(OBSERVERS, FRAMEWORKS), testInherited));
                        }
                    }, testInputs);
                    if (!observers.isEmpty()) {
                        SequencedSet<String> observationInputs = new LinkedHashSet<>(reportInputs);
                        observationInputs.add(OBSERVERS);
                        buildExecutor.addModule(OBSERVATIONS, (nested, nestedInherited) -> {
                            SequencedMap<String, BuildExecutorModule> reported = new LinkedHashMap<>();
                            for (Map.Entry<String, PluginModule<?>> observer : observers.entrySet()) {
                                reported.put(observer.getKey(), (reporting, reportInherited) -> {
                                    BuildExecutorModule report = observation(observations, observer, reportInherited).report();
                                    if (report != null) {
                                        report.accept(reporting, PluginModule.unprepared(Set.of(OBSERVERS, FRAMEWORKS), reportInherited));
                                    }
                                });
                            }
                            PluginModule.addModules(nested, reported, nestedInherited.sequencedKeySet());
                        }, observationInputs);
                    }
                }
                reports.forEach((name, report) -> buildExecutor.addModule(name, report, reportInputs));
            }
        }
        Path mutation = BuildStep.locate(configuration, "pitest.properties");
        if (mutation != null && pitest) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(mutation).forEachProperty(values::put);
            buildExecutor.addModule(MUTATE, pitestModule.properties(values), inherited.sequencedKeySet());
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }
}

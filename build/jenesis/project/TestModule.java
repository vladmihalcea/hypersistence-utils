package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.ObservabilityEngine;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.TestFramework;
import build.jenesis.TestTags;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Java;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;
import javax.lang.model.SourceVersion;

public class TestModule implements BuildExecutorModule {

    public static final String REQUIRED = "required", ARTIFACTS = "artifacts", EXECUTED = "executed";
    private static final String RESOLVED = "resolved", DEPENDENCIES = "dependencies";

    private final TestFramework framework;
    private final List<TestFramework> frameworks;
    private final Predicate<String> isTest;
    private final Function<List<String>, ProcessHandler.OfProcess> factory;
    private final Map<String, Resolver> resolvers;
    private final Dependencies dependencies;
    private final boolean jarsOnly;
    private final boolean requireFramework;
    private final Pinning pinning;
    private final PathPlacement pathPlacement;
    private final String moduleName;
    private final String filter;
    private final String exclude;
    private final String tag;
    private final String engines;
    private final List<String> scan;
    private final boolean force;
    private final boolean parallel;
    private final boolean reporting;
    private final String group;
    private final List<ObservabilityEngine> observers;
    private final String incrementalDigest;
    private final ProcessBuildStep.Terms terms;
    private final boolean skip;

    public TestModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(null,
                List.of(),
                defaultIsTest(),
                null,
                resolvers,
                new Dependencies(repositories, resolvers),
                true,
                true,
                null,
                PathPlacement.CLASS_PATH,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                false,
                false,
                false,
                "main",
                List.of(),
                null,
                new ProcessBuildStep.Terms(),
                false);
    }

    public static TestModule ofEnvironment(Environment environment,
                                    Map<String, Repository> repositories,
                                    Map<String, Resolver> resolvers) {
        return new TestModule(null,
                List.of(),
                defaultIsTest(),
                null,
                resolvers,
                Dependencies.ofEnvironment(environment, repositories, resolvers),
                environment.flag("test.jars", true),
                true,
                null,
                PathPlacement.CLASS_PATH,
                null,
                environment.getProperty("test.filter"),
                environment.getProperty("test.exclude"),
                environment.getProperty("test.tag"),
                null,
                List.of(),
                environment.flag("test.force"),
                environment.flag("test.parallel"),
                environment.flag("test.reporting"),
                "main",
                List.of(),
                incrementalDigest(environment.getProperty("test.incremental")),
                ProcessBuildStep.Terms.ofEnvironment(environment, "tests"),
                environment.flag("test.skip"));
    }

    private static String incrementalDigest(String property) {
        if (property == null || property.strip().equals("false")) {
            return null;
        } else if (property.isBlank() || property.strip().equals("true")) {
            return "MD5";
        } else if (!Security.getAlgorithms("MessageDigest").contains(property.strip().toUpperCase(Locale.ROOT))) {
            throw new IllegalArgumentException("jenesis.test.incremental is true, false or the name of a message digest,"
                    + " one of " + new TreeSet<>(Security.getAlgorithms("MessageDigest")) + ": " + property);
        }
        return property.strip();
    }

    private static Predicate<String> defaultIsTest() {
        List<Pattern> patterns = Stream.of(
                        "Test[^.$]*", "[^.$]*Test", "[^.$]*Tests", "[^.$]*TestCase",
                        "IT[^.$]*", "[^.$]*IT", "[^.$]*ITCase")
                .map(pattern -> Pattern.compile("(?:.*\\.)?" + pattern))
                .toList();
        return (Predicate<String> & Serializable)
                (name -> patterns.stream().anyMatch(pattern -> pattern.matcher(name).matches()));
    }

    private TestModule(TestFramework framework,
                       List<TestFramework> frameworks,
                       Predicate<String> isTest,
                       Function<List<String>, ProcessHandler.OfProcess> factory,
                       Map<String, Resolver> resolvers,
                       Dependencies dependencies,
                       boolean jarsOnly,
                       boolean requireFramework,
                       Pinning pinning,
                       PathPlacement pathPlacement,
                       String moduleName,
                       String filter,
                       String exclude,
                       String tag,
                       String engines,
                       List<String> scan,
                       boolean force,
                       boolean parallel,
                       boolean reporting,
                       String group,
                       List<ObservabilityEngine> observers,
                       String incrementalDigest,
                       ProcessBuildStep.Terms terms,
                       boolean skip) {
        this.skip = skip;
        this.framework = framework;
        this.frameworks = frameworks;
        this.isTest = isTest;
        this.factory = factory;
        this.resolvers = resolvers;
        this.dependencies = dependencies;
        this.jarsOnly = jarsOnly;
        this.requireFramework = requireFramework;
        this.pinning = pinning;
        this.pathPlacement = pathPlacement;
        this.moduleName = moduleName;
        this.filter = filter;
        this.exclude = exclude;
        this.tag = tag;
        this.engines = engines;
        this.scan = scan;
        this.force = force;
        this.parallel = parallel;
        this.reporting = reporting;
        this.group = group;
        this.observers = observers;
        this.incrementalDigest = incrementalDigest;
        this.terms = terms;
    }

    public TestModule framework(TestFramework framework) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule frameworks(List<TestFramework> frameworks) {
        return new TestModule(framework,
                List.copyOf(frameworks),
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public <P extends Predicate<String> & Serializable> TestModule isTest(P isTest) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule factory(Function<List<String>, ProcessHandler.OfProcess> factory) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule filter(String filter) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule exclude(String exclude) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule jarsOnly(boolean jarsOnly) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule requireFramework(boolean requireFramework) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule pinning(Pinning pinning) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule pathPlacement(PathPlacement pathPlacement) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule moduleName(String moduleName) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule tag(String tag) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule engines(String engines) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule scan(List<String> scan) {
        for (String dependency : scan) {
            int slash = dependency.indexOf('/');
            if (slash < 0
                    ? !SourceVersion.isName(dependency)
                    : slash == 0 || slash == dependency.length() - 1 || dependency.indexOf('/', slash + 1) >= 0) {
                throw new IllegalArgumentException("The scan key of test.properties names the dependencies whose test"
                        + " classes run among the module's own, comma-separated, each by its module name or as"
                        + " <groupId>/<artifactId>, as org.example.tck or org.example/tck: " + dependency);
            }
        }
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                List.copyOf(scan),
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule force(boolean force) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule group(String group) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule parallel(boolean parallel) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule reporting(boolean reporting) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule observe(ObservabilityEngine... observers) {
        return observe(List.of(observers));
    }

    public TestModule observe(List<ObservabilityEngine> observers) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule printing(BiConsumer<Boolean, String> printing) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    public TestModule skip(boolean skip) {
        return new TestModule(framework,
                frameworks,
                isTest,
                factory,
                resolvers,
                dependencies,
                jarsOnly,
                requireFramework,
                pinning,
                pathPlacement,
                moduleName,
                filter,
                exclude,
                tag,
                engines,
                scan,
                force,
                parallel,
                reporting,
                group,
                observers,
                incrementalDigest,
                terms,
                skip);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        if (!jarsOnly && pathPlacement.modular() && moduleName != null) {
            throw new IllegalArgumentException("The tests of module " + moduleName + " run on the module path, where"
                    + " a folder of resources is no part of the module: run them against its jar, as"
                    + " jenesis.test.jars=true does");
        }
        TestFramework resolved = framework;
        if (resolved == null) {
            resolved = TestFrameworks.detect(frameworks, () -> inherited.values().stream().iterator()).orElse(null);
            if (resolved == null) {
                if (requireFramework && !skip) {
                    throw new IllegalStateException(
                            "No test framework could be resolved from inherited dependencies: "
                                    + inherited.sequencedKeySet());
                }
                return;
            }
        }
        SequencedSet<String> upstream = inherited.sequencedKeySet();
        buildExecutor.addStep(RESOLVED,
                new Requires(group, resolved, frameworks, Set.copyOf(resolvers.keySet()), observers),
                upstream);
        SequencedSet<String> resolveInputs = new LinkedHashSet<>(upstream);
        resolveInputs.add(RESOLVED);
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).pathPlacement(pathPlacement),
                resolveInputs);
        if (skip) {
            return;
        }
        buildExecutor.addStep(EXECUTED,
                new Run(terms,
                        factory,
                        resolved,
                        frameworks,
                        isTest,
                        jarsOnly,
                        pathPlacement,
                        moduleName,
                        filter,
                        exclude,
                        tag,
                        engines,
                        scan,
                        force,
                        parallel,
                        reporting,
                        group,
                        observers,
                        incrementalDigest),
                Stream.concat(upstream.stream(), Stream.of(DEPENDENCIES)));
    }

    @Override
    public Optional<String> resolve(String path) {
        if (path.equals(EXECUTED)) {
            return Optional.of(EXECUTED);
        }
        if (path.equals(DEPENDENCIES)) {
            return Optional.of(ARTIFACTS);
        }
        return Optional.empty();
    }

    private record Requires(String group,
                            TestFramework framework,
                            List<TestFramework> frameworks,
                            Set<String> prefixes,
                            List<ObservabilityEngine> observers) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            List<Path> folders = arguments.values().stream()
                    .filter(argument -> !argument.removed())
                    .map(BuildStepArgument::folder)
                    .toList();
            List<ModuleDescriptor> artifacts = TestFrameworks.modules(folders);
            TestFramework resolved = framework != null ? framework : TestFrameworks.detect(frameworks, artifacts).orElse(null);
            SequencedProperties properties = new SequencedProperties();
            SequencedProperties versions = new SequencedProperties();
            SequencedMap<String, String> pinned = new LinkedHashMap<>();
            for (Path folder : folders) {
                Path versionsFile = folder.resolve(BuildStep.VERSIONS);
                if (Files.exists(versionsFile)) {
                    SequencedProperties.ofFiles(versionsFile).forEachProperty(pinned::putIfAbsent);
                }
            }
            if (resolved != null) {
                SequencedMap<String, String> runners = resolved.missingCoordinates(artifacts);
                String selectedPrefix = null;
                for (String coordinate : runners.sequencedKeySet()) {
                    int index = coordinate.indexOf('/');
                    String prefix = index > 0 ? coordinate.substring(0, index) : "";
                    if (selectedPrefix == null && prefixes.contains(prefix)) {
                        selectedPrefix = prefix;
                    }
                    if (prefix.equals(selectedPrefix)) {
                        properties.setProperty(group + "/runtime/" + coordinate, "");
                    }
                }
                if (selectedPrefix != null) {
                    for (Map.Entry<String, String> entry : pinned.entrySet()) {
                        if (entry.getKey().startsWith(group + "/" + selectedPrefix + "/")) {
                            versions.putIfAbsent(entry.getKey(), entry.getValue());
                        }
                    }
                    for (Map.Entry<String, String> entry : runners.entrySet()) {
                        String coordinate = entry.getKey(), version = entry.getValue();
                        int index = coordinate.indexOf('/');
                        if (version != null && index > 0 && selectedPrefix.equals(coordinate.substring(0, index))) {
                            versions.putIfAbsent(group + "/" + coordinate, version);
                        }
                    }
                }
            }
            for (ObservabilityEngine observer : observers) {
                String agent = observer.name() + ":agent";
                for (Map.Entry<String, String> entry : observer.coordinates().entrySet()) {
                    properties.setProperty(agent
                            + "/runtime/"
                            + entry.getKey()
                            + "/"
                            + released(pinned, observer.name(), entry.getKey(), entry.getValue()), "");
                }
            }
            properties.store(context.next().resolve(BuildStep.REQUIRES));
            if (!versions.isEmpty()) {
                versions.store(context.next().resolve(BuildStep.VERSIONS));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static String released(SequencedMap<String, String> pinned,
                                       String engine,
                                       String coordinate,
                                       String version) {
            int repository = coordinate.indexOf('/'), groupId = coordinate.indexOf('/', repository + 1);
            if (!version.equals("RELEASE") || repository < 1 || groupId < 0) {
                return version;
            }
            String own = engine + ":agent/" + coordinate, release = "/" + coordinate.substring(0, groupId + 1);
            String train = null;
            for (Map.Entry<String, String> entry : pinned.entrySet()) {
                String pin = entry.getValue().trim().split("\\s+", 2)[0];
                if (pin.isEmpty() || pin.startsWith(":")) {
                    continue;
                }
                if (entry.getKey().equals(own)) {
                    return pin;
                }
                int slash = entry.getKey().indexOf('/');
                String group = slash < 0 ? "" : entry.getKey().substring(0, slash);
                if (train == null
                        && (group.equals(engine) || group.startsWith(engine + ":"))
                        && entry.getKey().startsWith(release, slash)) {
                    train = pin;
                }
            }
            return train == null ? version : train;
        }
    }

    public record Scope(String filter, String exclude, List<TestTags> covered) {

        private static final String FILTER = "filter", EXCLUDE = "exclude", COVERED = "covered.";

        public Scope {
            filter = filter == null || filter.isBlank() ? null : filter;
            exclude = exclude == null || exclude.isBlank() ? null : exclude;
            covered = List.copyOf(covered);
        }

        public Scope(String filter, List<TestTags> covered) {
            this(filter, null, covered);
        }

        public static Scope ofFile(Path file) throws IOException {
            SequencedProperties recorded = SequencedProperties.ofFiles(file);
            List<TestTags> covered = new ArrayList<>();
            try {
                for (int index = 0; recorded.getProperty(COVERED + index) != null; index++) {
                    covered.add(TestTags.parse(recorded.getProperty(COVERED + index)));
                }
            } catch (IllegalArgumentException _) {
                covered.clear();
            }
            return new Scope(recorded.getProperty(FILTER), recorded.getProperty(EXCLUDE), covered);
        }

        public void store(Path file) throws IOException {
            SequencedProperties recorded = new SequencedProperties();
            if (filter != null) {
                recorded.setProperty(FILTER, filter);
            }
            if (exclude != null) {
                recorded.setProperty(EXCLUDE, exclude);
            }
            for (int index = 0; index < covered.size(); index++) {
                recorded.setProperty(COVERED + index, covered.get(index).toString());
            }
            recorded.store(file);
        }

        public boolean filters(String requested) {
            return entries(filter).equals(entries(requested));
        }

        public boolean excludes(String requested) {
            return entries(exclude).equals(entries(requested));
        }

        private static List<String> entries(String expression) {
            if (expression == null || expression.isBlank()) {
                return List.of();
            }
            List<String> entries = new ArrayList<>();
            for (String candidate : expression.split(",")) {
                String entry = candidate.trim();
                if (!entry.isEmpty()) {
                    entries.add(entry);
                }
            }
            return entries;
        }
    }

    private static class Run extends Java {

        private final TestFramework framework;
        private final List<TestFramework> frameworks;
        private final Predicate<String> isTest;
        private final String moduleName;
        private final transient String filter;
        private final transient String exclude;
        private final transient String tag;
        private final String engines;
        private final List<String> scan;
        private final transient boolean force;
        private final transient boolean parallel;
        private final boolean reporting;
        private final String group;
        private final List<ObservabilityEngine> observers;
        private final transient String incrementalDigest;

        private Run(ProcessBuildStep.Terms terms,
                    Function<List<String>, ProcessHandler.OfProcess> factory,
                    TestFramework framework,
                    List<TestFramework> frameworks,
                    Predicate<String> isTest,
                    boolean jarsOnly,
                    PathPlacement pathPlacement,
                    String moduleName,
                    String filter,
                    String exclude,
                    String tag,
                    String engines,
                    List<String> scan,
                    boolean force,
                    boolean parallel,
                    boolean reporting,
                    String group,
                    List<ObservabilityEngine> observers,
                    String incrementalDigest) {
            super(factory == null ? ProcessHandler.OfProcess.ofJavaHome("bin/java") : factory,
                  pathPlacement,
                  jarsOnly,
                  group,
                  terms);
            this.framework = framework;
            this.frameworks = frameworks;
            this.isTest = isTest;
            this.moduleName = moduleName;
            this.filter = filter;
            this.exclude = exclude;
            this.tag = tag;
            this.engines = engines;
            this.scan = scan;
            this.force = force;
            this.parallel = parallel;
            this.reporting = reporting;
            this.group = group;
            this.observers = observers;
            this.incrementalDigest = incrementalDigest;
        }

        @Override
        protected List<String> configurations() {
            return List.of("java", "test");
        }

        @Override
        protected Optional<String> diagnosis(BuildStepContext context) {
            Path reports = context.next().resolve(BuildStep.REPORTS + "tests");
            return reporting && Files.isDirectory(reports)
                    ? Optional.of("The reports of the test run are in " + reports)
                    : Optional.empty();
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            TestTags requested = TestTags.parse(tag);
            List<TestTags> ran = ran(context, arguments);
            if (requested.coveredBy(ran)) {
                return CompletableFuture.completedStage(new BuildStepResult(false));
            }
            List<TestTags> covered = new ArrayList<>(ran);
            covered.add(requested);
            new Scope(filter, exclude, covered).store(context.next().resolve("testscope.properties"));
            SequencedMap<String, String> handed = BuildStep.listing(arguments);
            return super.apply(executor, context, arguments).thenApply(completed -> {
                SequencedMap<String, String> left;
                try {
                    left = BuildStep.listing(arguments);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                if (!handed.equals(left)) {
                    SequencedSet<String> written = new TreeSet<>(handed.keySet());
                    written.addAll(left.keySet());
                    written.removeIf(key -> Objects.equals(handed.get(key), left.get(key)));
                    throw new IllegalStateException("The tests wrote into the folders the build handed them: "
                            + written + " - a test writes into its working directory or a temporary folder, never"
                            + " beside what it tests, as the steps after it read those folders as they were built");
                }
                return completed;
            });
        }

        private List<TestTags> ran(BuildStepContext context, SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            if (force || super.shouldRun(arguments) || context.previous() == null) {
                return List.of();
            }
            Path recorded = context.previous().resolve("testscope.properties");
            if (!Files.exists(recorded)) {
                return List.of();
            }
            Scope scope = Scope.ofFile(recorded);
            return scope.filters(filter) && scope.excludes(exclude) ? scope.covered() : List.of();
        }

        @Override
        protected CompletionStage<List<String>> commands(Executor executor,
                                                        BuildStepContext context,
                                                        SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            List<Path> folders = arguments.values().stream()
                    .filter(argument -> !argument.removed())
                    .map(BuildStepArgument::folder)
                    .toList();
            TestFramework resolved = (framework != null
                    ? framework
                    : TestFrameworks.detect(frameworks, folders)
                            .orElseThrow(() -> new IllegalArgumentException("No test framework found")))
                    .runningOn(TestFrameworks.jars(folders));
            String path = null, compiledFrom = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path module = argument.folder().resolve(BuildStep.MODULE);
                if (Files.isRegularFile(module)) {
                    SequencedProperties described = SequencedProperties.ofFiles(module);
                    path = described.getProperty("path");
                    String sources = described.value("sources");
                    compiledFrom = path == null || path.isEmpty() ? sources
                            : sources == null ? path
                            : path + "/" + sources;
                    break;
                }
            }
            List<TestSpec> requested = TestSpec.parse(filter), specs = new ArrayList<>();
            for (TestSpec spec : requested) {
                if (spec.module() == null || spec.module().equals(path)) {
                    specs.add(spec);
                }
            }
            if (!requested.isEmpty() && specs.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            }
            List<Pattern> excluded = new ArrayList<>();
            for (TestSpec spec : TestSpec.parse(exclude)) {
                if (spec.method() != null) {
                    throw new IllegalArgumentException("jenesis.test.exclude names test classes, as [<module>/]<regex>,"
                            + " but " + exclude + " names the method " + spec.method()
                            + " - narrow a class to some of its methods with jenesis.test.filter");
                } else if (spec.module() == null || spec.module().equals(path)) {
                    excluded.add(spec.classPattern());
                }
            }
            TestTags tags = TestTags.parse(tag);
            List<TestTags> ran = ran(context, arguments);
            List<String> commands = new ArrayList<>();
            for (ObservabilityEngine observer : observers) {
                commands.addAll(observer.commands(agentJars(arguments, observer), context.next()));
            }
            SequencedMap<String, String> attachments = attachments(arguments);
            SequencedMap<String, Path> attached = attachedJars(arguments, attachments.sequencedKeySet());
            for (Map.Entry<String, String> attachment : attachments.entrySet()) {
                Path jar = attached.get(attachment.getKey());
                if (jar == null) {
                    throw new IllegalStateException("No resolved artifact for attached agent " + attachment.getKey());
                }
                try (JarFile file = new JarFile(jar.toFile())) {
                    Manifest manifest = file.getManifest();
                    if (manifest == null || manifest.getMainAttributes().getValue("Premain-Class") == null) {
                        throw new IllegalStateException("Attached agent "
                                + attachment.getKey()
                                + " does not declare Premain-Class: "
                                + jar);
                    }
                }
                commands.add("-javaagent:" + jar.toAbsolutePath()
                        + (attachment.getValue().isEmpty() ? "" : "=" + attachment.getValue()));
            }
            for (Map.Entry<String, String> entry : resolved.systemProperties().entrySet()) {
                commands.add("-D" + entry.getKey() + "=" + entry.getValue());
            }
            SequencedSet<Path> scanned = new LinkedHashSet<>();
            for (String dependency : scan) {
                SequencedSet<Path> held = new LinkedHashSet<>();
                for (BuildStepArgument argument : arguments.values()) {
                    if (argument.removed()) {
                        continue;
                    }
                    if (dependency.indexOf('/') < 0) {
                        for (Path jar : Dependencies.select(argument.folder(), group, "runtime")) {
                            ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                            if (descriptor != null && descriptor.name().equals(dependency)) {
                                held.add(jar);
                            }
                        }
                    } else {
                        held.addAll(Dependencies.select(argument.folder(), group, "runtime/maven/" + dependency));
                    }
                }
                if (held.isEmpty()) {
                    throw new IllegalStateException("The tests of " + (path == null ? "this module" : path) + " scan "
                            + dependency + " for test classes, but no jar of it is on their test path - declare it"
                            + " a dependency of these tests, "
                            + (dependency.indexOf('/') < 0
                                    ? "as requires " + dependency + "; in their module-info.java"
                                    : "as a <dependency> on " + dependency.replace('/', ':') + " in their pom.xml,"
                                            + " or by requiring the module it holds")
                            + ", or remove it from the scan key of test.properties");
                }
                scanned.addAll(held);
            }
            if (pathPlacement.modular() && resolved.runnerModule() != null) {
                SequencedSet<String> added = new LinkedHashSet<>();
                if (moduleName != null) {
                    added.add(moduleName);
                }
                for (Path jar : scanned) {
                    ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                    if (descriptor != null && pathPlacement.test(jar)) {
                        added.add(descriptor.name());
                    }
                }
                if (!added.isEmpty()) {
                    commands.add("--add-modules");
                    commands.add(String.join(",", added));
                    Map<String, ModuleDescriptor> opened = new HashMap<>();
                    SequencedSet<String> targets = new TreeSet<>();
                    Set<String> reflecting = resolved.reflectingModules();
                    for (BuildStepArgument argument : arguments.values()) {
                        if (argument.removed()) {
                            continue;
                        }
                        List<Path> files = new ArrayList<>();
                        Path classes = argument.folder().resolve(CLASSES);
                        if (!jarsOnly && Files.isDirectory(classes)) {
                            files.add(classes);
                        }
                        Path artifacts = argument.folder().resolve(ARTIFACTS);
                        if (Files.isDirectory(artifacts)) {
                            try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifacts)) {
                                stream.forEach(files::add);
                            }
                        }
                        files.addAll(Dependencies.select(argument.folder(), group, "runtime"));
                        for (Path file : files) {
                            ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
                            if (descriptor == null) {
                                continue;
                            }
                            if (added.contains(descriptor.name())) {
                                if (!descriptor.isAutomatic() && !opened.containsKey(descriptor.name())) {
                                    opened.put(descriptor.name(), ModuleFinder.of(file)
                                            .find(descriptor.name())
                                            .orElseThrow()
                                            .descriptor());
                                }
                            } else if (reflecting.contains(descriptor.name()) && pathPlacement.test(file)) {
                                targets.add(descriptor.name());
                            }
                        }
                    }
                    String target = targets.isEmpty() ? "ALL-UNNAMED" : String.join(",", targets);
                    for (String name : added) {
                        ModuleDescriptor described = opened.get(name);
                        if (described != null && !described.isOpen()) {
                            for (String opening : new TreeSet<>(described.packages())) {
                                commands.add("--add-opens");
                                commands.add(name + "/" + opening + "=" + target);
                            }
                        }
                    }
                }
                commands.add("-m");
                commands.add(resolved.runnerModule() + "/" + resolved.runnerClass());
            } else {
                commands.add(resolved.runnerClass());
            }
            SequencedSet<String> matchedClasses = new TreeSet<>(), excludedClasses = new TreeSet<>();
            SequencedMap<String, SequencedSet<String>> matchedMethods = new TreeMap<>();
            ClassFile classFile = ClassFile.of();
            AtomicInteger compiled = new AtomicInteger();
            List<Path> classFolders = new ArrayList<>(), jars = new ArrayList<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path classes = argument.folder().resolve(CLASSES), artifacts = argument.folder().resolve(ARTIFACTS);
                if (Files.isDirectory(classes)) {
                    classFolders.add(classes);
                }
                if (Files.isDirectory(artifacts)) {
                    try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifacts, "*.jar")) {
                        stream.forEach(jars::add);
                    }
                }
                jars.addAll(Dependencies.select(argument.folder(), group, "runtime"));
            }
            Map<String, Optional<ClassModel>> located = new HashMap<>();
            Function<String, Optional<ClassModel>> locator = name -> located.computeIfAbsent(name, _ -> {
                try {
                    for (Path folder : classFolders) {
                        Path file = folder.resolve(name + ".class");
                        if (Files.isRegularFile(file)) {
                            return Optional.of(classFile.parse(file));
                        }
                    }
                    for (Path jar : jars) {
                        try (JarFile file = new JarFile(jar.toFile())) {
                            JarEntry entry = file.getJarEntry(name + ".class");
                            if (entry != null) {
                                try (InputStream input = file.getInputStream(entry)) {
                                    return Optional.of(classFile.parse(input.readAllBytes()));
                                }
                            }
                        }
                    }
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
                return Optional.empty();
            });
            BiConsumer<String, ClassModel> select = (className, model) -> {
                if ((model.flags().flagsMask() & (ClassFile.ACC_ABSTRACT | ClassFile.ACC_MODULE)) != 0) {
                    return;
                }
                boolean left = excluded.stream().anyMatch(pattern -> pattern.matcher(className).matches());
                if (specs.isEmpty()) {
                    if (isTest.test(className) && holdsTests(resolved, model, locator)) {
                        (left ? excludedClasses : matchedClasses).add(className);
                    }
                } else {
                    SequencedSet<String> methods = new TreeSet<>();
                    for (TestSpec spec : specs) {
                        if (spec.classPattern.matcher(className).matches()) {
                            if (left) {
                                excludedClasses.add(className);
                            } else if (spec.method == null) {
                                matchedClasses.add(className);
                            } else {
                                methods.add(spec.method);
                            }
                        }
                    }
                    if (!methods.isEmpty() && !matchedClasses.contains(className)) {
                        matchedMethods.put(className, methods);
                    }
                }
            };
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path classes = argument.folder().resolve(CLASSES);
                if (Files.exists(classes)) {
                    Files.walkFileTree(classes, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                            return directory.equals(classes.resolve("META-INF"))
                                    ? FileVisitResult.SKIP_SUBTREE
                                    : FileVisitResult.CONTINUE;
                        }

                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            if (file.toString().endsWith(".class")) {
                                String raw = classes.relativize(file).toString();
                                compiled.incrementAndGet();
                                select.accept(raw.substring(0, raw.length() - 6).replace(File.separatorChar, '.'),
                                        classFile.parse(file));
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
            }
            int contained = 0;
            for (Path jar : scanned) {
                try (JarFile archive = new JarFile(jar.toFile())) {
                    for (JarEntry entry : Collections.list(archive.entries())) {
                        String name = entry.getName();
                        if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                            contained++;
                            try (InputStream input = archive.getInputStream(entry)) {
                                select.accept(name.substring(0, name.length() - 6).replace('/', '.'),
                                        classFile.parse(input.readAllBytes()));
                            }
                        }
                    }
                }
            }
            if (matchedClasses.isEmpty() && matchedMethods.isEmpty() && !excludedClasses.isEmpty()) {
                return CompletableFuture.completedFuture(null);
            } else if (matchedClasses.isEmpty() && matchedMethods.isEmpty() && tags.all()) {
                String unmatched = "among the " + compiled.get() + " classes compiled"
                        + (compiledFrom == null ? "" : " from " + compiledFrom + ", so tests kept in another"
                                + " folder need it named as the test sources, as testSourceDirectory or"
                                + " jenesis.maven.sources names one of a pom.xml")
                        + (scanned.isEmpty() ? "" : ", nor among the " + contained + " classes of the scanned "
                                + scanned.stream().map(jar -> jar.getFileName().toString()).collect(Collectors.joining(", ")));
                if (!specs.isEmpty()) {
                    throw new IllegalStateException("No tests matched the requested selection, filter: " + filter
                            + ", " + unmatched
                            + ". Adjust jenesis.test.filter, or set jenesis.test.skip to skip testing.");
                }
                Consumer<String> reporting = terms.reporting();
                if (reporting != null) {
                    reporting.accept("tests ran no test, as no class " + unmatched
                            + " is named as a test, which jenesis.test.filter can change");
                }
                return CompletableFuture.completedFuture(null);
            }
            SequencedSet<String> selection = matchedClasses;
            if (incrementalDigest != null && filter == null && exclude == null && tags.all() && ran.isEmpty() && !matchedClasses.isEmpty()) {
                SequencedSet<String> narrowed = selected(arguments, context, matchedClasses);
                if (narrowed != null && narrowed.isEmpty()) {
                    return CompletableFuture.completedFuture(null);
                } else if (narrowed != null) {
                    selection = narrowed;
                }
            }
            commands.addAll(resolved.engines(resolved.tags(resolved.arguments(
                    context.supplement(),
                    context.next(),
                    selection,
                    matchedMethods,
                    parallel,
                    reporting), tags, ran), engines));
            return CompletableFuture.completedFuture(commands);
        }

        private static boolean holdsTests(TestFramework framework,
                                          ClassModel type,
                                          Function<String, Optional<ClassModel>> locator) {
            ClassModel current = type;
            while (!framework.holdsTests(current)) {
                String superclass = current.superclass().map(ClassEntry::asInternalName).orElse(null);
                if (superclass == null || superclass.startsWith("java/")) {
                    return false;
                }
                Optional<ClassModel> located = locator.apply(superclass);
                if (located.isEmpty()) {
                    return true;
                }
                current = located.get();
            }
            return true;
        }

        private SequencedSet<String> selected(SequencedMap<String, BuildStepArgument> arguments,
                                              BuildStepContext context,
                                              SequencedSet<String> candidates) throws IOException {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance(incrementalDigest);
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            SequencedProperties current = new SequencedProperties();
            Map<String, Set<String>> references = new LinkedHashMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path folder = argument.folder().resolve(CLASSES);
                if (Files.isDirectory(folder)) {
                    Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                        @Override
                        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                            String name = folder.relativize(file).toString();
                            if (name.endsWith(".class") && !name.equals("module-info.class")) {
                                String className = name.substring(0, name.length() - 6).replace(File.separatorChar, '.');
                                if (!references.containsKey(className)) {
                                    index(className, Files.readAllBytes(file), digest, current, references);
                                }
                            }
                            return FileVisitResult.CONTINUE;
                        }
                    });
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "runtime")) {
                    if (!Files.isRegularFile(jar)) {
                        continue;
                    }
                    try (JarFile archive = new JarFile(jar.toFile())) {
                        Enumeration<JarEntry> entries = archive.entries();
                        while (entries.hasMoreElements()) {
                            JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.endsWith(".class") && !name.startsWith("META-INF/") && !name.equals("module-info.class")) {
                                String className = name.substring(0, name.length() - 6).replace('/', '.');
                                if (!references.containsKey(className)) {
                                    try (InputStream input = archive.getInputStream(entry)) {
                                        index(className, input.readAllBytes(), digest, current, references);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            current.store(context.next().resolve("test-selection.properties"));
            Path baseline = context.previous() == null ? null : context.previous().resolve("test-selection.properties");
            if (baseline == null || !Files.exists(baseline)) {
                return null;
            }
            SequencedProperties previous = SequencedProperties.ofFiles(baseline);
            for (String name : previous.stringPropertyNames()) {
                if (current.getProperty(name) == null) {
                    return null;
                }
            }
            SequencedSet<String> changed = new LinkedHashSet<>();
            for (String name : current.stringPropertyNames()) {
                if (!current.getProperty(name).equals(previous.getProperty(name))) {
                    changed.add(name);
                }
            }
            if (changed.isEmpty()) {
                return null;
            }
            SequencedSet<String> impacted = TestSelection.of(references).impacted(changed);
            SequencedSet<String> result = new TreeSet<>();
            for (String candidate : candidates) {
                if (impacted.contains(candidate)) {
                    result.add(candidate);
                }
            }
            return result;
        }

        private static void index(String className,
                                  byte[] bytes,
                                  MessageDigest digest,
                                  SequencedProperties current,
                                  Map<String, Set<String>> references) {
            current.setProperty(className, HexFormat.of().formatHex(digest.digest(bytes)));
            references.put(className, TestSelection.references(bytes));
        }

        private static SequencedMap<String, String> attachments(SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, String> attachments = new LinkedHashMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(BuildStep.ATTACHMENTS);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties.ofFiles(file).forEachProperty(attachments::putIfAbsent);
            }
            return attachments;
        }

        private static String withoutScope(String key) {
            int first = key.indexOf('/');
            int second = first < 0 ? -1 : key.indexOf('/', first + 1);
            return second < 0 ? key : key.substring(0, first) + key.substring(second);
        }

        private static SequencedMap<String, Path> attachedJars(SequencedMap<String, BuildStepArgument> arguments,
                                                               SequencedSet<String> keys) throws IOException {
            SequencedMap<String, Path> resolved = new LinkedHashMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(BuildStep.DEPENDENCIES);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties properties = SequencedProperties.ofFiles(file);
                for (String key : keys) {
                    String target = withoutScope(key);
                    for (String candidate : properties.stringPropertyNames()) {
                        String compared = withoutScope(candidate);
                        if (compared.equals(target)
                                || compared.startsWith(target + "/")
                                && compared.indexOf('/', target.length() + 1) < 0) {
                            String value = properties.getProperty(candidate);
                            int space = value.indexOf(' ');
                            Path jar = argument.folder()
                                    .resolve(space < 0 ? value : value.substring(0, space))
                                    .normalize();
                            if (Files.isRegularFile(jar)) {
                                resolved.putIfAbsent(key, jar);
                            }
                        }
                    }
                }
            }
            return resolved;
        }

        private static SequencedMap<String, Path> agentJars(SequencedMap<String, BuildStepArgument> arguments,
                                                            ObservabilityEngine observer) throws IOException {
            SequencedMap<String, Path> resolved = new LinkedHashMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(BuildStep.DEPENDENCIES);
                if (!Files.exists(file)) {
                    continue;
                }
                SequencedProperties properties = SequencedProperties.ofFiles(file);
                for (String coordinate : observer.coordinates().sequencedKeySet()) {
                    String prefix = observer.name() + ":agent/runtime/" + coordinate + "/";
                    for (String key : properties.stringPropertyNames()) {
                        if (key.startsWith(prefix)) {
                            String value = properties.getProperty(key);
                            int space = value.indexOf(' ');
                            Path jar = argument.folder().resolve(space < 0 ? value : value.substring(0, space)).normalize();
                            if (Files.exists(jar)) {
                                resolved.putIfAbsent(coordinate, jar);
                            }
                        }
                    }
                }
            }
            return resolved;
        }

    }

    private record TestSpec(String module, Pattern classPattern, String method) {

        static List<TestSpec> parse(String spec) {
            if (spec == null || spec.isBlank()) {
                return List.of();
            }
            List<TestSpec> result = new ArrayList<>();
            for (String entry : spec.split(",")) {
                String trimmed = entry.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                int separator = trimmed.indexOf('#');
                String selection = separator < 0 ? trimmed : trimmed.substring(0, separator);
                int slash = selection.lastIndexOf('/');
                result.add(new TestSpec(slash < 0 ? null : selection.substring(0, slash),
                        Pattern.compile(selection.substring(slash + 1)),
                        separator < 0 ? null : trimmed.substring(separator + 1)));
            }
            return result;
        }
    }
}

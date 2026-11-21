package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Jar;
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessHandler;

public class InferredDocumentationModule implements BuildExecutorModule {

    public static final String GENERATE = "generate", EMPTY = "empty", ARCHIVE = "archive";

    private final InferredDocumentationChainModule generateModule;
    private final Function<InferredDocumentationChainModule, BuildExecutorModule> generate;
    private final BuildStep archiver;
    private final boolean empty;
    private final boolean tests;
    private final SequencedMap<String, BuildExecutorModule> custom;

    public InferredDocumentationModule(SequencedSet<Path> configuration,
                                       Map<String, Repository> repositories,
                                       Map<String, Resolver> resolvers) {
        this(new InferredDocumentationChainModule(configuration, repositories, resolvers),
                value -> value,
                new Jar(ProcessHandler.Factory.of(), Jar.Sort.JAVADOC),
                false,
                false,
                Collections.emptyNavigableMap());
    }

    public static InferredDocumentationModule ofEnvironment(Environment environment,
                                                            SequencedSet<Path> configuration,
                                                            Map<String, Repository> repositories,
                                                            Map<String, Resolver> resolvers) {
        return new InferredDocumentationModule(InferredDocumentationChainModule.ofEnvironment(environment,
                        configuration,
                        repositories,
                        resolvers),
                value -> value,
                Jar.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment), Jar.Sort.JAVADOC),
                environment.flag("documentation.empty", false),
                environment.flag("stage.tests"),
                Collections.emptyNavigableMap());
    }

    private InferredDocumentationModule(InferredDocumentationChainModule generateModule,
                                        Function<InferredDocumentationChainModule, BuildExecutorModule> generate,
                                        BuildStep archiver,
                                        boolean empty,
                                        boolean tests,
                                        SequencedMap<String, BuildExecutorModule> custom) {
        this.generateModule = generateModule;
        this.generate = generate;
        this.archiver = archiver;
        this.empty = empty;
        this.tests = tests;
        this.custom = custom;
    }

    public InferredDocumentationModule generate(Function<InferredDocumentationChainModule, BuildExecutorModule> generate) {
        return new InferredDocumentationModule(generateModule,
                generate,
                archiver,
                empty,
                tests,
                custom);
    }

    public InferredDocumentationModule archiver(BuildStep archiver) {
        return new InferredDocumentationModule(generateModule,
                generate,
                archiver,
                empty,
                tests,
                custom);
    }

    public InferredDocumentationModule empty(boolean empty) {
        return new InferredDocumentationModule(generateModule,
                generate,
                archiver,
                empty,
                tests,
                custom);
    }

    public InferredDocumentationModule tests(boolean tests) {
        return new InferredDocumentationModule(generateModule,
                generate,
                archiver,
                empty,
                tests,
                custom);
    }

    public InferredDocumentationModule generateModule(InferredDocumentationChainModule generateModule) {
        return new InferredDocumentationModule(generateModule,
                generate,
                archiver,
                empty,
                tests,
                custom);
    }

    public InferredDocumentationModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredDocumentationModule(generateModule,
                generate,
                archiver,
                empty,
                tests,
                custom);
    }

    public InferredDocumentationModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredDocumentationModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        Path described = BuildStep.locate(new LinkedHashSet<>(inherited.values()), BuildStep.MODULE);
        if (!tests && described != null && SequencedProperties.ofFiles(described).getProperty("test") != null) {
            return;
        }
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
        if (generate == null) {
            return;
        }
        String documented;
        if (empty) {
            buildExecutor.addStep(EMPTY, new Empty());
            documented = EMPTY;
        } else {
            BuildExecutorModule chain = generate.apply(generateModule);
            if (chain == null) {
                return;
            }
            buildExecutor.addModule(GENERATE, chain, inherited.sequencedKeySet());
            documented = GENERATE
                    + "/"
                    + InferredDocumentationChainModule.DOCUMENT
                    + "/"
                    + InferredDocumentationChainModule.AGGREGATE;
        }
        if (archiver != null) {
            buildExecutor.addStep(ARCHIVE,
                    archiver,
                    Stream.concat(Stream.of(documented), inherited.sequencedKeySet().stream()));
        }
    }

    private static class Empty implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Files.writeString(Files.createDirectories(context.next().resolve(Javadoc.JAVADOC))
                    .resolve("INTENTIONALLY_EMPTY"), "This module publishes no API documentation.\n");
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

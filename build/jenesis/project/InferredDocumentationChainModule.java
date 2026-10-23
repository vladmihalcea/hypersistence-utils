package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Language;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Javadoc;
import build.jenesis.step.ProcessHandler;

public class InferredDocumentationChainModule implements BuildExecutorModule {

    public static final String JAVADOC = "javadoc";
    public static final String DOCUMENT = "document", AGGREGATE = "aggregate";
    private static final String SCAN = "scan",
            SCAN_FILE = "scan.properties",
            EXTENSIONS = "extensions",
            LANGUAGES = "languages",
            DOKKA = "dokka.",
            INCLUDE_JAVA = "include-java";
    private static final Map<String, String> EXTENSIONS_OF = Map.of(InferredCompilerChainModule.KOTLIN, ".kt",
            InferredCompilerChainModule.SCALA, ".scala",
            InferredCompilerChainModule.GROOVY, ".groovy");

    private final SequencedSet<Path> configuration;
    private final Javadoc javadocStep;
    private final Function<Javadoc, BuildStep> javadoc;
    private final SequencedMap<String, BuildExecutorModule> custom;
    private final SequencedMap<String, PluginModule<?>> languages;
    private final ExternalModule kotlinModule;
    private final boolean kotlin;
    private final ExternalModule scalaModule;
    private final boolean scala;
    private final ExternalModule groovyModule;
    private final boolean groovy;

    public InferredDocumentationChainModule(SequencedSet<Path> configuration,
                                            Map<String, Repository> repositories,
                                            Map<String, Resolver> resolvers) {
        this(configuration,
                new Javadoc(ProcessHandler.Factory.of()),
                step -> step,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                ExternalModule.official(Environment.NONE, InferredCompilerChainModule.KOTLIN, null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, InferredCompilerChainModule.SCALA, null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, InferredCompilerChainModule.GROOVY, null, repositories, resolvers),
                true);
    }

    public static InferredDocumentationChainModule ofEnvironment(Environment environment,
                                                                 SequencedSet<Path> configuration,
                                                                 Map<String, Repository> repositories,
                                                                 Map<String, Resolver> resolvers) {
        return new InferredDocumentationChainModule(configuration,
                Javadoc.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment)),
                step -> step,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                ExternalModule.official(environment, InferredCompilerChainModule.KOTLIN, null, repositories, resolvers),
                environment.flag("language.kotlin", true),
                ExternalModule.official(environment, InferredCompilerChainModule.SCALA, null, repositories, resolvers),
                environment.flag("language.scala", true),
                ExternalModule.official(environment, InferredCompilerChainModule.GROOVY, null, repositories, resolvers),
                environment.flag("language.groovy", true));
    }

    private InferredDocumentationChainModule(SequencedSet<Path> configuration,
                                             Javadoc javadocStep,
                                             Function<Javadoc, BuildStep> javadoc,
                                             SequencedMap<String, BuildExecutorModule> custom,
                                             SequencedMap<String, PluginModule<?>> languages,
                                             ExternalModule kotlinModule,
                                             boolean kotlin,
                                             ExternalModule scalaModule,
                                             boolean scala,
                                             ExternalModule groovyModule,
                                             boolean groovy) {
        this.configuration = configuration;
        this.javadocStep = javadocStep;
        this.javadoc = javadoc;
        this.custom = custom;
        this.languages = languages;
        this.kotlinModule = kotlinModule;
        this.kotlin = kotlin;
        this.scalaModule = scalaModule;
        this.scala = scala;
        this.groovyModule = groovyModule;
        this.groovy = groovy;
    }

    public InferredDocumentationChainModule javadoc(Function<Javadoc, BuildStep> javadoc) {
        return new InferredDocumentationChainModule(configuration,
                javadocStep,
                javadoc,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredDocumentationChainModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredDocumentationChainModule(configuration,
                javadocStep,
                javadoc,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredDocumentationChainModule languages(SequencedMap<String, ? extends BuildExecutorModule> languages) {
        return new InferredDocumentationChainModule(configuration,
                javadocStep,
                javadoc,
                custom,
                PluginModule.plugins("language", "LanguageFactory", languages),
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredDocumentationChainModule kotlin(boolean kotlin) {
        return new InferredDocumentationChainModule(configuration,
                javadocStep,
                javadoc,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredDocumentationChainModule scala(boolean scala) {
        return new InferredDocumentationChainModule(configuration,
                javadocStep,
                javadoc,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredDocumentationChainModule groovy(boolean groovy) {
        return new InferredDocumentationChainModule(configuration,
                javadocStep,
                javadoc,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredDocumentationChainModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredDocumentationChainModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        buildExecutor.addStep(SCAN, new Scan(), inherited.sequencedKeySet());
        SequencedSet<String> documentInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
        documentInputs.add(SCAN);
        SequencedMap<String, ExternalModule> official = new LinkedHashMap<>();
        Path dokka = BuildStep.locate(configuration, "dokka.properties");
        if (kotlin) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            if (dokka != null) {
                SequencedProperties.ofFiles(dokka).forEachProperty((key, value) -> values.put(DOKKA + key, value));
            }
            official.put(InferredCompilerChainModule.KOTLIN, kotlinModule.properties(values));
        }
        if (scala) {
            official.put(InferredCompilerChainModule.SCALA, scalaModule);
        }
        if (groovy) {
            official.put(InferredCompilerChainModule.GROOVY, groovyModule);
        }
        if (!official.isEmpty() || !languages.isEmpty()) {
            SequencedSet<String> languageInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
            languageInputs.add(SCAN);
            buildExecutor.addModule(LANGUAGES, (nested, nestedInherited) -> {
                Path scanFolder = nestedInherited.get(PREVIOUS + SCAN);
                if (scanFolder == null) {
                    throw new IllegalStateException("The languages of the documentation chain are missing their"
                            + " upstream scan input");
                }
                List<String> found = Objects.requireNonNullElse(
                        SequencedProperties.ofFiles(scanFolder.resolve(SCAN_FILE)).entries(EXTENSIONS),
                        List.of());
                SequencedMap<String, BuildExecutorModule> preparations = new LinkedHashMap<>();
                official.forEach((name, module) -> {
                    if (found.contains(EXTENSIONS_OF.get(name))) {
                        preparations.put(name, module.preparation());
                    }
                });
                languages.forEach((name, plugin) -> preparations.put(name, plugin.preparation()));
                PluginModule.addModules(nested, preparations, nestedInherited.sequencedKeySet());
            }, languageInputs);
            documentInputs.add(LANGUAGES);
        }
        buildExecutor.addModule(DOCUMENT, new Document(javadocStep, javadoc, languages, official), documentInputs);
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(SCAN) ? Optional.empty() : PluginModule.exposed(Set.of(LANGUAGES), path);
    }

    private static class Scan implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            boolean[] java = new boolean[1];
            SequencedSet<String> extensions = new TreeSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path sources = argument.folder().resolve(BuildStep.SOURCES);
                if (!Files.exists(sources)) {
                    continue;
                }
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        String name = file.getFileName().toString();
                        int dot = name.lastIndexOf('.');
                        if (dot > 0) {
                            extensions.add(name.substring(dot));
                        }
                        java[0] |= name.endsWith(".java");
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            SequencedProperties properties = new SequencedProperties();
            properties.setProperty(JAVADOC, Boolean.toString(java[0]));
            properties.setProperty(EXTENSIONS, String.join(",", extensions));
            properties.store(context.next().resolve(SCAN_FILE));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Document(Javadoc javadocStep,
                            Function<Javadoc, BuildStep> javadoc,
                            SequencedMap<String, PluginModule<?>> languages,
                            SequencedMap<String, ExternalModule> official) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            Path scanFolder = inherited.get(PREVIOUS + SCAN);
            if (scanFolder == null) {
                throw new IllegalStateException("Document sub-module is missing its upstream scan input");
            }
            SequencedProperties scan = SequencedProperties.ofFiles(scanFolder.resolve(SCAN_FILE));
            List<String> found = scan.entries(EXTENSIONS) == null ? List.of() : scan.entries(EXTENSIONS);
            SequencedMap<String, BuildExecutorModule> documented = new LinkedHashMap<>();
            SequencedMap<String, PluginModule<?>> loaded = new LinkedHashMap<>();
            for (Map.Entry<String, ExternalModule> language : official.entrySet()) {
                if (found.contains(EXTENSIONS_OF.get(language.getKey()))) {
                    ExternalModule module = language.getKey().equals(InferredCompilerChainModule.GROOVY)
                            ? language.getValue().properties(new LinkedHashMap<>(Map.of(INCLUDE_JAVA, Boolean.toString(scan.flag(JAVADOC)))))
                            : language.getValue();
                    loaded.put(language.getKey(), module);
                }
            }
            loaded.putAll(languages);
            for (Map.Entry<String, PluginModule<?>> plugin : loaded.entrySet()) {
                Language language = plugin.getValue().language(PluginModule.prepared(LANGUAGES, plugin.getKey(), inherited));
                if (language.documentation() != null && language.extensions().stream().anyMatch(found::contains)) {
                    documented.put(plugin.getKey(), language.documentation());
                }
            }
            boolean hasJava = scan.flag(JAVADOC) && javadoc != null;

            SequencedSet<String> sourceInputs = PluginModule.unprepared(Set.of(LANGUAGES), inherited).sequencedKeySet().stream()
                    .filter(identity -> !identity.equals(PREVIOUS + SCAN))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            SequencedSet<String> outputs = new LinkedHashSet<>();
            SequencedMap<String, String> within = new LinkedHashMap<>();
            boolean alone = documented.size() == 1 && !hasJava;
            for (Map.Entry<String, BuildExecutorModule> language : documented.entrySet()) {
                String name = BuildExecutorModule.encodePath(language.getKey());
                buildExecutor.addModule(name, language.getValue(), sourceInputs);
                outputs.add(name);
                if (!alone) {
                    within.put(name, language.getKey());
                }
            }
            if (hasJava) {
                BuildStep step = javadoc.apply(documented.isEmpty() ? javadocStep : javadocStep.classpath(true));
                if (step != null) {
                    buildExecutor.addStep(JAVADOC, step, sourceInputs);
                    outputs.add(JAVADOC);
                }
            }
            buildExecutor.addStep(AGGREGATE, new Aggregate(new TreeMap<>(within)), outputs);
        }
    }

    private record Aggregate(SequencedMap<String, String> within) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path combined = Files.createDirectories(context.next().resolve(Javadoc.JAVADOC));
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (argument.getValue().removed()) {
                    continue;
                }
                Path source = argument.getValue().folder().resolve(Javadoc.JAVADOC);
                if (!Files.exists(source)) {
                    continue;
                }
                Path target = within.entrySet().stream()
                        .filter(entry -> argument.getKey().equals(entry.getKey())
                                || argument.getKey().startsWith(entry.getKey() + "/"))
                        .map(entry -> combined.resolve(entry.getValue()))
                        .findFirst()
                        .orElse(combined);
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(target.resolve(source.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        BuildStep.linkOrCopy(target.resolve(source.relativize(file)), file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            if (!Files.exists(combined.resolve("index.html"))) {
                SequencedSet<String> rendered = new TreeSet<>();
                try (Stream<Path> entries = Files.list(combined)) {
                    for (Path entry : entries.toList()) {
                        if (Files.isDirectory(entry) && Files.exists(entry.resolve("index.html"))) {
                            rendered.add(entry.getFileName().toString());
                        }
                    }
                }
                StringBuilder body = new StringBuilder();
                body.append("<!DOCTYPE html>\n<html lang=\"en\"><head><meta charset=\"utf-8\">")
                        .append("<title>API documentation</title></head><body>\n")
                        .append("<h1>API documentation</h1>\n");
                if (rendered.isEmpty()) {
                    body.append("<p>No rendered API documentation is available for this module.</p>\n");
                } else {
                    body.append("<p>API documentation is available per language:</p>\n<ul>\n");
                    for (String name : rendered) {
                        body.append("<li><a href=\"").append(name).append("/index.html\">")
                                .append(name).append("</a></li>\n");
                    }
                    body.append("</ul>\n");
                }
                body.append("</body></html>\n");
                Files.writeString(combined.resolve("index.html"), body.toString());
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

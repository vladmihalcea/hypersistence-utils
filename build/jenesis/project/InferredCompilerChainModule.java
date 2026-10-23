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
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Javac;
import build.jenesis.step.ProcessHandler;

public class InferredCompilerChainModule implements BuildExecutorModule {

    public static final String JAVAC = "javac", RESOURCE = "resource";
    public static final String COMPILE = "compile", RESOLVED = "resolved";
    private static final String SCAN = "scan",
            SCAN_FILE = "scan.properties",
            EXTENSIONS = "extensions",
            LANGUAGES = "languages";
    public static final String KOTLIN = "kotlin", SCALA = "scala", GROOVY = "groovy";
    private static final Map<String, String> EXTENSIONS_OF = Map.of(KOTLIN, ".kt", SCALA, ".scala", GROOVY, ".groovy");

    private final PathPlacement pathPlacement;
    private final boolean includeResources;
    private final Javac javacStep;
    private final Function<Javac, BuildStep> javac;
    private final SequencedMap<String, BuildExecutorModule> custom;
    private final SequencedMap<String, PluginModule<?>> languages;
    private final ExternalModule kotlinModule;
    private final boolean kotlin;
    private final ExternalModule scalaModule;
    private final boolean scala;
    private final ExternalModule groovyModule;
    private final boolean groovy;

    public InferredCompilerChainModule(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(PathPlacement.INFERRED,
                true,
                new Javac(ProcessHandler.Factory.of()),
                step -> step,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                ExternalModule.official(Environment.NONE, KOTLIN, null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, SCALA, null, repositories, resolvers),
                true,
                ExternalModule.official(Environment.NONE, GROOVY, null, repositories, resolvers),
                true);
    }

    public static InferredCompilerChainModule ofEnvironment(Environment environment,
                                                            Map<String, Repository> repositories,
                                                            Map<String, Resolver> resolvers) {
        return new InferredCompilerChainModule(PathPlacement.INFERRED,
                true,
                Javac.ofEnvironment(environment, ProcessHandler.Factory.ofEnvironment(environment)),
                step -> step,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                ExternalModule.official(environment, KOTLIN, null, repositories, resolvers),
                environment.flag("language.kotlin", true),
                ExternalModule.official(environment, SCALA, null, repositories, resolvers),
                environment.flag("language.scala", true),
                ExternalModule.official(environment, GROOVY, null, repositories, resolvers),
                environment.flag("language.groovy", true));
    }

    private InferredCompilerChainModule(PathPlacement pathPlacement,
                                        boolean includeResources,
                                        Javac javacStep,
                                        Function<Javac, BuildStep> javac,
                                        SequencedMap<String, BuildExecutorModule> custom,
                                        SequencedMap<String, PluginModule<?>> languages,
                                        ExternalModule kotlinModule,
                                        boolean kotlin,
                                        ExternalModule scalaModule,
                                        boolean scala,
                                        ExternalModule groovyModule,
                                        boolean groovy) {
        this.pathPlacement = pathPlacement;
        this.includeResources = includeResources;
        this.javacStep = javacStep;
        this.javac = javac;
        this.custom = custom;
        this.languages = languages;
        this.kotlinModule = kotlinModule;
        this.kotlin = kotlin;
        this.scalaModule = scalaModule;
        this.scala = scala;
        this.groovyModule = groovyModule;
        this.groovy = groovy;
    }

    public InferredCompilerChainModule pathPlacement(PathPlacement pathPlacement) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule includeResources(boolean includeResources) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule javac(Function<Javac, BuildStep> javac) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule languages(SequencedMap<String, ? extends BuildExecutorModule> languages) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                PluginModule.plugins("language", "LanguageFactory", languages),
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule kotlin(boolean kotlin) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule scala(boolean scala) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule groovy(boolean groovy) {
        return new InferredCompilerChainModule(pathPlacement,
                includeResources,
                javacStep,
                javac,
                custom,
                languages,
                kotlinModule,
                kotlin,
                scalaModule,
                scala,
                groovyModule,
                groovy);
    }

    public InferredCompilerChainModule custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredCompilerChainModule custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(SCAN, new Scan(), inherited.sequencedKeySet());
        SequencedSet<String> compileInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
        compileInputs.add(SCAN);
        SequencedMap<String, PluginModule<?>> prepared = new LinkedHashMap<>();
        SequencedMap<String, String> official = new LinkedHashMap<>();
        SequencedMap<String, ExternalModule> configured = new LinkedHashMap<>();
        if (kotlin) {
            configured.put(KOTLIN, kotlinModule);
        }
        if (scala) {
            configured.put(SCALA, scalaModule);
        }
        if (groovy) {
            configured.put(GROOVY, groovyModule);
        }
        configured.forEach((name, module) -> {
            prepared.put(name, module);
            official.put(name, EXTENSIONS_OF.get(name));
        });
        prepared.putAll(languages);
        if (!prepared.isEmpty()) {
            SequencedSet<String> languageInputs = new LinkedHashSet<>(inherited.sequencedKeySet());
            languageInputs.add(SCAN);
            buildExecutor.addModule(LANGUAGES, (nested, nestedInherited) -> {
                Path scanFolder = nestedInherited.get(PREVIOUS + SCAN);
                if (scanFolder == null) {
                    throw new IllegalStateException("The languages of the compile chain are missing their upstream"
                            + " scan input");
                }
                List<String> found = Objects.requireNonNullElse(
                        SequencedProperties.ofFiles(scanFolder.resolve(SCAN_FILE)).entries(EXTENSIONS),
                        List.of());
                SequencedMap<String, BuildExecutorModule> preparations = new LinkedHashMap<>();
                prepared.forEach((name, plugin) -> {
                    if (!official.containsKey(name) || found.contains(official.get(name))) {
                        preparations.put(name, plugin.preparation());
                    }
                });
                PluginModule.addModules(nested, preparations, nestedInherited.sequencedKeySet());
            }, languageInputs);
            compileInputs.add(LANGUAGES);
        }
        buildExecutor.addModule(COMPILE,
                new Compile(pathPlacement, includeResources, javacStep, javac, prepared, official),
                compileInputs);
        if (!custom.isEmpty()) {
            buildExecutor.addModule("custom", (nested, nestedInherited) -> custom.forEach((name, module) ->
                    nested.addModule(name, module, nestedInherited.sequencedKeySet())), inherited.sequencedKeySet());
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        if (path.equals(COMPILE + "/" + RESOLVED)) {
            return Optional.of(RESOLVED);
        }
        return path.equals(SCAN) ? Optional.empty() : PluginModule.exposed(Set.of(LANGUAGES), path);
    }

    private static class Scan implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            boolean[] flags = new boolean[2];
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
                        if (name.endsWith(".java")) {
                            flags[0] = true;
                        } else {
                            flags[1] = true;
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            SequencedProperties properties = new SequencedProperties();
            properties.setProperty(JAVAC, Boolean.toString(flags[0]));
            properties.setProperty(RESOURCE, Boolean.toString(flags[1]));
            properties.setProperty(EXTENSIONS, String.join(",", extensions));
            properties.store(context.next().resolve(SCAN_FILE));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Compile(PathPlacement pathPlacement,
                           boolean includeResources,
                           Javac javacStep,
                           Function<Javac, BuildStep> javac,
                           SequencedMap<String, PluginModule<?>> languages,
                           SequencedMap<String, String> official) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            Path scanFolder = inherited.get(PREVIOUS + SCAN);
            if (scanFolder == null) {
                throw new IllegalStateException("Compile sub-module is missing its upstream scan input");
            }
            SequencedProperties scan = SequencedProperties.ofFiles(scanFolder.resolve(SCAN_FILE));
            boolean hasResource = scan.flag(RESOURCE);
            List<String> found = scan.entries(EXTENSIONS) == null ? List.of() : scan.entries(EXTENSIONS);
            SequencedMap<String, Language> before = new LinkedHashMap<>(), after = new LinkedHashMap<>();
            SequencedSet<String> compiled = new TreeSet<>();
            for (Map.Entry<String, PluginModule<?>> plugin : languages.entrySet()) {
                if (official.containsKey(plugin.getKey()) && !found.contains(official.get(plugin.getKey()))) {
                    continue;
                }
                Language language = plugin.getValue().language(PluginModule.prepared(LANGUAGES, plugin.getKey(), inherited));
                compiled.addAll(language.extensions());
                if (language.extensions().stream().anyMatch(found::contains)) {
                    (language.order() == Language.Order.BEFORE_JAVAC ? before : after)
                            .put(BuildExecutorModule.encodePath(plugin.getKey()), language);
                }
            }
            for (Map.Entry<String, String> language : EXTENSIONS_OF.entrySet()) {
                if (found.contains(language.getValue()) && !compiled.contains(language.getValue())) {
                    throw new IllegalStateException("The module holds " + language.getValue() + " sources, which no"
                            + " language compiles - the build compiles them with its plugin " + language.getKey()
                            + " unless jenesis.language." + language.getKey() + "=false switches it off");
                }
            }
            boolean hasJava = scan.flag(JAVAC) && javac != null;
            boolean hasLanguage = !before.isEmpty() || !after.isEmpty();

            SequencedSet<String> sourceInputs = PluginModule.unprepared(Set.of(LANGUAGES), inherited).sequencedKeySet().stream()
                    .filter(identity -> !identity.equals(PREVIOUS + SCAN))
                    .collect(Collectors.toCollection(LinkedHashSet::new));

            SequencedSet<String> dependencies = new LinkedHashSet<>(sourceInputs);
            for (Map.Entry<String, Language> language : before.entrySet()) {
                buildExecutor.addModule(language.getKey(), language.getValue().compiler(), dependencies);
                SequencedSet<String> updated = new LinkedHashSet<>(dependencies);
                updated.add(language.getKey());
                dependencies = updated;
            }
            if (hasJava) {
                BuildStep compiler = javac.apply(javacStep
                        .includeResources(includeResources && !hasLanguage)
                        .pathPlacement(pathPlacement));
                if (compiler != null) {
                    SequencedSet<String> javacInputs = new LinkedHashSet<>(dependencies);
                    buildExecutor.addStep(JAVAC, compiler, javacInputs);
                    SequencedSet<String> updated = new LinkedHashSet<>(javacInputs);
                    updated.add(JAVAC);
                    dependencies = updated;
                }
            }
            for (Map.Entry<String, Language> language : after.entrySet()) {
                buildExecutor.addModule(language.getKey(), language.getValue().compiler(), dependencies);
                SequencedSet<String> updated = new LinkedHashSet<>(dependencies);
                updated.add(language.getKey());
                dependencies = updated;
            }
            if (includeResources && hasResource && (!hasJava || hasLanguage)) {
                buildExecutor.addStep(RESOURCE, new Resources(new TreeSet<>(compiled)), sourceInputs);
            }
            if (hasLanguage) {
                SequencedSet<String> compilers = new LinkedHashSet<>(before.sequencedKeySet());
                compilers.addAll(after.sequencedKeySet());
                inherited.sequencedKeySet().stream()
                        .filter(identity -> languages.keySet().stream()
                                .map(name -> PREVIOUS + LANGUAGES + "/" + name + "/" + PluginModule.DEPENDENCIES)
                                .anyMatch(closure -> identity.equals(closure) || identity.startsWith(closure + "/")))
                        .forEach(compilers::add);
                buildExecutor.addStep(RESOLVED, new Resolved(), compilers);
            }
        }
    }

    private record Resolved() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties resolved = new SequencedProperties();
            int index = 0;
            for (BuildStepArgument argument : arguments.values()) {
                Path file = argument.folder().resolve(BuildStep.DEPENDENCIES);
                if (argument.removed() || !Files.isRegularFile(file)) {
                    continue;
                }
                String folder = Integer.toString(index++);
                SequencedProperties dependencies = SequencedProperties.ofFiles(file);
                for (String key : dependencies.stringPropertyNames()) {
                    String value = dependencies.getProperty(key);
                    int space = value.indexOf(' ');
                    String location = space < 0 ? value : value.substring(0, space);
                    Path jar = argument.folder().resolve(location).normalize();
                    if (key.chars().filter(character -> character == '/').count() < 2 || !Files.isRegularFile(jar)) {
                        continue;
                    }
                    String linked = folder + "/" + argument.folder().relativize(jar).toString().replace('\\', '/');
                    Path target = context.next().resolve(linked);
                    Files.createDirectories(target.getParent());
                    BuildStep.linkOrCopy(target, jar);
                    resolved.setProperty(key, space < 0 ? linked : linked + value.substring(space));
                }
            }
            resolved.store(context.next().resolve(BuildStep.DEPENDENCIES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Resources(SequencedSet<String> compiled) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path target = Files.createDirectory(context.next().resolve(BuildStep.CLASSES));
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
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path relative = sources.relativize(dir);
                        if (BuildStep.underBuildJenesis(relative)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        Files.createDirectories(target.resolve(relative));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String name = file.getFileName().toString();
                        Path relative = sources.relativize(file);
                        if (!name.endsWith(".java")
                                && compiled.stream().noneMatch(name::endsWith)
                                && !BuildStep.underMetaInfVersions(relative)) {
                            BuildStep.linkOrCopy(target.resolve(relative), file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

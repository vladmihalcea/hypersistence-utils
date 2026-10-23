package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Project;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.module.JenesisRepository;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.step.Bind;
import build.jenesis.step.Inventory;
import build.jenesis.step.ProjectFiles;

public record ProjectPlugins(Path pins,
                             Path arguments,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> preprocessors,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> transforms,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> inspections,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> supplements,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> audits,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> releasers,
                             SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> goals,
                             SequencedMap<String, BuildExecutorModule> resolutions) {

    public static final String PREPROCESS = "preprocess",
            POSTPROCESS = "postprocess",
            CUSTOM = "custom",
            PLUGIN = "plugin",
            TRANSFORM = "transform",
            INSPECT = "inspect",
            PINS = "jenesis.plugins.pin.properties",
            ADDITIONS = "additions",
            STAGED = "staged",
            PROJECT = "project",
            SEALED = "sealed",
            UNCHANGED = "unchanged",
            RESOLVED = "resolved",
            JRELEASER = "jreleaser",
            ASCIIDOCTOR = "asciidoctor",
            GRADLE_PLUGIN = "gradleplugin",
            DOCS = "docs",
            DOCUMENTS = "documents",
            ROOT = "root.properties",
            ROOT_KEY = "root";

    public ProjectPlugins() {
        this(null,
                null,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap());
    }

    public static Map<String, Repository> repositories(Map<String, Repository> repositories, Environment environment) {
        Map<String, Repository> merged = new LinkedHashMap<>();
        merged.put("module", JenesisRepository.ofEnvironment(environment, JenesisRepository.Scope.MODULE));
        merged.put("maven", MavenDefaultRepository.ofEnvironment(environment));
        merged.putAll(repositories);
        return Collections.unmodifiableMap(merged);
    }

    public static Map<String, Resolver> resolvers(Map<String, Resolver> resolvers, Environment environment) {
        Map<String, Resolver> merged = new LinkedHashMap<>();
        merged.put("module", ModularJarResolver.ofEnvironment(environment, true));
        merged.put("maven", MavenPomResolver.ofEnvironment(environment));
        merged.putAll(resolvers);
        return Collections.unmodifiableMap(merged);
    }

    public static String declared(String name) {
        return PLUGIN + "." + name;
    }

    public static Path jreleaser(SequencedSet<Path> configurations) {
        return Stream.of("jreleaser.yml", "jreleaser.yaml", "jreleaser.toml", "jreleaser.json")
                .map(name -> BuildStep.locate(configurations, name))
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }

    public ProjectPlugins tools(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        UnaryOperator<SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>>> tooled = plugins -> {
            SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> handed = new LinkedHashMap<>();
            plugins.forEach((name, plugin) -> handed.put(name, values -> {
                BuildExecutorModule module = plugin.apply(values);
                return module instanceof PluginModule<?> tool
                        ? tool.repositories(repositories).resolvers(resolvers)
                        : module;
            }));
            return handed;
        };
        return new ProjectPlugins(pins,
                arguments,
                tooled.apply(preprocessors),
                tooled.apply(transforms),
                tooled.apply(inspections),
                tooled.apply(supplements),
                tooled.apply(audits),
                tooled.apply(releasers),
                tooled.apply(goals),
                resolutions);
    }

    public ProjectPlugins pins(Path pins) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins arguments(Path arguments) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins preprocessors(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> preprocessors) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins transforms(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> transforms) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins inspections(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> inspections) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins supplements(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> supplements) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins audits(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> audits) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins releasers(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> releasers) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins goals(SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> goals) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins resolutions(SequencedMap<String, BuildExecutorModule> resolutions) {
        return new ProjectPlugins(pins,
                arguments,
                preprocessors,
                transforms,
                inspections,
                supplements,
                audits,
                releasers,
                goals,
                resolutions);
    }

    public ProjectPlugins preprocess(String name, BuildExecutorModule module) {
        if (preprocessors.containsKey(name)) {
            throw new IllegalArgumentException("A preprocessor named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(preprocessors);
        added.put(name, _ -> module);
        return preprocessors(added);
    }

    public ProjectPlugins preprocess(String name, BuildStep step) {
        return preprocess(name, step.asModule(name));
    }

    public ProjectPlugins transform(String name, BuildExecutorModule module) {
        if (transforms.containsKey(name)) {
            throw new IllegalArgumentException("A transform named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(transforms);
        added.put(name, _ -> module);
        return transforms(added);
    }

    public ProjectPlugins transform(String name, BuildStep step) {
        return transform(name, step.asModule(name));
    }

    public ProjectPlugins inspect(String name, BuildExecutorModule module) {
        if (inspections.containsKey(name)) {
            throw new IllegalArgumentException("An inspection named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(inspections);
        added.put(name, _ -> module);
        return inspections(added);
    }

    public ProjectPlugins inspect(String name, BuildStep step) {
        return inspect(name, step.asModule(name));
    }

    public ProjectPlugins supplement(String name, BuildExecutorModule module) {
        if (supplements.containsKey(name)) {
            throw new IllegalArgumentException("A supplement named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(supplements);
        added.put(name, _ -> module);
        return supplements(added);
    }

    public ProjectPlugins supplement(String name, BuildStep step) {
        return supplement(name, step.asModule(name));
    }

    public ProjectPlugins audit(String name, BuildExecutorModule module) {
        if (audits.containsKey(name)) {
            throw new IllegalArgumentException("An audit named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(audits);
        added.put(name, _ -> module);
        return audits(added);
    }

    public ProjectPlugins audit(String name, BuildStep step) {
        return audit(name, step.asModule(name));
    }

    public ProjectPlugins release(String name, BuildExecutorModule module) {
        if (releasers.containsKey(name)) {
            throw new IllegalArgumentException("A releaser named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(releasers);
        added.put(name, _ -> module);
        return releasers(added);
    }

    public ProjectPlugins release(String name, BuildStep step) {
        return release(name, step.asModule(name));
    }

    public ProjectPlugins goal(String name, BuildExecutorModule module) {
        if (goals.containsKey(name)) {
            throw new IllegalArgumentException("A goal named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> added = new LinkedHashMap<>(goals);
        added.put(name, _ -> module);
        return goals(added);
    }

    public ProjectPlugins goal(String name, BuildStep step) {
        return goal(name, step.asModule(name));
    }

    public BuildExecutorModule resolution() {
        return (buildExecutor, _) -> buildExecutor.addModule(CUSTOM, (custom, given) -> {
            for (Map.Entry<String, BuildExecutorModule> resolution : resolutions.entrySet()) {
                custom.addModule(resolution.getKey(), resolution.getValue(), given.sequencedKeySet());
            }
        }, pinned(buildExecutor));
    }

    public BuildExecutorModule preprocess(SequencedSet<Path> profiles) {
        return new BuildExecutorModule() {
            @Override
            public Optional<String> resolve(String path) {
                String[] segments = path.split("/");
                return segments.length > 2
                        && segments[0].equals(CUSTOM)
                        && segments[2].equals(PluginModule.DEPENDENCIES) ? Optional.of(path) : Optional.empty();
            }

            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
                if (preprocessors.isEmpty()) {
                    return;
                }
                SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
                SequencedSet<String> inputs = pinned(buildExecutor);
                inputs.addAll(inherited.sequencedKeySet());
                buildExecutor.addModule(CUSTOM, (custom, given) -> {
                    for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : preprocessors.entrySet()) {
                        custom.addModule(entry.getKey(),
                                entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                given.sequencedKeySet());
                    }
                }, inputs);
            }
        };
    }

    public BuildExecutorModule preprocessed(BuildExecutorModule build) {
        return new BuildExecutorModule() {
            @Override
            public Optional<String> resolve(String path) {
                return build.resolve(path);
            }

            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
                SequencedMap<String, Path> built = new LinkedHashMap<>(inherited);
                built.keySet().removeIf(key -> key.startsWith(BuildExecutorModule.PREVIOUS + PREPROCESS + "/"));
                build.accept(buildExecutor, built);
            }
        };
    }

    public BuildExecutorModule stage(SequencedSet<Path> profiles, SequencedMap<String, BuildStep> trees) {
        return (buildExecutor, inherited) -> {
            if (supplements.isEmpty() && audits.isEmpty()) {
                for (Map.Entry<String, BuildStep> tree : trees.entrySet()) {
                    buildExecutor.addStep(tree.getKey(), tree.getValue(), inherited.sequencedKeySet());
                }
                return;
            }
            SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
            SequencedSet<String> inputs = pinned(buildExecutor);
            buildExecutor.addModule(STAGED, (staged, given) -> {
                for (Map.Entry<String, BuildStep> tree : trees.entrySet()) {
                    staged.addStep(tree.getKey(), tree.getValue(), given.sequencedKeySet());
                }
            }, inherited.sequencedKeySet());
            SequencedSet<String> handed = new LinkedHashSet<>(inputs);
            handed.add(STAGED);
            if (!supplements.isEmpty()) {
                buildExecutor.addModule(TRANSFORM, (transform, given) -> {
                    SequencedSet<String> previous = new LinkedHashSet<>(given.sequencedKeySet());
                    for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : supplements.entrySet()) {
                        transform.addModule(entry.getKey(),
                                entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                previous);
                        previous.add(entry.getKey());
                    }
                }, handed);
            }
            for (String tree : trees.keySet()) {
                buildExecutor.addStep(tree,
                        new Merged(tree, STAGED + "/" + tree),
                        supplements.isEmpty() ? Stream.of(STAGED + "/" + tree) : Stream.of(STAGED + "/" + tree, TRANSFORM));
            }
            if (!audits.isEmpty()) {
                SequencedSet<String> inspected = new LinkedHashSet<>(trees.sequencedKeySet());
                buildExecutor.addStep(SEALED, new Sealed(), inspected);
                SequencedSet<String> available = new LinkedHashSet<>(inputs);
                available.addAll(inspected);
                available.add(SEALED);
                buildExecutor.addModule(INSPECT, (inspect, given) -> {
                    for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : audits.entrySet()) {
                        inspect.addModule(entry.getKey(),
                                entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                given.sequencedKeySet());
                    }
                }, available);
                SequencedSet<String> compared = new LinkedHashSet<>(inspected);
                compared.add(SEALED);
                compared.add(INSPECT);
                buildExecutor.addStep(UNCHANGED, new Unchanged(), compared);
            }
        };
    }

    public BuildExecutorModule release(SequencedSet<Path> configurations, Path root) {
        return (buildExecutor, inherited) -> {
            SequencedMap<String, BuildExecutorModule> plugins = new LinkedHashMap<>();
            for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> releaser : releasers.entrySet()) {
                if (!resolutions.containsKey(releaser.getKey())) {
                    plugins.put(releaser.getKey(), releaser.getValue().apply(Collections.emptyNavigableMap()));
                    continue;
                }
                boolean official = releaser.getKey().equals(JRELEASER)
                        && (BuildStep.locate(configurations, JRELEASER + ".properties") != null
                        || jreleaser(configurations) != null);
                Path file = BuildStep.locate(configurations, official
                        ? JRELEASER + ".properties"
                        : PLUGIN + "-" + releaser.getKey().substring(releaser.getKey().indexOf('.') + 1) + ".properties");
                if (file != null || official) {
                    SequencedMap<String, String> values = new LinkedHashMap<>();
                    if (file != null) {
                        SequencedProperties.ofFiles(file).forEachProperty(values::put);
                    }
                    plugins.put(releaser.getKey(), releaser.getValue().apply(values));
                }
            }
            if (plugins.isEmpty()) {
                return;
            }
            buildExecutor.addStep(PROJECT, new Resolved(root.toAbsolutePath().normalize().toString()), inherited.sequencedKeySet());
            SequencedSet<String> inputs = pinned(buildExecutor);
            inputs.add(PROJECT);
            inherited.sequencedKeySet().stream()
                    .filter(key -> !key.startsWith(BuildExecutorModule.PREVIOUS + Project.METADATA + "/"))
                    .forEach(inputs::add);
            buildExecutor.addModule(CUSTOM, (custom, given) -> {
                for (Map.Entry<String, BuildExecutorModule> plugin : plugins.entrySet()) {
                    custom.addModule(plugin.getKey(), plugin.getValue(), given.sequencedKeySet());
                }
            }, inputs);
        };
    }

    public BuildExecutorModule goal(SequencedSet<Path> profiles) {
        return (buildExecutor, inherited) -> {
            if (goals.isEmpty()) {
                return;
            }
            SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
            SequencedSet<String> inputs = pinned(buildExecutor);
            inputs.addAll(inherited.sequencedKeySet());
            for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : goals.entrySet()) {
                buildExecutor.addModule(entry.getKey().substring(entry.getKey().indexOf('.') + 1),
                        entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                        inputs);
            }
        };
    }

    public BuildExecutorModule postprocess(SequencedSet<Path> profiles) {
        return new BuildExecutorModule() {
            @Override
            public Optional<String> resolve(String path) {
                String[] segments = path.split("/");
                return path.startsWith(ADDITIONS + "/")
                        || path.equals(PROJECT)
                        || segments.length > 2
                        && (segments[0].equals(TRANSFORM) || segments[0].equals(INSPECT))
                        && segments[2].equals(PluginModule.DEPENDENCIES) ? Optional.of(path) : Optional.empty();
            }

            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
                if (transforms.isEmpty() && inspections.isEmpty()) {
                    return;
                }
                SequencedMap<String, SequencedMap<String, String>> values = arguments(profiles);
                SequencedSet<String> inputs = pinned(buildExecutor);
                inputs.addAll(inherited.sequencedKeySet());
                SequencedSet<String> inspected = new LinkedHashSet<>(inherited.sequencedKeySet());
                if (!transforms.isEmpty()) {
                    SequencedSet<String> prefixes = new LinkedHashSet<>();
                    for (Path folder : inherited.values()) {
                        Path file = folder.resolve(Inventory.INVENTORY);
                        if (Files.isRegularFile(file)) {
                            prefixes.addAll(Inventory.prefixes(SequencedProperties.ofFiles(file)));
                        }
                    }
                    buildExecutor.addModule(TRANSFORM, (transform, given) -> {
                        SequencedSet<String> previous = new LinkedHashSet<>(given.sequencedKeySet());
                        for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : transforms.entrySet()) {
                            transform.addModule(entry.getKey(),
                                    entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                    previous);
                            previous.add(entry.getKey());
                        }
                    }, inputs);
                    buildExecutor.addModule(ADDITIONS, (additions, produced) -> {
                        for (String prefix : prefixes) {
                            additions.addStep(BuildExecutorModule.encodePath(prefix),
                                    new Additions(prefix, prefixes),
                                    produced.sequencedKeySet());
                        }
                    }, TRANSFORM);
                    buildExecutor.addStep(PROJECT, new ProjectFiles().folder(ProjectFiles.PROJECT), TRANSFORM);
                    inspected.add(ADDITIONS);
                    inspected.add(PROJECT);
                }
                if (!inspections.isEmpty()) {
                    buildExecutor.addStep(SEALED, new Sealed(), inspected);
                    SequencedSet<String> available = new LinkedHashSet<>(inputs);
                    available.addAll(inspected);
                    available.add(SEALED);
                    buildExecutor.addModule(INSPECT, (inspect, given) -> {
                        for (Map.Entry<String, Function<SequencedMap<String, String>, BuildExecutorModule>> entry : inspections.entrySet()) {
                            inspect.addModule(entry.getKey(),
                                    entry.getValue().apply(values.getOrDefault(entry.getKey(), Collections.emptyNavigableMap())),
                                    given.sequencedKeySet());
                        }
                    }, available);
                    SequencedSet<String> compared = new LinkedHashSet<>(inspected);
                    compared.add(SEALED);
                    compared.add(INSPECT);
                    buildExecutor.addStep(UNCHANGED, new Unchanged(), compared);
                }
            }
        };
    }

    private SequencedMap<String, SequencedMap<String, String>> arguments(SequencedSet<Path> profiles) throws IOException {
        SequencedMap<String, SequencedMap<String, String>> values = new TreeMap<>();
        if (arguments == null) {
            return values;
        }
        List<Path> files = new ArrayList<>();
        for (Path profile : profiles) {
            String name = arguments.getFileName().toString();
            files.add(arguments.resolveSibling(name.substring(0, name.length() - ".properties".length())
                    + "-" + profile + ".properties"));
        }
        files.add(arguments);
        for (Path file : files) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties declared = SequencedProperties.ofFiles(file);
            for (String key : declared.stringPropertyNames()) {
                int dot = key.indexOf('.');
                String name = dot == -1 ? key : key.substring(0, dot);
                String plugin = Stream.of(resolutions, preprocessors, transforms, inspections, supplements, audits, releasers, goals)
                        .anyMatch(plugins -> plugins.containsKey(declared(name))) ? declared(name) : name;
                if (dot <= 0 || dot == key.length() - 1 || Stream.of(resolutions, preprocessors, transforms, inspections, supplements, audits, releasers, goals)
                        .noneMatch(plugins -> plugins.containsKey(plugin))) {
                    throw new IllegalArgumentException("The argument " + key + " in " + file + " names no plugin of "
                            + "the whole project - write <plugin>.<key>=<value> for one of "
                            + Stream.of(resolutions.keySet(), preprocessors.keySet(), transforms.keySet(),
                                            inspections.keySet(), supplements.keySet(), audits.keySet(),
                                            releasers.keySet(), goals.keySet())
                                    .flatMap(Set::stream)
                                    .map(plugged -> plugged.startsWith(PLUGIN + ".") ? plugged.substring(PLUGIN.length() + 1) : plugged)
                                    .collect(Collectors.toCollection(TreeSet::new)));
                }
                if (releasers.containsKey(plugin) && resolutions.containsKey(plugin)) {
                    throw new IllegalArgumentException("The argument " + key + " in " + file + " configures the plugin "
                            + name + " of release, which takes its values from the plugin-" + name + ".properties"
                            + " of a configuration folder, and runs only where one is found - write "
                            + key.substring(dot + 1) + "=<value> there");
                }
                values.computeIfAbsent(plugin, _ -> new TreeMap<>()).putIfAbsent(key.substring(dot + 1), declared.getProperty(key));
            }
        }
        return values;
    }

    private SequencedSet<String> pinned(BuildExecutor buildExecutor) {
        SequencedSet<String> inputs = new LinkedHashSet<>();
        if (pins != null && Files.isRegularFile(pins)) {
            buildExecutor.addSource(PINS, new Bind(Map.of(Path.of(""), Path.of(BuildStep.VERSIONS))), pins);
            inputs.add(PINS);
        }
        return inputs;
    }

    private record Resolved(String root) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties located = new SequencedProperties(), metadata = new SequencedProperties();
            located.setProperty(ROOT_KEY, root);
            String stage = BuildExecutorModule.PREVIOUS + Project.STAGE + "/",
                    resolved = BuildExecutorModule.PREVIOUS + Project.METADATA + "/";
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (argument.getValue().removed()) {
                    continue;
                }
                String key = argument.getKey();
                if (key.startsWith(resolved)) {
                    Path file = argument.getValue().folder().resolve(METADATA);
                    if (Files.isRegularFile(file)) {
                        SequencedProperties.ofFiles(file).forEachProperty(metadata::setProperty);
                    }
                } else if (key.startsWith(stage) && key.indexOf('/', stage.length()) == -1) {
                    located.setProperty("stage." + key.substring(stage.length()),
                            argument.getValue().folder().toAbsolutePath().normalize().toString());
                }
            }
            located.store(context.next().resolve(ROOT));
            metadata.store(context.next().resolve(METADATA));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Additions(String prefix, SequencedSet<String> prefixes) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties additions = new SequencedProperties();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(Inventory.INVENTORY);
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                SequencedProperties inventory = SequencedProperties.ofFiles(file);
                for (String key : inventory.stringPropertyNames()) {
                    String owner = prefixes.contains(key) ? key : Inventory.ownerOf(key, prefixes);
                    if (owner == null) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file
                                + ", which names no module of this build - prefix it with one of " + prefixes);
                    }
                    String entry = key.substring(Math.min(owner.length() + 1, key.length()));
                    if (entry.isEmpty() || entry.contains("/") || Arrays.asList(entry.split("\\.", -1)).contains("")) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file + " - name what it"
                                + " adds as <module>.<key>, a key of dot-separated names, for a file below its own"
                                + " output");
                    }
                    if (!owner.equals(prefix)) {
                        continue;
                    }
                    Path source = BuildStep.resolveContained(argument.folder(), inventory.getProperty(key));
                    if (!Files.exists(source)) {
                        throw new IllegalArgumentException("A transform adds " + key + " in " + file + ", but "
                                + source + " does not exist");
                    }
                    Path target = context.next().resolve(entry.replace('.', '/')).resolve(source.getFileName().toString());
                    if (additions.getProperty(key) != null) {
                        throw new IllegalStateException("More than one transform adds " + key + " - give each"
                                + " addition a name of its own");
                    }
                    Files.createDirectories(target.getParent());
                    if (Files.isDirectory(source)) {
                        try (Stream<Path> files = Files.walk(source)) {
                            for (Path nested : files.filter(Files::isRegularFile).toList()) {
                                Path linked = target.resolve(source.relativize(nested).toString());
                                Files.createDirectories(linked.getParent());
                                BuildStep.linkOrCopy(linked, nested);
                            }
                        }
                    } else {
                        BuildStep.linkOrCopy(target, source);
                    }
                    additions.setProperty(key, context.next().relativize(target).toString().replace(File.separatorChar, '/'));
                }
            }
            if (!additions.isEmpty()) {
                additions.store(context.next().resolve(Inventory.INVENTORY));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Merged(String tree, String staged) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            BuildStepArgument base = arguments.get(staged);
            if (base != null && !base.removed()) {
                try (Stream<Path> files = Files.walk(base.folder())) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Path placed = context.next().resolve(base.folder().relativize(file).toString());
                        Files.createDirectories(placed.getParent());
                        BuildStep.linkOrCopy(placed, file);
                    }
                }
            }
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (argument.getKey().equals(staged) || argument.getValue().removed()) {
                    continue;
                }
                Path folder = argument.getValue().folder().resolve(tree);
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        String relative = folder.relativize(file).toString().replace(File.separatorChar, '/');
                        Path placed = context.next().resolve(relative);
                        if (Files.exists(placed)) {
                            throw new IllegalStateException(argument.getKey() + " adds " + tree + "/" + relative
                                    + ", which is staged already - a transform of stage adds and never replaces");
                        }
                        Files.createDirectories(placed.getParent());
                        BuildStep.linkOrCopy(placed, file);
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Sealed() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties sealed = new SequencedProperties();
            BuildStep.listing(arguments).forEach(sealed::setProperty);
            sealed.store(context.next().resolve("sealed.properties"));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Unchanged() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, BuildStepArgument> inspected = new LinkedHashMap<>(arguments);
            BuildStepArgument seal = inspected.remove(SEALED);
            inspected.keySet().removeIf(key -> key.equals(INSPECT) || key.startsWith(INSPECT + "/"));
            SequencedProperties sealed = SequencedProperties.ofFiles(seal.folder().resolve("sealed.properties"));
            SequencedMap<String, String> expected = new TreeMap<>();
            sealed.forEachProperty(expected::put);
            SequencedMap<String, String> actual = BuildStep.listing(inspected);
            if (!expected.equals(actual)) {
                SequencedSet<String> changed = new TreeSet<>(expected.keySet());
                changed.addAll(actual.keySet());
                changed.removeIf(key -> Objects.equals(expected.get(key), actual.get(key)));
                throw new IllegalStateException("An inspection changed what it inspects: " + changed
                        + " - an inspection writes only into its own output");
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

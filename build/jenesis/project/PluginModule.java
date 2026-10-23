package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.Environment;
import build.jenesis.Language;
import build.jenesis.Observation;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.TestFramework;
import build.jenesis.step.Bind;
import build.jenesis.step.ProcessBuildStep;

abstract class PluginModule<M extends PluginModule<M>> implements BuildExecutorModule {

    static final String DELEGATE = "delegate",
            INPUTS = "inputs",
            DEPENDENCIES = "dependencies",
            CLOSURE = "+closure",
            RULES = "rules";
    public static final String CONFIGURATION = "configuration";
    private static final Pattern INPUT_NAME = Pattern.compile("[A-Za-z0-9._-]+");

    final String buildModuleName;
    final String group;
    final PathPlacement pathPlacement;
    final SequencedMap<String, String> properties;
    final SequencedMap<String, SequencedMap<Path, Path>> inputs;
    final Map<String, Repository> repositories;
    final Map<String, Resolver> resolvers;
    final Environment environment;

    PluginModule(String buildModuleName,
                 String group,
                 PathPlacement pathPlacement,
                 SequencedMap<String, String> properties,
                 SequencedMap<String, SequencedMap<Path, Path>> inputs,
                 Map<String, Repository> repositories,
                 Map<String, Resolver> resolvers,
                 Environment environment) {
        this.buildModuleName = buildModuleName;
        this.group = group;
        this.pathPlacement = pathPlacement;
        this.properties = properties;
        this.inputs = inputs;
        this.repositories = repositories;
        this.resolvers = resolvers;
        this.environment = environment;
    }

    static SequencedMap<String, PluginModule<?>> plugins(String hook,
                                                         String factory,
                                                         SequencedMap<String, ? extends BuildExecutorModule> modules) {
        SequencedMap<String, PluginModule<?>> plugins = new LinkedHashMap<>();
        modules.forEach((name, module) -> {
            if (!(module instanceof PluginModule<?> plugin)) {
                throw new IllegalArgumentException("The plugin " + name + "+" + hook
                        + " is no plugin module - name a"
                        + " module that provides an " + factory + " in jenesis.plugins.properties, or build it with"
                        + " ExternalModule.ofEnvironment or InternalModule.ofEnvironment");
            }
            plugins.put(ProjectPlugins.PLUGIN + "/" + name, plugin);
        });
        return Collections.unmodifiableSequencedMap(plugins);
    }

    static void addModules(BuildExecutor buildExecutor,
                           SequencedMap<String, ? extends BuildExecutorModule> modules,
                           SequencedSet<String> inputs) {
        SequencedMap<String, BuildExecutorModule> plugins = new LinkedHashMap<>();
        modules.forEach((name, module) -> {
            if (name.startsWith(ProjectPlugins.PLUGIN + "/")) {
                plugins.put(name.substring(ProjectPlugins.PLUGIN.length() + 1), module);
            } else {
                buildExecutor.addModule(name, module, inputs);
            }
        });
        if (!plugins.isEmpty()) {
            buildExecutor.addModule(ProjectPlugins.PLUGIN, (nested, given) -> plugins.forEach((name, module) ->
                    nested.addModule(name, module, given.sequencedKeySet())), inputs);
        }
    }

    static SequencedMap<String, Path> prepared(String namespace, String name, SequencedMap<String, Path> inherited) {
        String prefix = namespace + "/" + name + "/";
        SequencedMap<String, Path> prepared = new LinkedHashMap<>();
        inherited.forEach((identity, folder) -> {
            String located = located(identity);
            if (located.startsWith(prefix)) {
                prepared.put(PREVIOUS + located.substring(prefix.length()), folder);
            }
        });
        return prepared;
    }

    static SequencedMap<String, Path> unprepared(Set<String> namespaces, SequencedMap<String, Path> inherited) {
        SequencedMap<String, Path> unprepared = new LinkedHashMap<>();
        inherited.forEach((identity, folder) -> {
            String located = located(identity);
            if (namespaces.stream().noneMatch(namespace -> located.startsWith(namespace + "/"))) {
                unprepared.put(identity, folder);
            }
        });
        return unprepared;
    }

    static Optional<String> exposed(Set<String> namespaces, String path) {
        for (String namespace : namespaces) {
            if (path.equals(namespace) || path.startsWith(namespace + "/")) {
                List<String> segments = List.of(path.split("/"));
                int named = segments.size() > 2 && segments.get(1).equals(ProjectPlugins.PLUGIN) ? 3 : 2;
                return segments.size() > named && segments.get(named).equals(DEPENDENCIES)
                        ? Optional.of(String.join("/", segments.subList(0, named)) + "/" + CLOSURE
                        + (segments.size() > named + 1 ? "/" + String.join("/", segments.subList(named + 1, segments.size())) : ""))
                        : Optional.empty();
            }
        }
        return Optional.of(path);
    }

    private static String located(String identity) {
        String located = identity;
        while (located.startsWith(PREVIOUS)) {
            located = located.substring(PREVIOUS.length());
        }
        return located;
    }

    abstract String identity();

    abstract SequencedSet<String> prepared();

    abstract List<Path> artifacts(SequencedMap<String, Path> prepared) throws IOException;

    abstract M properties(SequencedMap<String, String> properties);

    abstract M inputs(SequencedMap<String, SequencedMap<Path, Path>> inputs);

    abstract M repositories(Map<String, Repository> repositories);

    abstract M resolvers(Map<String, Resolver> resolvers);

    public abstract BuildExecutorModule preparation();

    public M configured(Path base, Path root, SequencedMap<String, String> given) {
        SequencedMap<String, String> values = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<Path, Path>> bound = new LinkedHashMap<>();
        given.forEach((declaration, declaredValue) -> {
            if (declaration.startsWith("@@") || !declaration.startsWith("@")) {
                String key = declaration.startsWith("@@") ? declaration.substring(1) : declaration;
                values.put(key, environment.variable("The plugin " + group + " sets " + key, declaredValue));
                return;
            }
            int slash = declaration.indexOf('/');
            String input = declaration.substring(1, slash == -1 ? declaration.length() : slash);
            String target = slash == -1 ? null : declaration.substring(slash + 1);
            String origin = "The input " + declaration + " of the plugin " + group;
            if (!INPUT_NAME.matcher(input).matches()) {
                throw new IllegalArgumentException(origin + " does not start with a name - name an input"
                        + " with letters, digits, ., _ and -, as @<input> or @<input>/<target>, or write @@"
                        + declaration.substring(1) + " for a value whose key starts with @");
            }
            if (declaredValue.isBlank()) {
                throw new IllegalArgumentException(origin + " binds nothing - name a file or folder of the project");
            }
            Path linked = base.resolve(environment.variable(origin + " binds", declaredValue).trim())
                    .toAbsolutePath()
                    .normalize();
            Path source = Files.isRegularFile(linked) ? linked : Bind.contained(root, linked, origin);
            if (!Files.exists(source)) {
                throw new IllegalArgumentException(origin + " binds " + source + ", which does not exist");
            }
            Path placed = target == null
                    ? Files.isDirectory(source) ? Path.of("") : source.getFileName()
                    : Path.of(target).normalize();
            if (placed.getRoot() != null || placed.startsWith("..")) {
                throw new IllegalArgumentException(origin + " places its files at " + target
                        + " - name a relative folder inside the input, without ..");
            }
            if (bound.computeIfAbsent(input, _ -> new LinkedHashMap<>()).putIfAbsent(placed, source) != null) {
                throw new IllegalArgumentException(origin + " places its files where another binding of @"
                        + input + " places its own - give each binding a target of its own");
            }
        });
        return properties(values).inputs(bound);
    }

    public static Path named(Path properties, String key, String value) {
        Path named = properties.resolveSibling(value.trim()).normalize();
        if (!Files.isRegularFile(named)) {
            throw new IllegalArgumentException(properties + " names " + key + "=" + value.trim() + ", but " + named
                    + " is no file - name it relative to " + properties.getFileName());
        }
        return named;
    }

    M configuredBy(Path properties, Path target) throws IOException {
        SequencedMap<String, String> values = new LinkedHashMap<>();
        SequencedProperties.ofFiles(properties).forEachProperty(values::put);
        String configuration = values.remove(CONFIGURATION);
        if (configuration == null || configuration.isBlank()) {
            throw new IllegalArgumentException(properties + " names no " + CONFIGURATION + " - add " + CONFIGURATION
                    + "=" + target + ", the file relative to " + properties.getFileName());
        }
        return properties(values).bound(RULES, target, named(properties, CONFIGURATION, configuration));
    }

    M discovered(SequencedSet<Path> configuration, Path conventional) throws IOException {
        Path properties = BuildStep.locate(configuration, group + ".properties");
        if (properties != null) {
            return configuredBy(properties, conventional);
        }
        Path file = BuildStep.locate(configuration, conventional.toString());
        return file == null ? null : bound(RULES, conventional, file);
    }

    M bound(String input, Path target, Path source) {
        SequencedMap<String, SequencedMap<Path, Path>> merged = new LinkedHashMap<>(inputs);
        SequencedMap<Path, Path> placed = new LinkedHashMap<>(merged.getOrDefault(input, Collections.emptyNavigableMap()));
        placed.put(target, source);
        merged.put(input, placed);
        return inputs(merged);
    }

    @Override
    public Optional<String> resolve(String path) {
        if (path.equals(DELEGATE)) {
            return Optional.of("");
        }
        if (path.startsWith(DELEGATE + "/")) {
            return Optional.of(path.substring(DELEGATE.length() + 1));
        }
        if (path.equals(DEPENDENCIES) || path.startsWith(DEPENDENCIES + "/")) {
            return Optional.of(CLOSURE + path.substring(DEPENDENCIES.length()));
        }
        return Optional.empty();
    }

    public Observation observation(SequencedMap<String, Path> prepared) throws IOException {
        return loaded(prepared).observe(buildModuleName,
                environment,
                properties,
                ProcessBuildStep.permits(environment),
                group,
                repositories,
                resolvers);
    }

    public Language language(SequencedMap<String, Path> prepared) throws IOException {
        return loaded(prepared).language(buildModuleName,
                environment,
                properties,
                ProcessBuildStep.permits(environment),
                group,
                repositories,
                resolvers);
    }

    public TestFramework framework(SequencedMap<String, Path> prepared) throws IOException {
        return loaded(prepared).framework(buildModuleName,
                environment,
                properties,
                ProcessBuildStep.permits(environment),
                group,
                repositories,
                resolvers);
    }

    private JenesisClassLoaderBridge loaded(SequencedMap<String, Path> prepared) throws IOException {
        if (!inputs.isEmpty()) {
            throw new IllegalArgumentException("The plugin " + identity() + " binds " + inputs.sequencedKeySet()
                    + ", but a plugin that observes or runs the tests is handed no input - remove its @<input> lines");
        }
        try {
            return JenesisClassLoaderBridge.of(group, artifacts(prepared), pathPlacement);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to load the plugin " + identity(), e);
        }
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        preparation().accept(buildExecutor, inherited);
        if (!inputs.isEmpty()) {
            buildExecutor.addModule(INPUTS, Bind.asInputs(inputs));
        }
        buildExecutor.addModule(DELEGATE, new BuildExecutorModule() {

            private BuildExecutorModule plugin;

            @Override
            public void accept(BuildExecutor delegateExecutor, SequencedMap<String, Path> delegated) throws IOException {
                JenesisClassLoaderBridge bridge;
                Object foreignModule;
                try {
                    bridge = JenesisClassLoaderBridge.of(group, artifacts(delegated), pathPlacement);
                    foreignModule = bridge.create(buildModuleName,
                            environment,
                            properties,
                            ProcessBuildStep.permits(environment),
                            group,
                            repositories,
                            resolvers);
                } catch (Exception e) {
                    throw new IllegalStateException("Failed to load the plugin " + identity(), e);
                }
                SequencedMap<String, Path> forwarded = new LinkedHashMap<>(delegated);
                prepared().forEach(name -> forwarded.remove(PREVIOUS + name));
                plugin = bridge.wrapModule(foreignModule, group);
                plugin.accept(delegateExecutor, forwarded);
            }

            @Override
            public Optional<String> resolve(String path) {
                return plugin.resolve(path).map(resolved -> resolved.isEmpty() ? path : resolved);
            }
        }, Stream.of(prepared().stream(), inherited.sequencedKeySet().stream(), inputs.isEmpty() ? Stream.<String>empty() : Stream.of(INPUTS))
                .flatMap(Function.identity()));
    }
}

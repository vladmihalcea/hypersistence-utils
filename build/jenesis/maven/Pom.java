package build.jenesis.maven;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;

public class Pom implements BuildStep {

    public static final String POM = "pom.xml";

    private final Set<String> prefixes;
    private final Map<String, String> shared;
    private final String group;
    private final boolean embedded;
    private final transient MavenPomEmitter emitter = new MavenPomEmitter();

    public Pom() {
        this(Set.of("maven"), Map.of(), "main", false);
    }

    private Pom(Set<String> prefixes, Map<String, String> shared, String group, boolean embedded) {
        this.prefixes = Set.copyOf(prefixes);
        this.shared = Map.copyOf(shared);
        this.group = group;
        this.embedded = embedded;
    }

    public Pom prefixes(Set<String> prefixes) {
        return new Pom(prefixes, shared, group, embedded);
    }

    public Pom shared(Map<String, String> shared) {
        return new Pom(prefixes, shared, group, embedded);
    }

    public Pom group(String group) {
        return new Pom(prefixes, shared, group, embedded);
    }

    public Pom embedded(boolean embedded) {
        return new Pom(prefixes, shared, group, embedded);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(REQUIRES),
                Path.of(EXCLUSIONS),
                Path.of(OPTIONALS),
                Path.of(OVERRIDES),
                Path.of(Dependencies.GRAPH),
                Path.of(METADATA)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        List<Path> folders = arguments.values().stream()
                .filter(argument -> !argument.removed())
                .map(BuildStepArgument::folder)
                .toList();
        SequencedProperties requires = SequencedProperties.ofFolders(folders, REQUIRES);
        SequencedProperties exclusions = SequencedProperties.ofFolders(folders, EXCLUSIONS);
        SequencedProperties optionals = SequencedProperties.ofFolders(folders, OPTIONALS);
        SequencedProperties metadata = SequencedProperties.ofFolders(folders, METADATA);
        SequencedProperties overrides = SequencedProperties.ofFolders(folders, OVERRIDES);
        SequencedSet<MavenDependencyName> dropped = new LinkedHashSet<>();
        SequencedProperties recorded = SequencedProperties.ofFolders(folders, Dependencies.GRAPH);
        for (String key : recorded.stringPropertyNames()) {
            if (key.startsWith("overridden/" + group + "/")) {
                String coordinate = key.substring(key.indexOf('/', "overridden/".length() + group.length() + 1) + 1);
                int prefix = coordinate.indexOf('/'), separator = coordinate.indexOf('/', prefix + 1);
                if (prefixes.contains(coordinate.substring(0, prefix))) {
                    dropped.add(new MavenDependencyName(coordinate.substring(prefix + 1, separator),
                            coordinate.substring(separator + 1)));
                }
            }
        }
        SequencedMap<String, Resolver.Resolution> graph = Dependencies.graph(folders.stream()
                .map(folder -> folder.resolve(Dependencies.GRAPH))
                .toList(), List.of());
        Set<String> coordinateOptionals = new HashSet<>();
        for (String key : optionals.stringPropertyNames()) {
            if (key.startsWith(group + "/")) {
                coordinateOptionals.add(key.substring(key.indexOf('/', key.indexOf('/') + 1) + 1));
            }
        }
        SequencedMap<String, SequencedSet<String>> coordinateScopes = new LinkedHashMap<>();
        for (String key : requires.stringPropertyNames()) {
            int first = key.indexOf('/');
            int second = key.indexOf('/', first + 1);
            String coordinate = key.substring(second + 1), scope = key.substring(first + 1, second);
            if (!key.startsWith(group + "/") || overrides.getProperty(group + "/" + coordinate) != null) {
                continue;
            }
            coordinateScopes.computeIfAbsent(coordinate, _ -> new LinkedHashSet<>()).add(scope);
        }
        SequencedMap<String, String> coordinateExclusions = new LinkedHashMap<>();
        for (String key : exclusions.stringPropertyNames()) {
            int second = key.indexOf('/', key.indexOf('/') + 1);
            coordinateExclusions.putIfAbsent(key.substring(second + 1), exclusions.getProperty(key));
        }
        shared.forEach(metadata::setProperty);
        String groupId = metadata.getProperty("project");
        if (groupId == null) {
            throw new IllegalStateException("Missing 'project' (groupId) in metadata.properties");
        }
        String artifactId = metadata.getProperty("artifact");
        if (artifactId == null) {
            throw new IllegalStateException(
                    "Missing 'artifact' (artifactId) in metadata.properties for " + groupId);
        }
        String version = metadata.getProperty("version", "0-SNAPSHOT");
        SequencedMap<MavenDependencyKey, MavenDependencyValue> deps = new LinkedHashMap<>();
        for (Map.Entry<String, SequencedSet<String>> scopedEntry : coordinateScopes.entrySet()) {
            String name = scopedEntry.getKey();
            boolean inCompile = scopedEntry.getValue().contains("compile");
            boolean inRuntime = scopedEntry.getValue().contains("runtime");
            int separator = name.indexOf('/');
            if (separator == -1 || !inCompile && !inRuntime) {
                continue;
            }
            boolean maven = prefixes.contains(name.substring(0, separator));
            String module = name.substring(separator + 1);
            String coordinate = scopedEntry.getValue().stream()
                    .map(scope -> graph.get(group + "/" + scope))
                    .filter(Objects::nonNull)
                    .flatMap(resolution -> resolution.vertices().entrySet().stream())
                    .filter(vertex -> vertex.getValue().resolvedVersion() != null && (maven
                            ? vertex.getKey().equals(name)
                            : module.equals(vertex.getValue().module())
                                    && prefixes.contains(vertex.getKey().substring(0, vertex.getKey().indexOf('/')))))
                    .findFirst()
                    .map(vertex -> vertex.getKey() + "/" + vertex.getValue().resolvedVersion())
                    .orElse(maven ? name : null);
            if (coordinate == null) {
                throw new IllegalStateException("The POM of " + groupId + ":" + artifactId + " names each module its"
                        + " module requires by the Maven artifact it resolved to, and " + module + " resolved to none:"
                        + " name its artifact with @jenesis.alias " + module + " <groupId>/<artifactId>");
            }
            separator = coordinate.indexOf('/');
            if (!prefixes.contains(coordinate.substring(0, separator))) {
                continue;
            }
            MavenDependencyKey.Versioned parsed = MavenDependencyKey.parse(coordinate.substring(separator + 1));
            MavenDependencyScope scope = inCompile && inRuntime
                    ? MavenDependencyScope.COMPILE
                    : inCompile ? MavenDependencyScope.PROVIDED : MavenDependencyScope.RUNTIME;
            SequencedSet<MavenDependencyName> excluded = new LinkedHashSet<>();
            String exclusionList = coordinateExclusions.get(name);
            if (exclusionList != null && !exclusionList.isEmpty()) {
                for (String entry : exclusionList.split(",")) {
                    int slash = entry.indexOf('/');
                    if (slash > 0) {
                        excluded.add(new MavenDependencyName(entry.substring(0, slash), entry.substring(slash + 1)));
                    }
                }
            }
            excluded.addAll(dropped);
            List<MavenDependencyName> excludes = excluded.isEmpty() ? null : List.copyOf(excluded);
            deps.putIfAbsent(parsed.key(), new MavenDependencyValue(
                    parsed.version(),
                    scope,
                    null,
                    excludes,
                    coordinateOptionals.contains(name) ? Boolean.TRUE : null));
        }
        try (Writer writer = Files.newBufferedWriter(context.next().resolve(POM))) {
            emitter.emit(
                    groupId,
                    artifactId,
                    version,
                    deps,
                    MavenPomEmitter.Metadata.of(metadata)).accept(writer);
        }
        if (embedded) {
            Path folder = Files.createDirectories(context.next()
                    .resolve(RESOURCES + "META-INF/maven")
                    .resolve(groupId)
                    .resolve(artifactId));
            Files.copy(context.next().resolve(POM), folder.resolve(POM));
            SequencedProperties coordinate = new SequencedProperties();
            coordinate.setProperty("artifactId", artifactId);
            coordinate.setProperty("groupId", groupId);
            coordinate.setProperty("version", version);
            coordinate.store(folder.resolve("pom.properties"));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

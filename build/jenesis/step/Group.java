package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class Group implements BuildStep {

    public static final String GROUPS = "groups.properties", REACTOR = "reactor.properties";

    private final Function<String, Optional<String>> identification;
    private final String requiresPath;

    public <F extends Function<String, Optional<String>> & Serializable> Group(F identification) {
        this(identification, REQUIRES);
    }

    private Group(Function<String, Optional<String>> identification, String requiresPath) {
        this.identification = identification;
        this.requiresPath = requiresPath;
    }

    public Group requiresPath(String requiresPath) {
        return new Group(identification, requiresPath);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument ->
                argument.hasChanged(Path.of(IDENTITY), Path.of(requiresPath), Path.of(REACTOR)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Map<String, Set<String>> from = new HashMap<>(), to = new LinkedHashMap<>(), ordered = new LinkedHashMap<>();
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            if (entry.getValue().removed()) {
                continue;
            }
            String name = identification.apply(entry.getKey()).orElse(null);
            if (name == null) {
                continue;
            }
            toProperties(entry.getValue().folder().resolve(IDENTITY)).forEach(dependency -> from.computeIfAbsent(
                    dependency,
                    _ -> new LinkedHashSet<>()).add(name));
            Set<String> requires = to.computeIfAbsent(name, _ -> new LinkedHashSet<>());
            for (String coordinate : toProperties(entry.getValue().folder().resolve(requiresPath))) {
                int second = coordinate.indexOf('/', coordinate.indexOf('/') + 1);
                requires.add(coordinate.substring(second + 1));
            }
            ordered.put(name, toProperties(entry.getValue().folder().resolve(REACTOR)));
        }
        Map<String, SequencedSet<String>> edges = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : to.entrySet()) {
            edges.put(entry.getKey(), entry.getValue().stream()
                    .flatMap(dependency -> from.getOrDefault(dependency,
                            from.getOrDefault(Assign.unclassified(dependency), Set.of())).stream())
                    .filter(name -> !name.equals(entry.getKey()))
                    .collect(Collectors.toCollection(LinkedHashSet::new)));
        }
        for (Map.Entry<String, Set<String>> entry : ordered.entrySet()) {
            for (String coordinate : new TreeSet<>(entry.getValue())) {
                for (String target : from.getOrDefault(coordinate, Set.of())) {
                    if (!target.equals(entry.getKey()) && !reaches(edges, target, entry.getKey())) {
                        edges.get(entry.getKey()).add(target);
                    }
                }
            }
        }
        SequencedProperties groups = new SequencedProperties();
        edges.forEach((name, targets) -> groups.setProperty(name, String.join(",", targets)));
        groups.store(context.next().resolve(GROUPS));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static boolean reaches(Map<String, SequencedSet<String>> edges, String origin, String target) {
        Set<String> visited = new HashSet<>();
        Deque<String> queue = new ArrayDeque<>(List.of(origin));
        while (!queue.isEmpty()) {
            String current = queue.remove();
            if (current.equals(target)) {
                return true;
            }
            if (visited.add(current)) {
                queue.addAll(edges.getOrDefault(current, Collections.emptySortedSet()));
            }
        }
        return false;
    }

    private static Set<String> toProperties(Path file) throws IOException {
        if (Files.exists(file)) {
            return SequencedProperties.ofFiles(file).stringPropertyNames();
        } else {
            return Set.of();
        }
    }
}

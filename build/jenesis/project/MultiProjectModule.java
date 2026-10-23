package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Group;

public record MultiProjectModule(BuildExecutorModule identifier,
                                 Function<String, Optional<String>> resolver,
                                 Function<SequencedMap<String, SequencedSet<String>>, MultiProject> factory)
        implements BuildExecutorModule {

    public static final String IDENTIFIER = "identifier",
            COMPOSE = "compose",
            MODULE = "module",
            TESTED = "tested";
    public static final String SOURCES = "sources",
            MANIFESTS = "manifests",
            COORDINATES = "coordinates",
            PREPARE = "prepare",
            PRODUCE = "produce",
            ASSIGN = "assign",
            INVENTORY = "inventory",
            DEPENDENCIES = "dependencies",
            ARTIFACTS = "artifacts",
            SIGNATURES = "signatures",
            SPDX = "spdx";
    private static final String GROUP = "group";
    public static final String IDENTIFIER_PATH = PREVIOUS.repeat(3) + IDENTIFIER + "/";

    public static boolean test(SequencedMap<String, Path> arguments) throws IOException {
        for (Map.Entry<String, Path> entry : arguments.entrySet()) {
            if (entry.getKey().endsWith("/" + MANIFESTS)) {
                Path module = entry.getValue().resolve(BuildStep.MODULE);
                return Files.isRegularFile(module) && SequencedProperties.ofFiles(module).getProperty("test") != null;
            }
        }
        return false;
    }

    public static Path location(Path root, SequencedMap<String, Path> arguments) throws IOException {
        for (Map.Entry<String, Path> entry : arguments.entrySet()) {
            if (entry.getKey().endsWith("/" + MANIFESTS)) {
                Path module = entry.getValue().resolve(BuildStep.MODULE);
                if (Files.isRegularFile(module)) {
                    String path = SequencedProperties.ofFiles(module).getProperty("path");
                    if (path != null) {
                        return root.resolve(path);
                    }
                }
                return null;
            }
        }
        return null;
    }

    @Override
    public Optional<String> resolve(String path) {
        if (path.startsWith(IDENTIFIER + "/")) {
            if (path.endsWith("/" + COORDINATES)) {
                return Optional.empty();
            }
            return Optional.of(path.substring(IDENTIFIER.length() + 1));
        }
        String composeModulePrefix = COMPOSE + "/" + MODULE + "/";
        if (path.startsWith(composeModulePrefix)) {
            return Optional.of(path.substring(composeModulePrefix.length()));
        }
        return Optional.empty();
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addModule(IDENTIFIER, identifier, inherited.sequencedKeySet().stream());
        buildExecutor.addModule(COMPOSE, (process, identified) -> {
            SequencedMap<String, String> modules = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> identifiers = new LinkedHashMap<>();
            for (String identifier : identified.sequencedKeySet()) {
                if (identifier.startsWith(PREVIOUS + IDENTIFIER + "/")) {
                    resolver.apply(identifier.substring(PREVIOUS.length() + IDENTIFIER.length() + 1)).ifPresent(module -> {
                        if (module.isEmpty()) {
                            throw new IllegalArgumentException("Module name must not be empty");
                        }
                        modules.put(identifier, module);
                        identifiers.computeIfAbsent(module, _ -> new LinkedHashSet<>()).add(identifier);
                    });
                }
            }
            process.addStep(GROUP,
                    new Group(identifier -> Optional.of(modules.get(identifier)))
                            .requiresPath(BuildStep.REQUIRES),
                    modules.sequencedKeySet());
            process.addModule(MODULE, (build, paths) -> {
                SequencedMap<String, SequencedSet<String>> projects = new LinkedHashMap<>();
                SequencedProperties groups = SequencedProperties.ofFiles(paths.get(PREVIOUS + GROUP).resolve(Group.GROUPS));
                for (String module : identifiers.keySet()) {
                    List<String> required = groups.entries(module);
                    projects.put(module, required == null ? new LinkedHashSet<>() : new LinkedHashSet<>(required));
                }
                MultiProject project = factory.apply(projects);
                SequencedMap<String, AssemblyDescriptor> assemblies = new LinkedHashMap<>();
                SequencedMap<String, SequencedSet<String>> closures = new LinkedHashMap<>(), testers = new LinkedHashMap<>();
                SequencedMap<String, SequencedMap<String, Path>> arguments = new LinkedHashMap<>();
                SequencedMap<String, SequencedSet<String>> pending = new LinkedHashMap<>(projects);
                while (!pending.isEmpty()) {
                    boolean progressed = false;
                    Iterator<Map.Entry<String, SequencedSet<String>>> it = pending.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, SequencedSet<String>> entry = it.next();
                        if (Collections.disjoint(entry.getValue(), pending.keySet())) {
                            SequencedMap<String, Path> identifying = new LinkedHashMap<>();
                            identifiers.get(entry.getKey()).forEach(identifier -> identifying.put(
                                    PREVIOUS + identifier,
                                    paths.get(PREVIOUS + identifier)));
                            SequencedMap<String, SequencedSet<String>> dependencies = new LinkedHashMap<>();
                            Queue<String> queue = new LinkedList<>(entry.getValue());
                            while (!queue.isEmpty()) {
                                String current = queue.remove();
                                if (!dependencies.containsKey(current)) {
                                    SequencedSet<String> values = projects.get(current);
                                    dependencies.put(current, values);
                                    queue.addAll(values);
                                }
                            }
                            assemblies.put(entry.getKey(), project.module(entry.getKey(), dependencies, identifying));
                            arguments.put(entry.getKey(), identifying);
                            closures.put(entry.getKey(), dependencies.sequencedKeySet());
                            if (test(identifying)) {
                                entry.getValue().forEach(tested -> testers.computeIfAbsent(tested, _ -> new LinkedHashSet<>())
                                        .add(entry.getKey()));
                            }
                            it.remove();
                            progressed = true;
                        }
                    }
                    if (!progressed) {
                        throw new IllegalStateException("Cyclic module dependencies: " + pending.keySet());
                    }
                }
                SequencedMap<String, AssemblyDescriptor.Registration> registrations = new LinkedHashMap<>();
                SequencedSet<String> passed = inherited.sequencedKeySet().stream()
                        .map(key -> PREVIOUS.repeat(2) + key)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                for (Map.Entry<String, AssemblyDescriptor> assembly : assemblies.entrySet()) {
                    String module = assembly.getKey();
                    SequencedMap<String, String> built = new LinkedHashMap<>(), followed = new LinkedHashMap<>();
                    for (String identifier : arguments.get(module).sequencedKeySet()) {
                        String identity = identifier.substring(PREVIOUS.repeat(2).length() + IDENTIFIER.length() + 1);
                        built.put(identifier, identifier);
                        followed.put(identifier, identity.startsWith(module + "/") ? identity.substring(module.length() + 1) : identity);
                    }
                    registrations.putAll(assembly.getValue().toBuildModules(module,
                            built,
                            followed,
                            closures.get(module),
                            testers.getOrDefault(module, Collections.emptyNavigableSet()),
                            assemblies,
                            passed));
                }
                Set<String> registered = new HashSet<>();
                while (!registrations.isEmpty()) {
                    boolean progressed = false;
                    Iterator<Map.Entry<String, AssemblyDescriptor.Registration>> it = registrations.entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry<String, AssemblyDescriptor.Registration> entry = it.next();
                        if (entry.getValue().requirements().sequencedKeySet().stream()
                                .filter(key -> !key.startsWith(PREVIOUS))
                                .allMatch(registered::contains)) {
                            build.addModule(entry.getKey(), entry.getValue().module(), entry.getValue().requirements());
                            registered.add(entry.getKey());
                            it.remove();
                            progressed = true;
                        }
                    }
                    if (!progressed) {
                        throw new IllegalStateException("Cyclic phase dependencies: " + registrations.keySet());
                    }
                }
            }, Stream.concat(Stream.of(GROUP), identified.sequencedKeySet().stream()));
        }, Stream.concat(Stream.of(IDENTIFIER), inherited.sequencedKeySet().stream()));
    }
}

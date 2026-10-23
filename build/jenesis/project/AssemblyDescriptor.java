package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;

public final class AssemblyDescriptor {

    public static final String BUILD = "build";
    private static final Pattern NAME = Pattern.compile("[a-zA-Z0-9._]+");

    private final SequencedMap<String, BuildExecutorModule> modules;
    private final SequencedMap<String, AssemblyRequirement> requirements;

    private AssemblyDescriptor(SequencedMap<String, BuildExecutorModule> modules,
                               SequencedMap<String, AssemblyRequirement> requirements) {
        this.modules = modules;
        this.requirements = requirements;
    }

    public static AssemblyDescriptor of(BuildExecutorModule build, AssemblyRequirement requirement) {
        return new AssemblyDescriptor(Collections.emptyNavigableMap(), Collections.emptyNavigableMap())
                .andThen(BUILD, build, requirement);
    }

    public AssemblyDescriptor andThen(String phase, BuildExecutorModule module, AssemblyRequirement requirement) {
        if (!NAME.matcher(phase).matches()) {
            throw new IllegalArgumentException("A phase is named by letters, digits, '.' and '_' alone, so that"
                    + " <phase>-<module> names one phase of one module: " + phase);
        }
        SequencedMap<String, BuildExecutorModule> modules = new LinkedHashMap<>(this.modules);
        SequencedMap<String, AssemblyRequirement> requirements = new LinkedHashMap<>(this.requirements);
        BuildExecutorModule declared = modules.get(phase);
        if (declared == null) {
            modules.put(phase, module);
            requirements.put(phase, requirement);
        } else {
            modules.put(phase, new Sequence(declared, module));
            requirements.put(phase, requirements.get(phase).and(requirement));
        }
        List<String> before = modules.sequencedKeySet().stream().takeWhile(key -> !key.equals(phase)).toList();
        for (String predecessor : requirements.get(phase).after()) {
            if (!before.contains(predecessor)) {
                throw new IllegalArgumentException("The phase " + phase + " follows " + predecessor
                        + ", which is no phase declared before it - expected one of " + before);
            }
        }
        return new AssemblyDescriptor(modules, requirements);
    }

    public AssemblyDescriptor nest(String phase, String location, UnaryOperator<BuildExecutorModule> nesting) {
        BuildExecutorModule module = modules.get(phase);
        if (module == null) {
            throw new IllegalArgumentException("No assembly phase " + phase + " - expected one of " + modules.sequencedKeySet());
        }
        SequencedMap<String, BuildExecutorModule> modules = new LinkedHashMap<>(this.modules);
        modules.put(phase, new Nested(nesting.apply(module), location));
        return new AssemblyDescriptor(modules, requirements);
    }

    public String build() {
        return BUILD;
    }

    public BuildExecutorModule toBuildModule() {
        return modules.get(BUILD);
    }

    public BuildExecutorModule toPhasesModule() {
        return (buildExecutor, inherited) -> {
            for (Map.Entry<String, AssemblyRequirement> phase : requirements.entrySet()) {
                SequencedMap<String, String> required = new LinkedHashMap<>();
                inherited.sequencedKeySet().forEach(key -> required.put(key, key));
                phase.getValue().after().forEach(predecessor -> required.put(predecessor, predecessor));
                buildExecutor.addModule(phase.getKey(), modules.get(phase.getKey()), required);
            }
        };
    }

    SequencedMap<String, Registration> toBuildModules(String module,
                                                      SequencedMap<String, String> built,
                                                      SequencedMap<String, String> followed,
                                                      SequencedSet<String> dependencies,
                                                      SequencedSet<String> testers,
                                                      Map<String, AssemblyDescriptor> assemblies,
                                                      SequencedSet<String> inherited) {
        SequencedMap<String, Registration> registrations = new LinkedHashMap<>();
        requirements.forEach((phase, requirement) -> {
            SequencedMap<String, String> required = new LinkedHashMap<>(phase.equals(BUILD) ? built : followed);
            for (String predecessor : requirement.after()) {
                required.put(predecessor + "-" + module, predecessor);
            }
            for (String dependency : dependencies) {
                for (String upstream : requirement.upstream()) {
                    if (assemblies.get(dependency).modules.containsKey(upstream)) {
                        required.put(upstream + "-" + dependency, upstream + "-" + dependency);
                    }
                }
            }
            for (String tester : testers) {
                for (String tested : requirement.tested()) {
                    if (assemblies.get(tester).modules.containsKey(tested)) {
                        required.put(tested + "-" + tester, MultiProjectModule.TESTED + "/" + tested + "-" + tester);
                    }
                }
            }
            inherited.forEach(key -> required.put(key, key));
            registrations.put(phase + "-" + module, new Registration(modules.get(phase), required));
        });
        return registrations;
    }

    record Registration(BuildExecutorModule module, SequencedMap<String, String> requirements) {
    }

    private record Sequence(BuildExecutorModule first, BuildExecutorModule second) implements BuildExecutorModule {

        @Override
        public Optional<String> resolve(String path) {
            return first.resolve(path);
        }

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            first.accept(buildExecutor, inherited);
            second.accept(buildExecutor, inherited);
        }
    }

    private record Nested(BuildExecutorModule module, String location) implements BuildExecutorModule {

        @Override
        public Optional<String> resolve(String path) {
            return module.resolve(path).map(resolved -> resolved.startsWith(location + "/")
                    ? resolved.substring(location.length() + 1)
                    : resolved);
        }

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            module.accept(buildExecutor, inherited);
        }
    }
}

package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;

public class Layers implements BuildStep {

    public static final String MEMBERSHIP = "layered.properties";
    private static final String MODULE_PATH = "modulepath.", CLASS_PATH = "classpath.";
    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final String group;

    public Layers() {
        this("main");
    }

    private Layers(String group) {
        this.group = group;
    }

    public Layers group(String group) {
        return new Layers(group);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, Declaration> declared = declared(arguments);
        if (declared.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedMap<String, ModuleDescriptor> host = descriptors(arguments);
        SequencedProperties membership = new SequencedProperties();
        for (Map.Entry<String, Declaration> entry : declared.entrySet()) {
            String layer = entry.getKey(), api = entry.getValue().api();
            SequencedMap<String, ModuleDescriptor> isolated = new LinkedHashMap<>();
            SequencedSet<String> unnamed = new LinkedHashSet<>();
            SequencedSet<String> names = isolate(layer, api, closure(api, host), arguments, isolated, unnamed);
            verify(layer, api, isolated);
            membership.setProperty(MODULE_PATH + layer, String.join(",", names));
            if (!unnamed.isEmpty()) {
                membership.setProperty(CLASS_PATH + layer, String.join(",", unnamed));
            }
        }
        membership.store(context.next().resolve(MEMBERSHIP));
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    public static SequencedMap<String, Declaration> declared(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, Declaration> declared = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path declaration = argument.folder().resolve(BuildStep.LAYERS);
            if (!Files.isRegularFile(declaration)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(declaration);
            for (String layer : properties.stringPropertyNames()) {
                SAFE_SEGMENT.accept("layer name", layer);
                List<String> tokens = properties.words(layer);
                if (tokens.size() != 2) {
                    throw new IllegalArgumentException("Layer " + layer + " in " + declaration
                            + " is not '<declaring module> <api module>': " + tokens);
                }
                Declaration previous = declared.putIfAbsent(layer,
                        new Declaration(tokens.getFirst(), tokens.getLast()));
                if (previous != null && !previous.module().equals(tokens.getFirst())) {
                    throw new IllegalStateException(previous.module() + " and " + tokens.getFirst()
                            + " both declare a layer called " + layer
                            + " - rename one, so a pin and a group name say which is meant");
                }
            }
        }
        return declared;
    }

    private static SequencedSet<String> closure(String api, SequencedMap<String, ModuleDescriptor> host) {
        SequencedSet<String> shared = new LinkedHashSet<>();
        Deque<String> pending = new ArrayDeque<>(List.of(api, "build.jenesis.launcher"));
        while (!pending.isEmpty()) {
            String module = pending.removeFirst();
            if (ModuleGraph.isSystemModule(module) || !shared.add(module)) {
                continue;
            }
            ModuleDescriptor descriptor = host.get(module);
            if (descriptor != null) {
                descriptor.requires().forEach(requires -> pending.addLast(requires.name()));
            }
        }
        return shared;
    }

    private static SequencedSet<String> isolate(String layer,
                                                String api,
                                                SequencedSet<String> shared,
                                                SequencedMap<String, BuildStepArgument> arguments,
                                                SequencedMap<String, ModuleDescriptor> isolated,
                                                SequencedSet<String> unnamed)
            throws IOException {
        SequencedSet<String> names = new LinkedHashSet<>();
        SequencedMap<String, String> carriers = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.select(argument.folder(), "layer:" + layer, "runtime")) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                if (descriptor == null) {
                    unnamed.add(jar.getFileName().toString());
                    continue;
                }
                if (shared.contains(descriptor.name())) {
                    continue;
                }
                String file = jar.getFileName().toString();
                String carrier = carriers.putIfAbsent(descriptor.name(), file);
                if (carrier == null) {
                    isolated.put(descriptor.name(), descriptor);
                    names.add(file);
                } else if (!carrier.equals(file)) {
                    throw new IllegalStateException(carrier + " and " + file + " both carry module "
                            + descriptor.name() + " in layer " + layer + " - a layer resolves whichever of"
                            + " the two comes first, so drop one with @jenesis.exclude");
                }
            }
        }
        if (isolated.isEmpty()) {
            throw new IllegalStateException("Layer " + layer + " holds no module: " + (unnamed.isEmpty()
                    ? "everything it resolves is already shared through " + api + " - a dependency whose"
                            + " types the API module reaches is exposed by it, and cannot be isolated"
                            + " behind it"
                    : "it resolves " + unnamed + ", which carry no module identity - a layer is reached"
                            + " through the modules it holds, so name one with modules.properties"));
        }
        return names;
    }

    public record Membership(SequencedSet<String> modulepath, SequencedSet<String> classpath) {

        public SequencedSet<String> all() {
            SequencedSet<String> all = new LinkedHashSet<>(modulepath);
            all.addAll(classpath);
            return all;
        }

        public SequencedSet<String> platform(Map<String, Path> jars) {
            SequencedMap<String, ModuleDescriptor> system = new TreeMap<>();
            ModuleFinder.ofSystem().findAll().forEach(reference -> system.put(reference.descriptor().name(), reference.descriptor()));
            SequencedSet<String> platform = new TreeSet<>();
            boolean unnamed = !classpath.isEmpty();
            for (String name : modulepath) {
                ModuleDescriptor descriptor = jars.containsKey(name) ? PathPlacement.moduleDescriptor(jars.get(name)) : null;
                if (descriptor == null || descriptor.isAutomatic()) {
                    unnamed = true;
                } else {
                    descriptor.requires().stream()
                            .map(ModuleDescriptor.Requires::name)
                            .filter(system::containsKey)
                            .forEach(platform::add);
                }
            }
            if (unnamed) {
                system.values().stream()
                        .filter(descriptor -> descriptor.exports().stream().anyMatch(exports -> !exports.isQualified()))
                        .map(ModuleDescriptor::name)
                        .forEach(platform::add);
            }
            return platform;
        }

        public SequencedMap<Path, Boolean> nativeAccess(Map<String, Path> jars, Set<Path> granted) {
            SequencedMap<Path, Boolean> members = new LinkedHashMap<>();
            for (String name : all()) {
                Path jar = jars.get(name);
                if (jar != null && granted.contains(jar.toAbsolutePath().normalize())) {
                    members.put(jar, modulepath.contains(name));
                }
            }
            return members;
        }
    }

    public static SequencedMap<String, Membership> membership(Path folder) throws IOException {
        Path file = folder.resolve(MEMBERSHIP);
        if (!Files.isRegularFile(file)) {
            return Collections.emptyNavigableMap();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedMap<String, SequencedSet<String>> modules = new LinkedHashMap<>(), classes = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            SequencedMap<String, SequencedSet<String>> target;
            String prefix;
            if (key.startsWith(MODULE_PATH)) {
                target = modules;
                prefix = MODULE_PATH;
            } else if (key.startsWith(CLASS_PATH)) {
                target = classes;
                prefix = CLASS_PATH;
            } else {
                throw new IllegalStateException("Layer entry '" + key + "' in " + file + " names neither a"
                        + " module path nor a class path - expected '" + MODULE_PATH + "<declaring module>"
                        + ".<name>' or '" + CLASS_PATH + "<declaring module>.<name>'");
            }
            List<String> names = properties.entries(key);
            target.put(key.substring(prefix.length()), names == null
                    ? new LinkedHashSet<>()
                    : new LinkedHashSet<>(names));
        }
        SequencedMap<String, Membership> membership = new LinkedHashMap<>();
        SequencedSet<String> layers = new LinkedHashSet<>(modules.sequencedKeySet());
        layers.addAll(classes.sequencedKeySet());
        for (String layer : layers) {
            membership.put(layer, new Membership(
                    modules.getOrDefault(layer, new LinkedHashSet<>()),
                    classes.getOrDefault(layer, new LinkedHashSet<>())));
        }
        return membership;
    }

    private SequencedMap<String, ModuleDescriptor> descriptors(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, ModuleDescriptor> descriptors = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.select(argument.folder(), group, "runtime")) {
                describe(jar, descriptors);
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        describe(file, descriptors);
                    }
                }
            }
        }
        return descriptors;
    }

    private static void describe(Path file, SequencedMap<String, ModuleDescriptor> descriptors) {
        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
        if (descriptor != null) {
            descriptors.putIfAbsent(descriptor.name(), descriptor);
        }
    }

    private static void verify(String layer, String api, SequencedMap<String, ModuleDescriptor> isolated) {
        for (ModuleDescriptor descriptor : isolated.values()) {
            for (ModuleDescriptor.Provides provides : descriptor.provides()) {
                String contract = provides.service();
                int last = contract.lastIndexOf('.');
                String declaring = last < 1 ? null : declaring(contract.substring(0, last), isolated);
                if (declaring != null) {
                    throw new IllegalStateException("Layer " + layer + " provides " + contract
                            + ", but holds " + declaring + ", which declares it - this module would look the"
                            + " service up against a different class of the same name and find no provider;"
                            + " the contract belongs in " + api + ", the module the layer shares with it");
                }
            }
        }
    }

    private static String declaring(String contract, SequencedMap<String, ModuleDescriptor> isolated) {
        for (Map.Entry<String, ModuleDescriptor> entry : isolated.entrySet()) {
            if (entry.getValue().packages().contains(contract)) {
                return entry.getKey();
            }
        }
        return null;
    }

    public record Declaration(String module, String api) {
    }
}

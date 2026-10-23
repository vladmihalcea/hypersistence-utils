package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.DependencyTreeReport;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.Palette;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class Tree implements BuildStep {

    private final transient Consumer<String> out;
    private final transient Palette palette;
    private final transient boolean compact, merged, internal, tests, tools;
    private final transient String group;

    public Tree() {
        this(null, Palette.NONE, false, true, false, true, false, "main");
    }

    public static Tree ofEnvironment(Environment environment) {
        Tree tree = new Tree(environment.out(),
                Palette.ofEnvironment(environment),
                false,
                environment.flag("tree.merge", true),
                environment.flag("tree.internal", false),
                environment.flag("tree.tests", true),
                environment.flag("tree.tools", false),
                "main");
        String format = environment.getProperty("tree.format");
        return format == null ? tree : tree.compact(switch (format) {
            case "full" -> false;
            case "compact" -> true;
            default -> throw new IllegalArgumentException(
                    "Unknown jenesis.tree.format '" + format + "', expected 'full' or 'compact'");
        });
    }

    private Tree(Consumer<String> out,
                 Palette palette,
                 boolean compact,
                 boolean merged,
                 boolean internal,
                 boolean tests,
                 boolean tools,
                 String group) {
        this.out = out;
        this.palette = palette;
        this.compact = compact;
        this.merged = merged;
        this.internal = internal;
        this.tests = tests;
        this.tools = tools;
        this.group = group;
    }

    public Tree printing(Consumer<String> out, Palette palette) {
        return new Tree(out, palette, compact, merged, internal, tests, tools, group);
    }

    public Tree compact(boolean compact) {
        return new Tree(out, palette, compact, merged, internal, tests, tools, group);
    }

    public Tree merged(boolean merged) {
        return new Tree(out, palette, compact, merged, internal, tests, tools, group);
    }

    public Tree internal(boolean internal) {
        return new Tree(out, palette, compact, merged, internal, tests, tools, group);
    }

    public Tree tests(boolean tests) {
        return new Tree(out, palette, compact, merged, internal, tests, tools, group);
    }

    public Tree tools(boolean tools) {
        return new Tree(out, palette, compact, merged, internal, tests, tools, group);
    }

    public Tree group(String group) {
        return new Tree(out, palette, compact, merged, internal, tests, tools, group);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        if (out == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        Map<Path, SequencedProperties> inventories = new HashMap<>();
        SequencedMap<Path, String> prefixes = new LinkedHashMap<>();
        SequencedMap<String, String> locations = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String key : inventory.stringPropertyNames()) {
                if (key.endsWith(".graph.0")) {
                    String prefix = key.substring(0, key.length() - "graph.0".length());
                    for (int index = 0; inventory.value(prefix + "identity." + index) != null; index++) {
                        String identity = inventory.value(prefix + "identity." + index);
                        locations.put(identity.startsWith("maven/")
                                ? identity.substring(0, identity.lastIndexOf('/'))
                                : identity, "./" + inventory.value(prefix + "path", ""));
                    }
                    inventories.put(argument.folder(), inventory);
                    prefixes.put(argument.folder(), prefix);
                    break;
                }
            }
        }
        DependencyTreeReport report = new DependencyTreeReport(out, palette)
                .compact(compact)
                .merged(merged)
                .internal(internal)
                .locations(locations);
        SequencedMap<String, Resolver.Vertex> aggregated = new LinkedHashMap<>();
        for (Map.Entry<Path, String> entry : prefixes.entrySet()) {
            SequencedProperties inventory = inventories.get(entry.getKey());
            String prefix = entry.getValue();
            if (!tests && inventory.getProperty(prefix + "test") != null) {
                continue;
            }
            String identity = inventory.value(prefix + "identity.0");
            for (int index = 0; inventory.value(prefix + "identity." + index) != null; index++) {
                if (inventory.value(prefix + "identity." + index).startsWith("maven/")) {
                    identity = inventory.value(prefix + "identity." + index);
                    break;
                }
            }
            boolean maven = identity != null && identity.startsWith("maven/");
            String key = maven ? identity.substring(0, identity.lastIndexOf('/')) : identity,
                    version = maven ? identity.substring(identity.lastIndexOf('/') + 1) : inventory.value(prefix + "version");
            List<License> licenses = new ArrayList<>();
            for (int index = 0; inventory.value(prefix + "license." + index) != null; index++) {
                licenses.add(new License(null, null, inventory.value(prefix + "license." + index), null));
            }
            Resolver.Vertex root = new Resolver.Vertex(version, inventory.value(prefix + "module"), false, true, licenses);
            List<Path> graphs = Inventory.paths(inventory, entry.getKey(), prefix + "graph");
            SequencedMap<String, String> declarations = Dependencies.layers(graphs);
            SequencedMap<String, SequencedMap<String, Resolver.Resolution>> groups = new LinkedHashMap<>();
            SequencedMap<String, Resolver.Resolution> layered = new LinkedHashMap<>();
            SequencedMap<String, String> layers = new LinkedHashMap<>();
            Dependencies.graph(
                    graphs,
                    Inventory.paths(inventory, entry.getKey(), prefix + "licenses")).forEach((groupScope, resolution) -> {
                String named = groupScope.substring(0, groupScope.indexOf('/')),
                        scope = groupScope.substring(groupScope.indexOf('/') + 1);
                if (key != null && named.startsWith("layer:")) {
                    String name = scope.equals("runtime") ? named : groupScope;
                    layered.put(name, resolution);
                    layers.put(name, declarations.getOrDefault(named.substring("layer:".length()), key));
                } else if (key == null || tools || named.equals(group)) {
                    groups.computeIfAbsent(named, _ -> new TreeMap<>()).put(scope, resolution);
                } else {
                    return;
                }
                aggregated.putAll(resolution.vertices());
            });
            if (!layered.isEmpty()) {
                groups.computeIfAbsent(group, _ -> new TreeMap<>());
            }
            groups.forEach((named, scopes) -> {
                if (key == null) {
                    report.render(named, scopes);
                } else if (named.equals(group)) {
                    SequencedMap<String, Resolver.Resolution> merged = new LinkedHashMap<>(scopes);
                    merged.putAll(layered);
                    report.render(merged, key, root, layers);
                } else {
                    report.render("Group " + named + ", resolved to build "
                            + (version == null ? key : key + " " + version) + ":", scopes);
                }
            });
        }
        report.summary(aggregated);
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

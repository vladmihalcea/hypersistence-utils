package build.jenesis;

import module java.base;

public final class DependencyTreeReport {

    private static final int[] GRADIENT = {
            39, 44, 48, 83, 113, 148, 184, 214, 208, 203, 168, 134};

    private final Consumer<String> out;
    private final Palette palette;
    private final boolean compact, merged, internal;
    private final Map<String, String> locations;

    public DependencyTreeReport(Consumer<String> out, Palette palette) {
        this(out, palette, false, true, false, Map.of());
    }

    private DependencyTreeReport(Consumer<String> out,
                                 Palette palette,
                                 boolean compact,
                                 boolean merged,
                                 boolean internal,
                                 Map<String, String> locations) {
        this.out = out;
        this.palette = palette;
        this.compact = compact;
        this.merged = merged;
        this.internal = internal;
        this.locations = locations;
    }

    public DependencyTreeReport compact(boolean compact) {
        return new DependencyTreeReport(out, palette, compact, merged, internal, locations);
    }

    public DependencyTreeReport merged(boolean merged) {
        return new DependencyTreeReport(out, palette, compact, merged, internal, locations);
    }

    public DependencyTreeReport internal(boolean internal) {
        return new DependencyTreeReport(out, palette, compact, merged, internal, locations);
    }

    public DependencyTreeReport locations(Map<String, String> locations) {
        return new DependencyTreeReport(out, palette, compact, merged, internal, locations);
    }

    public void render(Resolver.Resolution resolution) {
        render("Dependency tree:", null, new LinkedHashMap<>(Map.of("", resolution)), false, Map.of());
    }

    public void render(String title, SequencedMap<String, Resolver.Resolution> scopes) {
        if (merged) {
            render(title, null, scopes, true, Map.of());
        } else {
            scopes.forEach((scope, resolution) -> render(
                    title + "/" + scope,
                    null,
                    new LinkedHashMap<>(Map.of(scope, resolution)),
                    false,
                    Map.of()));
        }
    }

    public void render(SequencedMap<String, Resolver.Resolution> scopes, String key, Resolver.Vertex root) {
        render(scopes, key, root, Collections.emptyNavigableMap());
    }

    public void render(SequencedMap<String, Resolver.Resolution> scopes,
                       String key,
                       Resolver.Vertex root,
                       SequencedMap<String, String> layers) {
        String version = root.resolvedVersion(), coordinate = version == null ? key : key + "/" + version;
        SequencedMap<String, SequencedSet<String>> grafts = new LinkedHashMap<>(), attachments = new LinkedHashMap<>();
        if (merged) {
            layers.forEach((layer, declaring) -> scopes.forEach((scope, resolution) -> {
                for (Resolver.Edge edge : resolution.edges()) {
                    Resolver.Vertex node = resolution.vertices().get(vertexKey(edge));
                    if (!scope.equals(layer) && edge.followed() && node != null
                            && declaring.equals(vertexKey(edge) + "/" + node.resolvedVersion())) {
                        attachments.computeIfAbsent(layer, _ -> new LinkedHashSet<>()).add(edge.coordinate());
                        grafts.computeIfAbsent(edge.coordinate(), _ -> new LinkedHashSet<>()).add(layer);
                    }
                }
            }));
        }
        SequencedMap<String, Resolver.Resolution> rooted = new LinkedHashMap<>();
        scopes.forEach((scope, resolution) -> {
            if (resolution.edges().isEmpty()) {
                return;
            }
            SequencedSet<String> parents = attachments.getOrDefault(scope, new LinkedHashSet<>(List.of(coordinate)));
            List<Resolver.Edge> edges = new ArrayList<>();
            edges.add(new Resolver.Edge(null, coordinate, version, scope, true));
            for (Resolver.Edge edge : resolution.edges()) {
                if (edge.parent() != null) {
                    edges.add(edge);
                } else if (edge.followed()) {
                    parents.forEach(parent -> edges.add(
                            new Resolver.Edge(parent, edge.coordinate(), edge.version(), edge.scope(), true)));
                }
            }
            SequencedMap<String, Resolver.Vertex> nodes = new LinkedHashMap<>();
            nodes.put(key, root);
            nodes.putAll(resolution.vertices());
            rooted.put(scope, new Resolver.Resolution(resolution.artifacts(), edges, nodes));
        });
        if (merged) {
            render(null, key, rooted, true, grafts);
        } else {
            rooted.forEach((scope, resolution) -> render(
                    null,
                    key,
                    new LinkedHashMap<>(Map.of(scope, resolution)),
                    false,
                    Map.of()));
        }
    }

    private void render(String title,
                        String root,
                        SequencedMap<String, Resolver.Resolution> scopes,
                        boolean labelled,
                        Map<String, SequencedSet<String>> grafts) {
        SequencedMap<String, Resolver.Resolution> present = new LinkedHashMap<>();
        scopes.forEach((scope, resolution) -> {
            if (!resolution.edges().isEmpty()) {
                present.put(scope, resolution);
            }
        });
        if (present.isEmpty()) {
            return;
        }
        StringBuilder builder = new StringBuilder(System.lineSeparator());
        if (title != null) {
            builder.append(palette.heading()).append(title).append(palette.reset())
                    .append(System.lineSeparator());
        }
        builder.append(render(present, labelled, grafts));
        SequencedMap<Map.Entry<String, Resolver.Vertex>, SequencedSet<String>> resolved = new LinkedHashMap<>();
        present.forEach((scope, resolution) -> resolution.vertices().forEach((coordinate, node) -> {
            if (!coordinate.equals(root) && (internal || !node.internal())) {
                resolved.computeIfAbsent(Map.entry(coordinate, node), _ -> new LinkedHashSet<>()).add(scope);
            }
        }));
        if (!resolved.isEmpty()) {
            builder.append(System.lineSeparator())
                    .append(palette.heading()).append("Resolved dependencies:").append(palette.reset())
                    .append(System.lineSeparator());
            Set<String> external = new HashSet<>();
            resolved.entrySet().stream()
                    .sorted(Comparator.comparing(entry -> entry.getKey().getKey()))
                    .forEach(entry -> {
                        if (compact && !entry.getKey().getValue().internal()) {
                            external.add(entry.getKey().getKey());
                            return;
                        }
                        builder.append("  ")
                                .append(entry.getKey().getKey())
                                .append(paint(245, " -> " + entry.getKey().getValue().resolvedVersion()));
                        if (labelled) {
                            builder.append(' ').append(paint(67, "[" + String.join(", ", entry.getValue()) + "]"));
                        }
                        builder.append(System.lineSeparator());
                    });
            if (!external.isEmpty()) {
                builder.append("  ")
                        .append(paint(245, external.size() + " external " + (external.size() == 1 ? "dependency" : "dependencies")))
                        .append(System.lineSeparator());
            }
        }
        synchronized (out) {
            builder.toString().lines().forEach(out);
        }
    }

    public void summary(SequencedMap<String, Resolver.Vertex> vertices) {
        SequencedMap<String, Resolver.Vertex> nodes = new LinkedHashMap<>();
        vertices.forEach((coordinate, node) -> {
            if (internal || !node.internal()) {
                nodes.put(coordinate, node);
            }
        });
        if (nodes.isEmpty()) {
            return;
        }
        int total = nodes.size();
        SequencedMap<String, Integer> licenses = new LinkedHashMap<>();
        SequencedMap<String, String> categories = new LinkedHashMap<>();
        SequencedMap<String, Integer> permissiveness = new LinkedHashMap<>();
        Set<String> implied = new LinkedHashSet<>();
        int named = 0, automatic = 0, plain = 0, multiple = 0;
        for (Resolver.Vertex node : nodes.values()) {
            List<License> declared = node.licenses().stream()
                    .filter(entry -> entry.id() != null || entry.name() != null || entry.url() != null)
                    .toList();
            Set<String> labels = new LinkedHashSet<>();
            for (License entry : declared) {
                labels.add(entry.label());
            }
            implied.addAll(labels);
            if (labels.size() > 1) {
                multiple++;
            }
            License primary = declared.stream()
                    .max(Comparator.comparingInt(entry -> switch (entry.category()) {
                        case null -> 0;
                        case "public-domain" -> 6;
                        case "permissive" -> 5;
                        case "weak-copyleft" -> 4;
                        case "strong-copyleft" -> 3;
                        case "network-copyleft" -> 2;
                        default -> 1;
                    }))
                    .orElse(null);
            String label = primary == null ? "unknown" : primary.label();
            String category = primary == null ? null : primary.category();
            licenses.merge(label, 1, Integer::sum);
            categories.putIfAbsent(label, category);
            permissiveness.merge(category == null ? "unknown" : category, 1, Integer::sum);
            if (node.automatic()) {
                automatic++;
            } else if (node.module() != null) {
                named++;
            } else {
                plain++;
            }
        }
        int width = "non-modular".length();
        for (String label : licenses.keySet()) {
            width = Math.max(width, label.length());
        }
        for (String label : permissiveness.keySet()) {
            width = Math.max(width, label.length());
        }
        String tally = implied.size() + (implied.size() == 1 ? " license" : " licenses") + " implied";
        if (multiple > 0) {
            tally += ", " + multiple + (multiple == 1 ? " dependency offers" : " dependencies offer") + " multiple";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(System.lineSeparator())
                .append(palette.heading()).append("Licenses:").append(palette.reset())
                .append(System.lineSeparator())
                .append("  ").append(paint(245, tally)).append(System.lineSeparator());
        for (Map.Entry<String, Integer> entry : distribution(licenses)) {
            builder.append(row(entry.getKey(), entry.getValue(), total, width, categoryColor(categories.get(entry.getKey()))));
        }
        builder.append(System.lineSeparator())
                .append(palette.heading()).append("Permissiveness:").append(palette.reset())
                .append(System.lineSeparator());
        for (Map.Entry<String, Integer> entry : distribution(permissiveness)) {
            builder.append(row(entry.getKey(), entry.getValue(), total, width, categoryColor(entry.getKey())));
        }
        builder.append(System.lineSeparator())
                .append(palette.heading()).append("Modules:").append(palette.reset())
                .append(System.lineSeparator())
                .append(row("named", named, total, width, 71))
                .append(row("automatic", automatic, total, width, 214))
                .append(row("non-modular", plain, total, width, 245));
        synchronized (out) {
            builder.toString().lines().forEach(out);
        }
    }

    private static List<Map.Entry<String, Integer>> distribution(SequencedMap<String, Integer> counts) {
        return counts.entrySet().stream()
                .sorted(Comparator.comparingInt(Map.Entry<String, Integer>::getValue).reversed()
                        .thenComparing(Map.Entry::getKey))
                .toList();
    }

    private static int categoryColor(String category) {
        if (category == null) {
            return 245;
        }
        return switch (category) {
            case "permissive", "public-domain" -> 71;
            case "weak-copyleft" -> 214;
            case "strong-copyleft", "network-copyleft" -> 167;
            default -> 245;
        };
    }

    private String row(String label, int count, int total, int width, int color) {
        int bar = count == 0 ? 0 : Math.max(1, count * 20 / total);
        return "  " + paint(color, String.format(Locale.ROOT, "%-" + width + "s", label))
                + "  " + paint(245, String.format(Locale.ROOT, "%3d (%3d%%)", count, count * 100 / total))
                + "  " + paint(color, "█".repeat(bar))
                + System.lineSeparator();
    }

    private String render(SequencedMap<String, Resolver.Resolution> scopes,
                          boolean labelled,
                          Map<String, SequencedSet<String>> grafts) {
        SequencedMap<String, Map<String, List<Resolver.Edge>>> children = new LinkedHashMap<>();
        SequencedMap<String, List<Resolver.Edge>> roots = new LinkedHashMap<>();
        Map<String, Set<String>> seen = new HashMap<>();
        scopes.forEach((scope, resolution) -> {
            Map<String, List<Resolver.Edge>> byParent = new HashMap<>();
            List<Resolver.Edge> followed = new ArrayList<>();
            for (Resolver.Edge edge : resolution.edges()) {
                if (edge.parent() == null) {
                    if (edge.followed()) {
                        followed.add(edge);
                    }
                } else {
                    byParent.computeIfAbsent(edge.parent(), _ -> new ArrayList<>()).add(edge);
                }
            }
            children.put(scope, byParent);
            roots.put(scope, followed);
            seen.put(scope, new HashSet<>());
        });
        List<Map.Entry<Resolver.Edge, SequencedSet<String>>> merged = merge(roots, scopes, labelled);
        if (compact) {
            Map<Map.Entry<Resolver.Edge, SequencedSet<String>>, Integer> weight = new IdentityHashMap<>();
            for (Map.Entry<Resolver.Edge, SequencedSet<String>> root : merged) {
                weight.put(root, reachableInternal(root, children, scopes));
            }
            merged.sort(Comparator.comparingInt((Map.Entry<Resolver.Edge, SequencedSet<String>> root) -> weight.get(root))
                    .reversed()
                    .thenComparing(root -> root.getKey().coordinate()));
        }
        StringBuilder builder = new StringBuilder();
        int[] colorIndex = {0};
        Set<String> externalRoots = new LinkedHashSet<>();
        for (Map.Entry<Resolver.Edge, SequencedSet<String>> root : merged) {
            Resolver.Edge edge = root.getKey();
            SequencedSet<String> applied = root.getValue();
            if (compact && !isInternal(edge, scopes.get(applied.getFirst()).vertices())) {
                externalRoots.add(vertexKey(edge));
                continue;
            }
            if (compact) {
                applied = unseen(edge, applied, seen);
                if (applied.isEmpty()) {
                    continue;
                }
            }
            int treeColor = GRADIENT[colorIndex[0]++ % GRADIENT.length];
            builder.append(label(edge, applied, scopes, labelled, treeColor, true)).append(System.lineSeparator());
            children(builder, edge.coordinate(), applied, scopes, children, grafts, labelled, "", seen, treeColor);
        }
        if (!externalRoots.isEmpty()) {
            builder.append(externalSummary(externalRoots.size())).append(System.lineSeparator());
        }
        return builder.toString();
    }

    private void children(StringBuilder builder,
                          String coordinate,
                          SequencedSet<String> applied,
                          SequencedMap<String, Resolver.Resolution> scopes,
                          SequencedMap<String, Map<String, List<Resolver.Edge>>> children,
                          Map<String, SequencedSet<String>> grafts,
                          boolean labelled,
                          String indent,
                          Map<String, Set<String>> seen,
                          int treeColor) {
        SequencedMap<String, List<Resolver.Edge>> next = new LinkedHashMap<>();
        for (String scope : applied) {
            next.put(scope, children.get(scope).getOrDefault(coordinate, List.of()));
        }
        for (String scope : grafts.getOrDefault(coordinate, Collections.emptyNavigableSet())) {
            if (children.containsKey(scope)) {
                next.putIfAbsent(scope, children.get(scope).getOrDefault(coordinate, List.of()));
            }
        }
        List<Map.Entry<Resolver.Edge, SequencedSet<String>>> visible = new ArrayList<>();
        Set<String> external = new LinkedHashSet<>();
        if (compact) {
            List<Map.Entry<Resolver.Edge, SequencedSet<String>>> internal = new ArrayList<>();
            for (Map.Entry<Resolver.Edge, SequencedSet<String>> element : merge(next, scopes, labelled)) {
                if (isInternal(element.getKey(), scopes.get(element.getValue().getFirst()).vertices())) {
                    internal.add(element);
                } else {
                    external.add(vertexKey(element.getKey()));
                }
            }
            Map<Map.Entry<Resolver.Edge, SequencedSet<String>>, Integer> weight = new IdentityHashMap<>();
            for (Map.Entry<Resolver.Edge, SequencedSet<String>> element : internal) {
                weight.put(element, reachableInternal(element, children, scopes));
            }
            internal.sort(Comparator.comparingInt((Map.Entry<Resolver.Edge, SequencedSet<String>> element) -> weight.get(element))
                    .reversed()
                    .thenComparing(element -> element.getKey().coordinate()));
            for (Map.Entry<Resolver.Edge, SequencedSet<String>> element : internal) {
                SequencedSet<String> unseen = unseen(element.getKey(), element.getValue(), seen);
                if (!unseen.isEmpty()) {
                    visible.add(Map.entry(element.getKey(), unseen));
                }
            }
        } else {
            visible.addAll(merge(next, scopes, labelled));
        }
        for (int index = 0; index < visible.size(); index++) {
            boolean last = index == visible.size() - 1 && external.isEmpty();
            Resolver.Edge edge = visible.get(index).getKey();
            SequencedSet<String> scoped = visible.get(index).getValue();
            builder.append(paint(treeColor, indent + (last ? "└─ " : "├─ ")))
                    .append(label(edge, scoped, scopes, labelled, treeColor, false))
                    .append(System.lineSeparator());
            SequencedSet<String> expanded = compact ? scoped : edge.followed() ? unseen(edge, scoped, seen) : new LinkedHashSet<>();
            if (!expanded.isEmpty()) {
                children(builder,
                        edge.coordinate(),
                        expanded,
                        scopes,
                        children,
                        grafts,
                        labelled,
                        indent + (last ? "   " : "│  "),
                        seen,
                        treeColor);
            }
        }
        if (!external.isEmpty()) {
            builder.append(paint(treeColor, indent + "└─ "))
                    .append(externalSummary(external.size()))
                    .append(System.lineSeparator());
        }
    }

    private static List<Map.Entry<Resolver.Edge, SequencedSet<String>>> merge(SequencedMap<String, List<Resolver.Edge>> edges,
                                                                              SequencedMap<String, Resolver.Resolution> scopes,
                                                                              boolean labelled) {
        List<Map.Entry<Resolver.Edge, SequencedSet<String>>> merged = new ArrayList<>();
        List<List<Object>> identities = new ArrayList<>();
        edges.forEach((scope, list) -> {
            int position = 0;
            for (Resolver.Edge edge : list) {
                List<Object> identity = Arrays.asList(
                        edge.coordinate(),
                        edge.version(),
                        edge.followed(),
                        labelled ? null : edge.scope(),
                        scopes.get(scope).vertices().get(vertexKey(edge)));
                int index = identities.indexOf(identity);
                if (index < 0) {
                    index = position;
                    while (index < merged.size() && vertexKey(merged.get(index).getKey()).equals(vertexKey(edge))) {
                        index++;
                    }
                    identities.add(index, identity);
                    merged.add(index, Map.entry(edge, new LinkedHashSet<>()));
                }
                merged.get(index).getValue().add(scope);
                position = index + 1;
            }
        });
        return merged;
    }

    private static SequencedSet<String> unseen(Resolver.Edge edge, SequencedSet<String> applied, Map<String, Set<String>> seen) {
        SequencedSet<String> unseen = new LinkedHashSet<>();
        for (String scope : applied) {
            if (seen.get(scope).add(edge.coordinate())) {
                unseen.add(scope);
            }
        }
        return unseen;
    }

    private String externalSummary(int count) {
        return paint(245, count + " external " + (count == 1 ? "dependency" : "dependencies"));
    }

    private static String vertexKey(Resolver.Edge edge) {
        String coordinate = edge.coordinate(), version = edge.version();
        return version != null && !version.isEmpty() && coordinate.endsWith("/" + version)
                ? coordinate.substring(0, coordinate.length() - version.length() - 1)
                : coordinate;
    }

    private static boolean isInternal(Resolver.Edge edge, SequencedMap<String, Resolver.Vertex> nodes) {
        Resolver.Vertex node = nodes.get(vertexKey(edge));
        return node != null && node.internal();
    }

    private static int reachableInternal(Map.Entry<Resolver.Edge, SequencedSet<String>> element,
                                         SequencedMap<String, Map<String, List<Resolver.Edge>>> children,
                                         SequencedMap<String, Resolver.Resolution> scopes) {
        int weight = 0;
        for (String scope : element.getValue()) {
            weight = Math.max(weight, reachableInternal(
                    element.getKey().coordinate(),
                    children.get(scope),
                    scopes.get(scope).vertices(),
                    new HashSet<>()));
        }
        return weight;
    }

    private static int reachableInternal(String coordinate,
                                         Map<String, List<Resolver.Edge>> children,
                                         SequencedMap<String, Resolver.Vertex> nodes,
                                         Set<String> visited) {
        if (!visited.add(coordinate)) {
            return 0;
        }
        int count = 1;
        for (Resolver.Edge edge : children.getOrDefault(coordinate, List.of())) {
            if (isInternal(edge, nodes)) {
                count += reachableInternal(edge.coordinate(), children, nodes, visited);
            }
        }
        return count;
    }

    private String label(Resolver.Edge edge,
                         SequencedSet<String> applied,
                         SequencedMap<String, Resolver.Resolution> scopes,
                         boolean labelled,
                         int treeColor,
                         boolean root) {
        String coordinate = edge.coordinate(), version = edge.version(), key = coordinate, discovered = null;
        if (version != null && !version.isEmpty() && coordinate.endsWith("/" + version)) {
            key = coordinate.substring(0, coordinate.length() - version.length() - 1);
            discovered = version;
        }
        Resolver.Vertex node = edge.followed() ? scopes.get(applied.getFirst()).vertices().get(key) : null;
        StringBuilder line = new StringBuilder();
        if (!edge.followed()) {
            line.append(paint(240, key));
        } else if (root) {
            line.append(palette.bold(treeColor)).append(key).append(palette.reset());
        } else {
            line.append(key);
        }
        if (discovered != null) {
            line.append(' ').append(paint(245, discovered));
            String promoted = node == null ? null : node.resolvedVersion();
            if (promoted != null && !promoted.equals(discovered)) {
                line.append(paint(173, " -> " + promoted));
            }
        }
        String scope = labelled ? String.join(", ", applied) : edge.scope();
        if (scope != null) {
            line.append(' ').append(paint(67, "[" + scope + "]"));
        }
        if (node != null) {
            StringBuilder meta = new StringBuilder();
            if (node.module() != null) {
                meta.append("module ").append(node.module());
                if (node.automatic()) {
                    meta.append(", automatic");
                }
            } else if (node.automatic()) {
                meta.append("automatic module");
            }
            if (node.internal()) {
                String location = locations.get(key);
                meta.append(meta.isEmpty() ? "local" : ", local").append(location == null ? "" : " " + location);
            }
            if (!meta.isEmpty()) {
                line.append(' ').append(paint(node.internal() ? 84 : 109, "(" + meta + ")"));
            }
            String names = node.licenses().stream()
                    .map(license -> license.id() != null ? license.id()
                            : license.name() != null ? license.name() : license.url())
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining(", "));
            if (!names.isEmpty()) {
                line.append(' ').append(paint(142, "{" + names + "}"));
            }
        }
        if (!edge.followed()) {
            line.append(' ').append(paint(240, "(*)"));
        }
        return line.toString();
    }

    private String paint(int code, String text) {
        return palette.color(code) + text + palette.reset();
    }
}

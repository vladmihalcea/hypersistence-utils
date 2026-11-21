package build.jenesis.module;

import module java.base;
import build.jenesis.DependencyScope;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class ModularJarResolver implements Resolver {

    private final boolean resolveAutomaticModules;

    private final Resolver fallback;

    private final Supplier<ModuleVersionNegotiator> negotiatorSupplier;

    public ModularJarResolver(boolean resolveAutomaticModules) {
        this(resolveAutomaticModules, null);
    }

    public ModularJarResolver(boolean resolveAutomaticModules, Resolver fallback) {
        this(resolveAutomaticModules, fallback, ModuleVersionNegotiator.first());
    }

    public static ModularJarResolver ofEnvironment(Environment environment, boolean resolveAutomaticModules) {
        return ofEnvironment(environment, resolveAutomaticModules, null);
    }

    public static ModularJarResolver ofEnvironment(Environment environment,
                                                   boolean resolveAutomaticModules,
                                                   Resolver fallback) {
        String property = environment.getProperty("resolver.module");
        if (property == null) {
            return new ModularJarResolver(resolveAutomaticModules, fallback);
        }
        return new ModularJarResolver(resolveAutomaticModules, fallback, switch (property.toLowerCase(Locale.ROOT)) {
            case "first" -> ModuleVersionNegotiator.first();
            case "ignore" -> ModuleVersionNegotiator.ignore();
            case "fail" -> ModuleVersionNegotiator.fail();
            case "managed" -> ModuleVersionNegotiator.managed();
            default -> throw new IllegalArgumentException("Unknown jenesis.resolver.module '"
                    + property
                    + "', expected one of: first, ignore, fail, managed");
        });
    }

    public <S extends Supplier<ModuleVersionNegotiator> & Serializable> ModularJarResolver(
            boolean resolveAutomaticModules,
            Resolver fallback,
            S negotiatorSupplier) {
        this.resolveAutomaticModules = resolveAutomaticModules;
        this.fallback = fallback;
        this.negotiatorSupplier = negotiatorSupplier;
    }

    @Override
    public Resolver.Resolution dependencies(Executor executor,
                                            String prefix,
                                            Map<String, Repository> repositories,
                                            SequencedMap<String, SequencedSet<String>> coordinates,
                                            SequencedMap<String, String> versions,
                                            DependencyScope scope) throws IOException {
        coordinates.forEach((coordinate, exclusions) -> {
            if (!exclusions.isEmpty()) {
                throw new IllegalArgumentException("Cannot exclude "
                        + exclusions
                        + " from "
                        + coordinate
                        + ": a modular resolution reads no POM to exclude a dependency from");
            }
        });
        SequencedMap<String, Resolver.Resolved> dependencies = new LinkedHashMap<>();
        SequencedSet<String> resolved = new LinkedHashSet<>();
        SequencedSet<String> unresolved = new LinkedHashSet<>();
        SequencedMap<String, ModuleVersionNegotiator.CompiledVersion> propagated = new LinkedHashMap<>();
        ModuleVersionNegotiator negotiator = negotiatorSupplier.get();
        SequencedMap<String, String> hints = new LinkedHashMap<>(versions);
        List<Resolver.Edge> edges = new ArrayList<>();
        SequencedMap<String, Resolver.Vertex> nodes = new LinkedHashMap<>();
        Map<String, String> parents = new HashMap<>();
        Map<String, String> moduleCoordinates = new HashMap<>();
        SequencedSet<String> declaredModules = new LinkedHashSet<>();
        for (String coordinate : coordinates.sequencedKeySet()) {
            int split = coordinate.indexOf('/');
            declaredModules.add(split < 0 ? coordinate : coordinate.substring(0, split));
        }
        Queue<String> queue = new ArrayDeque<>(coordinates.sequencedKeySet());
        int runtime = Runtime.version().feature();
        while (!queue.isEmpty()) {
            String raw = queue.remove();
            int versionSplit = raw.indexOf('/');
            String current = versionSplit < 0 ? raw : raw.substring(0, versionSplit);
            String inlineVersion = versionSplit < 0 ? null : raw.substring(versionSplit + 1);
            if (resolved.contains(current) || unresolved.contains(current)) {
                continue;
            }
            String pinValue = versions.get(current);
            String pin, checksum;
            if (pinValue == null) {
                pin = null;
                checksum = null;
            } else {
                int split = pinValue.indexOf(' ');
                pin = split < 0 ? pinValue : pinValue.substring(0, split);
                checksum = split < 0 ? null : pinValue.substring(split + 1).trim();
            }
            ModuleVersionNegotiator.CompiledVersion compiled = propagated.get(current);
            String hint = compiled == null ? null : compiled.version();
            String requested = pin != null ? pin : (hint != null ? hint : inlineVersion);
            String classifier, expected;
            if (requested != null && requested.startsWith(":")) {
                int divider = requested.indexOf(':', 1);
                classifier = divider < 0 ? requested.substring(1) : requested.substring(1, divider);
                expected = divider < 0 ? null : requested.substring(divider + 1);
                if (classifier.isEmpty() || expected != null && expected.isEmpty()) {
                    throw new IllegalArgumentException("Malformed classifier '" + requested + "' for " + current
                            + ": expected :<classifier> or :<classifier>:<version>");
                }
            } else {
                classifier = null;
                expected = requested;
            }
            String identifier = classifier == null ? current : current + "-" + classifier;
            Repository repository = repositories.getOrDefault(Resolver.base(prefix), Repository.empty());
            RepositoryItem item = expected == null
                    ? repository.fetch(executor, identifier).orElse(null)
                    : repository.fetch(executor, identifier + "/" + expected).orElse(null);
            if (item == null) {
                if (fallback == null) {
                    throw new IllegalArgumentException("No module found for "
                            + current
                            + aliased(current, parents, moduleCoordinates, dependencies));
                }
                unresolved.add(current);
                if (requested != null) {
                    hints.putIfAbsent(current, checksum == null ? requested : requested + " " + checksum);
                }
            } else {
                Path file = item.file().orElse(null);
                ModuleDescriptor descriptor;
                if (file == null) {
                    NavigableMap<Integer, byte[]> candidates = new TreeMap<>();
                    try (ZipInputStream inputStream = new ZipInputStream(item.toInputStream())) {
                        ZipEntry entry;
                        while ((entry = inputStream.getNextEntry()) != null) {
                            String name = entry.getName();
                            int version;
                            if (name.equals("module-info.class")) {
                                version = 0;
                            } else if (name.startsWith("META-INF/versions/")
                                    && name.endsWith("/module-info.class")) {
                                String segment = name.substring(
                                        "META-INF/versions/".length(),
                                        name.length() - "/module-info.class".length());
                                try {
                                    version = Integer.parseInt(segment);
                                } catch (NumberFormatException _) {
                                    continue;
                                }
                                if (version > runtime) {
                                    continue;
                                }
                            } else {
                                continue;
                            }
                            candidates.put(version, inputStream.readAllBytes());
                        }
                    }
                    Map.Entry<Integer, byte[]> selected = candidates.lastEntry();
                    descriptor = selected == null
                            ? ModuleDescriptor.newAutomaticModule(current).build()
                            : ModuleDescriptor.read(ByteBuffer.wrap(selected.getValue()));
                } else {
                    descriptor = ModuleFinder.of(file).findAll().stream()
                            .findFirst()
                            .map(ModuleReference::descriptor)
                            .orElseGet(() -> ModuleDescriptor.newAutomaticModule(current).build());
                }
                if (descriptor.isAutomatic()) {
                    if (fallback != null) {
                        unresolved.add(current);
                        if (requested != null) {
                            hints.putIfAbsent(current, checksum == null ? requested : requested + " " + checksum);
                        }
                        continue;
                    }
                    if (resolveAutomaticModules) {
                        continue;
                    }
                    throw new IllegalArgumentException("Cannot resolve automatic module " + current
                            + " without a fallback resolver: its dependencies are not declared as modules");
                }
                if (!descriptor.name().equals(current)) {
                    throw new IllegalArgumentException(
                            "Expected module " + current + " but jar declares " + descriptor.name());
                }
                String declared = descriptor.rawVersion().orElse(null);
                if (declared != null && (declared.isEmpty() || declared.equals("..")
                        || declared.indexOf('/') >= 0 || declared.indexOf('\\') >= 0 || declared.startsWith(":"))) {
                    throw new IllegalArgumentException(
                            "Module " + current + " declares an unsafe version '" + declared + "'");
                }
                if (!resolveAutomaticModules && declared != null && expected != null && !declared.equals(expected)) {
                    throw new IllegalArgumentException(
                            "Expected version " + expected + " for " + current + " but jar declares " + declared);
                }
                String version = expected != null ? expected : declared;
                String currentCoordinate = prefix + "/" + identifier + (version == null ? "" : "/" + version);
                Path jar = item.file().orElseThrow(() -> new IllegalStateException(
                        "Repository did not materialize a file for " + current));
                if (checksum != null && !checksum.isEmpty()) {
                    Resolver.validate(jar, checksum, currentCoordinate);
                }
                dependencies.put(currentCoordinate, new Resolver.Resolved(jar, checksum == null ? "" : checksum, item.internal()));
                List<License> licenses;
                try (FileSystem archive = Files.isDirectory(jar) ? null : FileSystems.newFileSystem(jar)) {
                    licenses = License.ofArchive(archive == null ? jar : archive.getPath("/"));
                }
                if (!declaredModules.contains(current)) {
                    negotiator.discovered(current, version, pin != null);
                }
                resolved.add(current);
                moduleCoordinates.put(current, currentCoordinate);
                nodes.put(prefix + "/" + current, new Resolver.Vertex(version, descriptor.name(), descriptor.isAutomatic(), item.internal(), licenses));
                String parent = parents.get(current);
                edges.add(new Resolver.Edge(
                        parent == null ? null : moduleCoordinates.get(parent),
                        currentCoordinate,
                        version,
                        null,
                        true));
                descriptor.requires().stream()
                        .filter(requires -> !requires.accessFlags().contains(AccessFlag.STATIC_PHASE)
                                || scope == DependencyScope.COMPILE && requires.accessFlags().contains(AccessFlag.TRANSITIVE))
                        .filter(requires -> !ModuleGraph.isSystemModule(requires.name()))
                        .sorted(Comparator.comparing(ModuleDescriptor.Requires::name))
                        .forEach(requires -> {
                            String name = requires.name();
                            requires.rawCompiledVersion().ifPresent(v -> {
                                if (v.isEmpty() || v.equals("..") || v.indexOf('/') >= 0 || v.indexOf('\\') >= 0
                                        || v.startsWith(":")) {
                                    throw new IllegalArgumentException("Module " + current
                                            + " declares an unsafe compiled version '" + v + "' for " + name);
                                }
                                if (versions.get(name) != null) {
                                    return;
                                }
                                ModuleVersionNegotiator.CompiledVersion kept = negotiator.negotiate(name,
                                        propagated.get(name),
                                        new ModuleVersionNegotiator.CompiledVersion(v, current));
                                if (kept == null) {
                                    propagated.remove(name);
                                } else {
                                    propagated.put(name, kept);
                                }
                            });
                            parents.putIfAbsent(name, current);
                            if (!unresolved.contains(name) && !resolved.contains(name)) {
                                queue.add(name);
                            } else if (resolved.contains(name)) {
                                edges.add(new Resolver.Edge(
                                        currentCoordinate,
                                        moduleCoordinates.get(name),
                                        null,
                                        null,
                                        false));
                            }
                        });
            }
        }
        if (!unresolved.isEmpty()) {
            SequencedMap<String, SequencedSet<String>> unresolvedCoordinates = new LinkedHashMap<>();
            for (String coordinate : unresolved) {
                unresolvedCoordinates.put(coordinate, Collections.emptyNavigableSet());
            }
            Resolver.Resolution fallbackResolution = fallback.dependencies(
                    executor, prefix, repositories, unresolvedCoordinates, hints, scope);
            fallbackResolution.artifacts().forEach(dependencies::putIfAbsent);
            edges.addAll(fallbackResolution.edges());
            fallbackResolution.vertices().forEach(nodes::putIfAbsent);
        }
        return new Resolver.Resolution(dependencies, edges, nodes);
    }

    private static String aliased(String module,
                                  Map<String, String> parents,
                                  Map<String, String> moduleCoordinates,
                                  SequencedMap<String, Resolver.Resolved> dependencies) {
        String parent = parents.get(module);
        Resolver.Resolved resolved = parent == null ? null : dependencies.get(moduleCoordinates.get(parent));
        String target;
        try {
            target = resolved == null ? null : PathPlacement.aliases(resolved.file()).get(module);
        } catch (IOException | RuntimeException _) {
            target = null;
        }
        return target == null
                ? ""
                : " - "
                + parent
                + " aliases it to "
                + target
                + ", which pure module resolution cannot provide (use a Maven-backed layout)";
    }

}

package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.BuildExecutor;
import build.jenesis.DependencyScope;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.ModuleGraph;
import build.jenesis.Palette;
import build.jenesis.PathPlacement;
import build.jenesis.Platform;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;

public class MavenPomResolver implements MavenResolver {

    private static final String NAMESPACE_4_0_0 = "http://maven.apache.org/POM/4.0.0",
            NAMESPACE_4_1_0 = "http://maven.apache.org/POM/4.1.0";
    private static final Set<String> IMPLICITS = Set.of("groupId", "artifactId", "version", "packaging");
    private static final Set<String> ACTIVATIONS = Set.of("jdk", "os", "activeByDefault");
    private static final Set<String> PROCESSORS = Set.of("processor", "classpath-processor", "modular-processor");
    private static final Pattern PROPERTY = Pattern.compile("(\\$\\{([^}]+)})");
    private static final Pattern COORDINATE = Pattern.compile("[A-Za-z0-9_.:+~@*/-]+");
    private static final Pattern JDK_RANGE = Pattern.compile(
            "\\s*([\\[(])\\s*([0-9]{1,8}(?:\\.[0-9]{1,8})*)?\\s*(,)?\\s*([0-9]{1,8}(?:\\.[0-9]{1,8})*)?\\s*([\\])])\\s*(,|$)");
    private static final Pattern JDK_UNCLOSED = Pattern.compile(".*[\\[(]\\s*[0-9.]*\\s*,\\s*");
    private static final Pattern JDK_BOUND = Pattern.compile(".*[\\[(]\\s*[0-9.]+\\s*");
    public static final String CHECKSUM_PREFIX = "Checksum/";

    private final Supplier<MavenVersionNegotiator> negotiatorSupplier;
    private final String jdk, osName, osArch, osVersion;
    private final transient Consumer<String> printing;
    private final transient Palette palette;
    private final transient DocumentBuilderFactory factory = MavenDefaultVersionNegotiator.toDocumentBuilderFactory();

    public MavenPomResolver() {
        this(MavenDefaultVersionNegotiator.maven());
    }

    public static MavenPomResolver ofEnvironment(Environment environment) {
        String property = environment.value("resolver.maven", "maven");
        return new MavenPomResolver(switch (property.toLowerCase(Locale.ROOT)) {
            case "maven" -> MavenDefaultVersionNegotiator.maven();
            case "latest" -> MavenDefaultVersionNegotiator.latest();
            case "release" -> MavenDefaultVersionNegotiator.release();
            case "stable" -> MavenDefaultVersionNegotiator.stable();
            case "closest" -> MavenDefaultVersionNegotiator.closest();
            case "fail" -> MavenDefaultVersionNegotiator.fail();
            case "managed" -> MavenDefaultVersionNegotiator.managed();
            default -> throw new IllegalArgumentException("Unknown jenesis.resolver.maven '"
                    + property
                    + "', expected one of: maven, latest, release, stable, closest, fail, managed");
        }).printing(environment.flag("print.progress", true) ? environment.out() : null,
                Palette.ofEnvironment(environment));
    }

    public <S extends Supplier<MavenVersionNegotiator> & Serializable> MavenPomResolver(S negotiatorSupplier) {
        this(negotiatorSupplier,
                System.getProperty("java.version"),
                System.getProperty("os.name"),
                System.getProperty("os.arch"),
                System.getProperty("os.version"),
                null,
                Palette.NONE);
    }

    private MavenPomResolver(Supplier<MavenVersionNegotiator> negotiatorSupplier,
                             String jdk,
                             String osName,
                             String osArch,
                             String osVersion,
                             Consumer<String> printing,
                             Palette palette) {
        this.negotiatorSupplier = negotiatorSupplier;
        this.jdk = jdk;
        this.osName = osName.toLowerCase(Locale.ENGLISH);
        this.osArch = osArch.toLowerCase(Locale.ENGLISH);
        this.osVersion = osVersion.toLowerCase(Locale.ENGLISH);
        this.printing = printing;
        this.palette = palette;
    }

    public MavenPomResolver jdk(String jdk) {
        return new MavenPomResolver(negotiatorSupplier, jdk, osName, osArch, osVersion, printing, palette);
    }

    public MavenPomResolver os(String osName, String osArch, String osVersion) {
        return new MavenPomResolver(negotiatorSupplier, jdk, osName, osArch, osVersion, printing, palette);
    }

    public MavenPomResolver printing(Consumer<String> printing, Palette palette) {
        Set<String> printed = ConcurrentHashMap.newKeySet();
        return new MavenPomResolver(negotiatorSupplier,
                jdk,
                osName,
                osArch,
                osVersion,
                printing == null ? null : line -> {
                    if (printed.add(line)) {
                        printing.accept(line);
                    }
                },
                palette);
    }

    @Override
    public Resolver.Resolution dependencies(Executor executor,
                                            String prefix,
                                            Map<String, Repository> repositories,
                                            SequencedMap<String, SequencedSet<String>> coordinates,
                                            SequencedMap<String, String> versions,
                                            DependencyScope scope) throws IOException {
        return dependencies(executor, prefix, repositories, coordinates, versions, new LinkedHashMap<>(), scope);
    }

    @Override
    public Resolver.Resolution dependencies(Executor executor,
                                            String prefix,
                                            Map<String, Repository> repositories,
                                            SequencedMap<String, SequencedSet<String>> coordinates,
                                            SequencedMap<String, String> versions,
                                            SequencedMap<String, SequencedSet<String>> managedExclusions,
                                            DependencyScope scope) throws IOException {
        Map<MavenDependencyKey, MavenDependencyValue> managedDependencies = new LinkedHashMap<>();
        versions.forEach((coordinate, value) -> {
            MavenDependencyKey key = MavenDependencyKey.parseKey(coordinate);
            int split = value.indexOf(' ');
            String version = split < 0 ? value : value.substring(0, split);
            if (version.startsWith(":")) {
                throw new IllegalArgumentException("Module classifiers are not supported when resolving "
                        + coordinate + " through Maven: " + version);
            }
            String checksum = split < 0 ? null : value.substring(split + 1).trim();
            managedDependencies.put(key, new MavenDependencyValue(
                    version, null, null, null, null, checksum));
        });
        managedExclusions.forEach((coordinate, excludes) -> {
            List<MavenDependencyName> exclusions = excludes.stream()
                    .map(entry -> {
                        int separator = entry.indexOf('/');
                        if (separator < 1 || separator == entry.length() - 1) {
                            throw new IllegalArgumentException("Malformed managed exclusion '" + entry + "' for "
                                    + coordinate + ": expected <groupId>/<artifactId>");
                        }
                        return new MavenDependencyName(entry.substring(0, separator), entry.substring(separator + 1));
                    })
                    .toList();
            managedDependencies.merge(MavenDependencyKey.parseKey(coordinate),
                    new MavenDependencyValue(null, null, null, exclusions, null, null),
                    MavenPomResolver::merge);
        });
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = new LinkedHashMap<>();
        coordinates.forEach((coordinate, excludes) -> {
            List<MavenDependencyName> exclusions = excludes.isEmpty()
                    ? null
                    : excludes.stream()
                            .map(entry -> {
                                int separator = entry.indexOf('/');
                                return new MavenDependencyName(
                                        entry.substring(0, separator), entry.substring(separator + 1));
                            })
                            .collect(Collectors.toList());
            MavenDependencyKey.Versioned parsed = MavenDependencyKey.tryParse(coordinate);
            MavenDependencyValue managed = managedDependencies.get(parsed.key());
            if (managed == null && (parsed.key().classifier() != null || !parsed.key().type().equals("jar"))) {
                MavenDependencyValue artifact = managedDependencies.get(new MavenDependencyKey(parsed.key().groupId(),
                        parsed.key().artifactId(),
                        "jar",
                        null));
                if (artifact != null && artifact.version() != null) {
                    managed = new MavenDependencyValue(artifact.version(), MavenDependencyScope.COMPILE, null, null, null);
                }
            }
            String declared = parsed.version();
            if (declared != null && !isFloating(declared)) {
                String checksum = null;
                if (managed != null && managed.checksum() != null) {
                    if (!declared.equals(managed.version())) {
                        throw new IllegalStateException("Inline version "
                                + declared
                                + " for "
                                + coordinate
                                + " conflicts with managed version "
                                + managed.version());
                    }
                    checksum = managed.checksum();
                }
                dependencies.put(parsed.key(),
                        new MavenDependencyValue(declared, MavenDependencyScope.COMPILE, null, exclusions, null, checksum));
            } else if (managed != null) {
                dependencies.put(parsed.key(), new MavenDependencyValue(
                        managed.version(), MavenDependencyScope.COMPILE, null, exclusions, null, managed.checksum()));
            } else if (declared != null) {
                dependencies.put(parsed.key(),
                        new MavenDependencyValue(declared, MavenDependencyScope.COMPILE, null, exclusions, null));
            } else {
                throw new IllegalStateException(
                        "No version pinned for " + coordinate + " (add to dependencyManagement)");
            }
        });
        Traversal traversal = dependencies(executor,
                MavenRepository.of(repositories.getOrDefault(Resolver.base(prefix), Repository.empty())),
                new ContextualPom(new ResolvedPom(managedDependencies, dependencies, List.of(), null), true, null, Set.of(), null, null),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                prefix);
        SequencedMap<String, String> resolved = new LinkedHashMap<>();
        traversal.dependencies().forEach((key, value) -> {
            if (key.jar() || dependencies.containsKey(key) && !key.type().equals("pom")) {
                resolved.put(key.coordinate(prefix, value.version()), value.checksum() == null ? "" : value.checksum());
            }
        });
        SequencedMap<String, Resolver.Resolved> artifacts = Resolver.materializeAll(executor, repositories, prefix, resolved);
        Map<String, ModuleDescriptor> descriptors = new ConcurrentHashMap<>();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        traversal.dependencies().forEach((key, value) -> {
            String withVersion = key.coordinate(prefix, value.version());
            Resolver.Resolved artifact = artifacts.get(withVersion);
            if (artifact != null) {
                pending.add(CompletableFuture.runAsync(() -> {
                    ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(artifact.file());
                    if (descriptor != null) {
                        descriptors.put(withVersion, descriptor);
                    }
                }, executor));
            }
        });
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
        SequencedMap<String, Resolver.Vertex> nodes = new LinkedHashMap<>();
        traversal.dependencies().forEach((key, value) -> {
            String withVersion = key.coordinate(prefix, value.version());
            ModuleDescriptor descriptor = descriptors.get(withVersion);
            Resolver.Resolved artifact = artifacts.get(withVersion);
            nodes.put(key.coordinate(prefix, null), new Resolver.Vertex(
                    value.version(),
                    descriptor == null ? null : descriptor.name(),
                    descriptor != null && descriptor.isAutomatic(),
                    artifact != null && artifact.internal(),
                    traversal.licenses().getOrDefault(withVersion, List.of())));
        });
        return new Resolver.Resolution(artifacts, traversal.edges(), nodes);
    }

    public SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies(
            Executor executor,
            MavenRepository repository,
            Map<MavenDependencyKey, MavenDependencyValue> managedDependencies,
            SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies) throws IOException {
        return dependencies(executor,
                repository,
                new ContextualPom(new ResolvedPom(managedDependencies, dependencies, List.of(), null),
                        true,
                        null,
                        Set.of(),
                        null,
                        null),
                new ConcurrentHashMap<>(),
                new ConcurrentHashMap<>(),
                null).dependencies();
    }

    @Override
    public Resolver.Bom bom(Executor executor,
                            String prefix,
                            Map<String, Repository> repositories,
                            String coordinate,
                            String version,
                            String checksum,
                            boolean latest) throws IOException {
        if (checksum != null && !checksum.isEmpty()) {
            throw new IllegalArgumentException("A Maven BOM cannot carry a checksum: " + coordinate);
        }
        int slash = coordinate.indexOf('/');
        if (slash < 1 || slash == coordinate.length() - 1 || coordinate.indexOf('/', slash + 1) >= 0) {
            throw new IllegalArgumentException("Malformed Maven BOM coordinate '"
                    + coordinate
                    + "': expected <groupId>/<artifactId>");
        }
        String groupId = coordinate.substring(0, slash), artifactId = coordinate.substring(slash + 1);
        MavenRepository repository = MavenRepository.of(
                repositories.getOrDefault(Resolver.base(prefix), Repository.empty()));
        String resolved = version.isEmpty() || latest
                ? negotiatorSupplier.get().resolve(executor, repository, groupId, artifactId, "pom", null, "RELEASE")
                : isFloating(version)
                        ? negotiatorSupplier.get().resolve(executor, repository, groupId, artifactId, "pom", null, version)
                        : version;
        RepositoryItem item = repository.fetch(executor, groupId, artifactId, resolved, "pom", null, null)
                .orElseThrow(() -> new IllegalStateException("Unresolved: " + coordinate + "/" + resolved));
        Map<DependencyCoordinate, UnresolvedPom> unresolved = new HashMap<>();
        UnresolvedPom assembled;
        try (InputStream stream = item.toInputStream()) {
            assembled = assemble(executor, repository, stream, false, false, null, null, new HashSet<>(), unresolved);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Failed to parse BOM " + coordinate + "/" + resolved, e);
        }
        SequencedMap<String, String> entries = new TreeMap<>();
        resolve(executor, repository, assembled, unresolved).managedDependencies().forEach((key, value) -> {
            if (value.version() != null && !value.version().isEmpty()) {
                entries.put(prefix + "/" + key.coordinate(null, null), value.version());
            }
        });
        return new Resolver.Bom(item.file().orElse(null), item.internal(), false, resolved, entries);
    }

    @Override
    public MavenResolver.Closure dependencies(
            Executor executor,
            MavenRepository repository,
            List<RootPom> rootPoms,
            Map<MavenDependencyKey, MavenDependencyValue> managedCoordinates,
            MavenDependencyScope scope,
            String prefix) throws IOException {
        Map<DependencyCoordinate, UnresolvedPom> unresolved = new ConcurrentHashMap<>();
        Map<DependencyCoordinate, ResolvedPom> resolved = new ConcurrentHashMap<>();
        Map<MavenDependencyKey, MavenDependencyValue> managedDependencies = new LinkedHashMap<>(managedCoordinates);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = new LinkedHashMap<>();
        SequencedMap<String, MavenDependencyKey> roots = new LinkedHashMap<>();
        for (RootPom rootPom : rootPoms) {
            UnresolvedPom assembled;
            try (InputStream stream = rootPom.pom()) {
                assembled = assemble(executor, repository, stream, false, true, null, null, new HashSet<>(), unresolved);
            } catch (SAXException | ParserConfigurationException e) {
                throw new IllegalStateException("Failed to parse provided root POM", e);
            }
            ResolvedPom resolvedRoot = resolve(executor, repository, assembled, unresolved);
            String groupId = property(assembled.groupId(), assembled.properties());
            String artifactId = property(assembled.artifactId(), assembled.properties());
            String version = property(assembled.version(), assembled.properties());
            DependencyCoordinate coordinate = new DependencyCoordinate(groupId, artifactId, version);
            unresolved.putIfAbsent(coordinate, assembled);
            resolved.putIfAbsent(coordinate, resolvedRoot);
            MavenDependencyKey key = new MavenDependencyKey(groupId, artifactId, "jar", null);
            MavenDependencyValue value = new MavenDependencyValue(
                    version, scope, null, rootPom.exclusions(), null, rootPom.checksum());
            MavenDependencyValue pinned = managedCoordinates.get(key);
            if (pinned != null) {
                value = rootPom.versionPinned() ? reconcile(key, pinned, value) : merge(pinned, value);
            }
            dependencies.put(key, value);
            if (rootPom.identifier() != null) {
                roots.put(rootPom.identifier(), key);
            }
        }
        Traversal traversal = dependencies(executor,
                repository,
                new ContextualPom(new ResolvedPom(managedDependencies, dependencies, List.of(), null), true, scope, Set.of(), null, null),
                unresolved,
                resolved,
                prefix);
        return new MavenResolver.Closure(traversal.dependencies(), roots, traversal.edges(), traversal.licenses());
    }

    public SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies(Executor executor,
                                                                               MavenRepository repository,
                                                                               String groupId,
                                                                               String artifactId,
                                                                               String version,
                                                                               MavenDependencyScope scope)
            throws IOException {
        Map<DependencyCoordinate, UnresolvedPom> unresolved = new ConcurrentHashMap<>();
        Map<DependencyCoordinate, ResolvedPom> resolved = new ConcurrentHashMap<>();
        return dependencies(executor,
                repository,
                new ContextualPom(resolveOrCached(executor,
                        repository,
                        groupId,
                        artifactId,
                        version,
                        resolved,
                        unresolved), true, scope, Set.of(), null, null),
                unresolved,
                resolved,
                null).dependencies();
    }

    private Traversal dependencies(
            Executor executor,
            MavenRepository repository,
            ContextualPom initial,
            Map<DependencyCoordinate, UnresolvedPom> unresolved,
            Map<DependencyCoordinate, ResolvedPom> resolved,
            String prefix) throws IOException {
        Map<MavenDependencyKey, DependencyResolution> resolutions = new HashMap<>();
        SequencedSet<MavenDependencyKey> dependencies = new LinkedHashSet<>(), conflicts;
        MavenVersionNegotiator negotiator = negotiatorSupplier.get();
        List<Resolver.Edge> edges = new ArrayList<>();
        do {
            dependencies.clear();
            edges.clear();
            conflicts = traverse(executor,
                    repository,
                    negotiator,
                    resolved,
                    unresolved,
                    resolutions,
                    initial.pom().managedDependencies(),
                    dependencies,
                    initial,
                    prefix,
                    edges);
            Iterator<MavenDependencyKey> it = conflicts.iterator();
            while (it.hasNext()) {
                MavenDependencyKey key = it.next();
                DependencyResolution resolution = resolutions.get(key);
                boolean converged = true;
                if (resolution.widestScope != resolution.currentScope) {
                    resolution.currentScope = resolution.widestScope;
                    converged = false;
                }
                if (resolution.observedVersions.size() > 1) {
                    String candidate = negotiator.resolve(executor,
                            repository,
                            key.groupId(),
                            key.artifactId(),
                            key.type(),
                            key.classifier(),
                            resolution.currentVersion,
                            resolution.observedVersions);
                    if (!resolution.currentVersion.equals(candidate)) {
                        resolution.currentVersion = candidate;
                        converged = false;
                    }
                }
                if (converged) {
                    it.remove();
                }
            }
        } while (!conflicts.isEmpty());
        for (MavenDependencyKey key : dependencies) {
            if (initial.pom().dependencies().containsKey(key)) {
                continue;
            }
            negotiator.discovered(key.groupId(),
                    key.artifactId(),
                    key.type(),
                    key.classifier(),
                    resolutions.get(key).currentVersion,
                    initial.pom().managedDependencies().containsKey(key)
                            && initial.pom().managedDependencies().get(key).version() != null);
        }
        SequencedMap<MavenDependencyKey, MavenDependencyValue> results = new LinkedHashMap<>();
        dependencies.forEach(key -> {
            DependencyResolution resolution = resolutions.get(key);
            results.put(key, new MavenDependencyValue(resolution.currentVersion,
                    resolution.widestScope,
                    resolution.systemPath,
                    resolution.exclusions,
                    resolution.optional,
                    resolution.selectChecksum()));
        });
        SequencedMap<String, List<License>> licenses = new LinkedHashMap<>();
        results.forEach((key, value) -> {
            ResolvedPom pom = resolved.get(new DependencyCoordinate(key.groupId(), key.artifactId(), value.version()));
            if (pom != null && !pom.licenses().isEmpty()) {
                licenses.put(key.coordinate(prefix, value.version()), pom.licenses());
            }
        });
        return new Traversal(results, edges, licenses);
    }

    private SequencedSet<MavenDependencyKey> traverse(Executor executor,
                                                      MavenRepository repository,
                                                      MavenVersionNegotiator negotiator,
                                                      Map<DependencyCoordinate, ResolvedPom> resolved,
                                                      Map<DependencyCoordinate, UnresolvedPom> unresolved,
                                                      Map<MavenDependencyKey, DependencyResolution> resolutions,
                                                      Map<MavenDependencyKey, MavenDependencyValue> managedDependencies,
                                                      SequencedSet<MavenDependencyKey> dependencies,
                                                      ContextualPom current,
                                                      String prefix,
                                                      List<Resolver.Edge> edges) throws IOException {
        SequencedSet<MavenDependencyKey> conflicting = new LinkedHashSet<>();
        Queue<PendingPom> queue = new ArrayDeque<>();
        do {
            DependencyCoordinate relocation = current.pom().relocation();
            if (relocation != null && current.origin() != null) {
                MavenDependencyKey origin = current.origin(), target = new MavenDependencyKey(
                        relocation.groupId() == null ? origin.groupId() : relocation.groupId(),
                        relocation.artifactId() == null ? origin.artifactId() : relocation.artifactId(),
                        origin.type(),
                        origin.classifier());
                String version = relocation.version() == null
                        ? resolutions.get(origin).currentVersion
                        : relocation.version();
                if (!target.equals(origin)) {
                    dependencies.remove(origin);
                    if (printing != null) {
                        printing.accept("%s%-11s%s %s:%s:%s is relocated to %s:%s:%s, which is resolved in its place"
                                .formatted(palette.warning(),
                                        "[RELOCATED]",
                                        palette.reset(),
                                        origin.groupId(),
                                        origin.artifactId(),
                                        resolutions.get(origin).currentVersion,
                                        target.groupId(),
                                        target.artifactId(),
                                        version));
                    }
                    SequencedMap<MavenDependencyKey, MavenDependencyValue> relocated = new LinkedHashMap<>();
                    relocated.put(target, new MavenDependencyValue(version, MavenDependencyScope.COMPILE, null, null, null));
                    current = new ContextualPom(new ResolvedPom(Map.of(), relocated, List.of(), null),
                            false,
                            current.scope(),
                            current.exclusions(),
                            origin,
                            current.originVersion());
                }
            }
            for (Map.Entry<MavenDependencyKey, MavenDependencyValue> entry : current.pom().dependencies().entrySet()) {
                if (current.exclusions().contains(MavenDependencyName.EXCLUDE_ALL)) {
                    break;
                } else if (current.exclusions().contains(new MavenDependencyName(entry.getKey().groupId(), entry.getKey().artifactId()))
                        || current.exclusions().contains(new MavenDependencyName(entry.getKey().groupId(), "*"))
                        || current.exclusions().contains(new MavenDependencyName("*", entry.getKey().artifactId()))
                        || PROCESSORS.contains(entry.getKey().type())) {
                    continue;
                }
                MavenDependencyValue override = managedDependencies.get(entry.getKey()), value;
                if (current.root()) {
                    value = merge(entry.getValue(), override);
                } else {
                    MavenDependencyValue declared = merge(entry.getValue(), current.pom().managedDependencies().get(entry.getKey()));
                    value = override == null ? declared : merge(override, declared);
                    if (override != null && override.exclusions() != null && declared.exclusions() != null) {
                        value = value.exclusions(Stream.concat(declared.exclusions().stream(),
                                override.exclusions().stream()).distinct().toList());
                    }
                }
                value = defaultScope(value);
                if (!current.root() && Objects.equals(Boolean.TRUE, value.optional())) {
                    continue;
                }
                MavenDependencyScope resolvedScope = switch (current.scope()) {
                    case null -> value.scope();
                    case COMPILE -> switch (value.scope()) {
                        case COMPILE, RUNTIME -> value.scope();
                        default -> null;
                    };
                    case PROVIDED, RUNTIME, TEST -> switch (value.scope()) {
                        case COMPILE, RUNTIME -> current.scope();
                        default -> null;
                    };
                    case SYSTEM, IMPORT -> null;
                };
                if (resolvedScope == null) {
                    continue;
                }
                MavenDependencyKey dependencyKey = entry.getKey();
                String undefined = Stream.of(dependencyKey.groupId(),
                                dependencyKey.artifactId(),
                                dependencyKey.type(),
                                dependencyKey.classifier(),
                                value.version())
                        .filter(Objects::nonNull)
                        .map(PROPERTY::matcher)
                        .filter(Matcher::find)
                        .map(matcher -> matcher.group(2))
                        .findFirst()
                        .orElse(null);
                if (undefined != null) {
                    MavenDependencyKey origin = current.origin();
                    throw new IllegalStateException("The dependency " + dependencyKey.groupId() + ":"
                            + dependencyKey.artifactId()
                            + (dependencyKey.classifier() == null ? "" : ":" + dependencyKey.classifier())
                            + ":" + value.version() + (origin == null
                                    ? ""
                                    : " of " + origin.groupId() + ":" + origin.artifactId() + ":" + current.originVersion())
                            + " names the property " + undefined + ", which neither the declaring POM, its parents"
                            + " nor a profile this build activates defines - a profile is activated by <jdk>, <os>"
                            + " or <activeByDefault> alone, and a property of the JVM or of the environment is never read"
                            + (origin == null ? "" : ": exclude " + dependencyKey.groupId() + ":"
                                    + dependencyKey.artifactId() + " from " + origin.groupId() + ":"
                                    + origin.artifactId() + " and declare the artifact it stands for yourself"));
                }
                DependencyResolution resolution = resolutions.computeIfAbsent(
                        entry.getKey(),
                        _ -> new DependencyResolution());
                MavenDependencyScope scope = resolution.currentScope == null || resolution.currentScope.reduces(resolvedScope)
                        ? resolvedScope
                        : resolution.currentScope;
                String version;
                resolution.bindChecksum(entry.getKey(), value.version(), value.checksum());
                if (resolution.currentVersion == null) {
                    version = resolution.currentVersion = negotiator.resolve(executor,
                            repository,
                            entry.getKey().groupId(),
                            entry.getKey().artifactId(),
                            entry.getKey().type(),
                            entry.getKey().classifier(),
                            value.version());
                    resolution.observedVersions.add(value.version());
                    resolution.currentScope = resolution.widestScope = scope;
                    resolution.systemPath = entry.getValue().systemPath();
                    resolution.exclusions = entry.getValue().exclusions();
                    resolution.optional = entry.getValue().optional();
                } else {
                    version = resolution.currentVersion;
                    if (resolution.observedVersions.add(value.version()) || resolution.widestScope.reduces(scope)) {
                        resolution.widestScope = scope;
                        conflicting.add(entry.getKey());
                    }
                }
                boolean followed = dependencies.add(entry.getKey());
                edges.add(new Resolver.Edge(
                        current.origin() == null
                                ? null
                                : current.origin().coordinate(prefix, current.originVersion()),
                        entry.getKey().coordinate(prefix, value.version()),
                        value.version(),
                        scope.name().toLowerCase(Locale.ROOT),
                        followed));
                if (followed) {
                    Set<MavenDependencyName> exclusions = current.exclusions();
                    if (value.exclusions() != null) {
                        exclusions = new HashSet<>(exclusions);
                        exclusions.addAll(value.exclusions());
                    }
                    queue.add(new PendingPom(dispatch(executor,
                            repository,
                            entry.getKey().groupId(),
                            entry.getKey().artifactId(),
                            version,
                            resolved,
                            unresolved), scope, exclusions, entry.getKey(), value.version()));
                }
            }
            PendingPom pending = queue.poll();
            current = pending == null ? null : pending.awaited();
        } while (current != null);
        return conflicting;
    }

    private CompletableFuture<ResolvedPom> dispatch(Executor executor,
                                                    MavenRepository repository,
                                                    String groupId,
                                                    String artifactId,
                                                    String version,
                                                    Map<DependencyCoordinate, ResolvedPom> resolved,
                                                    Map<DependencyCoordinate, UnresolvedPom> unresolved) {
        ResolvedPom cached = resolved.get(new DependencyCoordinate(groupId, artifactId, version));
        if (cached != null) {
            return CompletableFuture.completedFuture(cached);
        }
        CompletableFuture<ResolvedPom> pending = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                pending.complete(resolveOrCached(executor,
                        repository,
                        groupId,
                        artifactId,
                        version,
                        resolved,
                        unresolved));
            } catch (Throwable t) {
                pending.completeExceptionally(t);
            }
        });
        return pending;
    }

    public SequencedMap<Path, MavenLocalPom> local(Executor executor,
                                                   Repository repository,
                                                   Path root) throws IOException {
        SequencedSet<Path> modules = new LinkedHashSet<>();
        Map<DependencyCoordinate, UnresolvedPom> unresolved = new HashMap<>();
        Map<Path, UnresolvedPom> paths = new HashMap<>();
        Queue<Map.Entry<Path, String>> queue = new ArrayDeque<>(List.of(Map.entry(root, "")));
        while (!queue.isEmpty()) {
            Map.Entry<Path, String> listed = queue.remove();
            Path current = listed.getKey();
            if (Files.exists(current.resolve(BuildExecutor.SKIP_MARKER))) {
                continue;
            } else if (!Files.isRegularFile(current.resolve("pom.xml"))) {
                throw new IllegalArgumentException(listed.getValue() + " names "
                        + root.relativize(current) + ", which holds no pom.xml - correct or remove the entry");
            }
            if (modules.add(current)) {
                UnresolvedPom pom;
                try {
                    pom = assemble(executor,
                            MavenRepository.of(repository),
                            Files.newInputStream(current.resolve("pom.xml")),
                            true,
                            true,
                            current,
                            paths,
                            new HashSet<>(),
                            unresolved);
                } catch (SAXException | ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
                if (pom.modules() != null) {
                    for (String module : pom.modules()) {
                        queue.add(Map.entry(current.resolve(module).normalize(),
                                "<module>" + module + "</module> of " + root.relativize(current.resolve("pom.xml"))));
                    }
                }
                paths.put(current, pom);
            } else {
                throw new IllegalArgumentException("Circular POM module reference to " + current);
            }
        }
        Map<MavenDependencyName, String> subprojects = new HashMap<>();
        for (Path module : modules) {
            UnresolvedPom pom = paths.get(module);
            String groupId = property(pom.groupId(), pom.properties()),
                    artifactId = property(pom.artifactId(), pom.properties()),
                    version = property(pom.version(), pom.properties());
            if (groupId != null && artifactId != null && version != null) {
                subprojects.put(new MavenDependencyName(groupId, artifactId), version);
            }
        }
        SequencedMap<Path, MavenLocalPom> results = new LinkedHashMap<>();
        for (Path module : modules) {
            UnresolvedPom pom = paths.get(module);
            SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = new LinkedHashMap<>();
            SequencedMap<MavenDependencyKey, MavenDependencyValue> managedDependencies = managed(executor,
                    MavenRepository.of(repository),
                    pom,
                    unresolved);
            SequencedMap<String, String> plugins = new LinkedHashMap<>(), pluginExclusions = new LinkedHashMap<>();
            pom.plugins().forEach((plugin, group) -> {
                String[] segments = plugin.split("/");
                String version = null;
                if (segments.length == 3 && segments[0].equals("maven")) {
                    MavenDependencyValue managedPlugin = Dependencies.PROCESSOR_PATH.contains(group)
                            ? managedDependencies.get(new MavenDependencyKey(segments[1], segments[2], "jar", null))
                            : null;
                    version = managedPlugin == null || managedPlugin.version() == null
                            ? subprojects.get(new MavenDependencyName(segments[1], segments[2]))
                            : managedPlugin.version();
                }
                plugins.put(version == null ? plugin : plugin + "/" + version, group);
            });
            SequencedMap<String, String> testPlugins = new LinkedHashMap<>(plugins);
            pom.dependencies().forEach((key, value) -> {
                MavenDependencyKey resolvedKey = key.resolve(pom.properties());
                MavenDependencyValue resolved = defaultScope(merge(value.resolve(pom.properties()),
                        managedDependencies.get(resolvedKey)));
                String subproject = subprojects.get(new MavenDependencyName(resolvedKey.groupId(), resolvedKey.artifactId()));
                if (resolved.version() == null && subproject != null) {
                    resolved = new MavenDependencyValue(subproject,
                            resolved.scope(),
                            resolved.systemPath(),
                            resolved.exclusions(),
                            resolved.optional(),
                            resolved.checksum());
                }
                switch (resolvedKey.type()) {
                    case "processor", "classpath-processor", "modular-processor" -> {
                        MavenDependencyValue managedJar = resolved.version() == null
                                ? managedDependencies.get(new MavenDependencyKey(resolvedKey.groupId(),
                                        resolvedKey.artifactId(),
                                        "jar",
                                        resolvedKey.classifier()))
                                : null;
                        String version = managedJar == null ? resolved.version() : managedJar.version();
                        String plugin = "maven/" + resolvedKey.groupId() + "/" + resolvedKey.artifactId()
                                + (version == null ? "" : "/" + version);
                        String declared = (resolved.scope() == MavenDependencyScope.TEST ? testPlugins : plugins).put(plugin, "plugin");
                        if (declared != null && !declared.equals("plugin")) {
                            throw new IllegalArgumentException("The processor " + plugin + " in " + module.resolve("pom.xml")
                                    + " is also named by a jenesis.plugin comment in the group " + declared
                                    + ", but a coordinate resolves in one group of a module only: drop the comment"
                                    + " or the dependency");
                        }
                        if (resolved.exclusions() != null && !resolved.exclusions().isEmpty()) {
                            pluginExclusions.put(plugin, resolved.exclusions().stream()
                                    .map(name -> name.groupId() + "/" + name.artifactId())
                                    .collect(Collectors.joining(",")));
                        }
                    }
                    case "classpath-jar", "modular-jar" -> throw new IllegalArgumentException("The dependency on "
                            + resolvedKey.groupId() + ":" + resolvedKey.artifactId() + " in " + module.resolve("pom.xml")
                            + " is of type " + resolvedKey.type() + ", but Jenesis places a jar by whether it"
                            + " describes a module - declare it as a jar");
                    default -> dependencies.put(resolvedKey, resolved);
                }
            });
            String release = Stream.of("maven.compiler.release", "maven.compiler.target", "maven.compiler.source")
                    .map(key -> property(pom.properties().get(key), pom.properties()))
                    .filter(value -> value != null && !value.isBlank())
                    .findFirst()
                    .map(value -> value.startsWith("1.") ? value.substring(2) : value)
                    .orElse(null),
                    testRelease = property(pom.properties().get("maven.compiler.testRelease"), pom.properties());
            if (Boolean.parseBoolean(property(pom.properties().get("maven.compiler.enablePreview"), pom.properties()))) {
                release = (release == null ? Integer.toString(Runtime.version().feature()) : release) + "-preview";
                testRelease = testRelease == null ? null : testRelease + "-preview";
            }
            SequencedMap<MavenDependencyKey, MavenDependencyKey.Versioned> expressions = new LinkedHashMap<>();
            Stream.concat(pom.managedDependencies().entrySet().stream(), pom.dependencies().entrySet().stream())
                    .filter(entry -> Stream.of(entry.getKey().groupId(),
                                    entry.getKey().artifactId(),
                                    entry.getKey().type(),
                                    entry.getKey().classifier(),
                                    entry.getValue().version())
                            .anyMatch(text -> text != null && text.contains("${")))
                    .forEach(entry -> {
                        DependencyKey raw = entry.getKey();
                        String rawVersion = entry.getValue().version() != null && entry.getValue().version().contains("${")
                                ? entry.getValue().version()
                                : null;
                        expressions.merge(raw.resolve(pom.properties()),
                                new MavenDependencyKey.Versioned(new MavenDependencyKey(raw.groupId(),
                                        raw.artifactId(),
                                        raw.type(),
                                        raw.classifier()), rawVersion),
                                (previous, next) -> new MavenDependencyKey.Versioned(previous.key(),
                                        previous.version() == null ? next.version() : previous.version()));
                    });
            Map<String, String> modelled = new HashMap<>();
            pom.metadata().properties().forEach((key, value) -> modelled.put("project." + key, value));
            modelled.putAll(pom.properties());
            SequencedMap<MavenDependencyKey, MavenDependencyValue> reactor = new LinkedHashMap<>();
            for (DependencyKey declared : pom.declaredManagement()) {
                MavenDependencyKey key = declared.resolve(pom.properties());
                MavenDependencyValue value = managedDependencies.get(key);
                if (value != null
                        && value.version() != null
                        && value.version().equals(subprojects.get(new MavenDependencyName(key.groupId(), key.artifactId())))) {
                    reactor.put(key, value);
                }
            }
            results.put(root.relativize(module), new MavenLocalPom(property(pom.groupId(), pom.properties()),
                    property(pom.artifactId(), pom.properties()),
                    property(pom.version(), pom.properties()),
                    property(pom.packaging(), pom.properties()),
                    release,
                    testRelease == null ? release : testRelease,
                    directory(pom.sourceDirectory(), pom.properties()),
                    pom.resourceDirectories() == null ? null : pom.resourceDirectories().stream()
                            .map(resource -> directory(resource, pom.properties()))
                            .toList(),
                    directory(pom.testSourceDirectory(), pom.properties()),
                    pom.testResourceDirectories() == null ? null : pom.testResourceDirectories().stream()
                            .map(resource -> directory(resource, pom.properties()))
                            .toList(),
                    dependencies,
                    managedDependencies,
                    pom.bom() == null ? null : pom.bom().entrySet().stream().collect(Collectors.toMap(
                            entry -> entry.getKey().resolve(pom.properties()),
                            entry -> entry.getValue().resolve(pom.properties()),
                            (left, _) -> left,
                            LinkedHashMap::new)),
                    pom.qualifiedDependencies(),
                    pom.attachments(),
                    pom.natives(),
                    plugins,
                    testPlugins,
                    pluginExclusions,
                    pom.aliases(),
                    pom.signatures(),
                    property(pom.properties().get("mainClass"), pom.properties()),
                    pom.metadata().expand(value -> property(value, modelled)),
                    expressions,
                    pom.relocation() == null ? null : new MavenPomEmitter.Relocation(
                            property(pom.relocation().groupId(), pom.properties()),
                            property(pom.relocation().artifactId(), pom.properties()),
                            property(pom.relocation().version(), pom.properties()),
                            property(pom.relocation().message(), pom.properties())),
                    reactor));
        }
        return results;
    }

    private UnresolvedPom assemble(Executor executor,
                                   MavenRepository repository,
                                   InputStream inputStream,
                                   boolean extended,
                                   boolean trusted,
                                   Path path,
                                   Map<Path, UnresolvedPom> paths,
                                   Set<DependencyCoordinate> children,
                                   Map<DependencyCoordinate, UnresolvedPom> unresolved)
            throws IOException, SAXException, ParserConfigurationException {
        Document document;
        try (inputStream) {
            DocumentBuilder builder;
            synchronized (factory) {
                builder = factory.newDocumentBuilder();
            }
            document = builder.parse(inputStream);
        }
        String namespace = document.getDocumentElement().getAttribute("xmlns");
        return switch (namespace.isEmpty() ? NAMESPACE_4_0_0 : namespace) {
            case NAMESPACE_4_0_0, NAMESPACE_4_1_0 -> {
                boolean inferring = NAMESPACE_4_1_0.equals(namespace);
                ParentCoordinate parent = null;
                Node declared = toElements(document.getDocumentElement(), "parent").findFirst().orElse(null);
                if (declared != null) {
                    String relative = toElementText(declared, "relativePath").map(value -> value.endsWith("/pom.xml")
                            ? value.substring(0, value.length() - 7)
                            : value).orElse("../");
                    String parentGroupId = toElementText(declared, "groupId").orElse(null),
                            parentArtifactId = toElementText(declared, "artifactId").orElse(null),
                            parentVersion = toElementText(declared, "version").orElse(null);
                    if (!inferring || parentGroupId != null && parentArtifactId != null && parentVersion != null) {
                        if (parentGroupId == null) {
                            throw missing("parent.groupId").get();
                        } else if (parentArtifactId == null) {
                            throw missing("parent.artifactId").get();
                        } else if (parentVersion == null) {
                            throw missing("parent.version").get();
                        }
                    } else {
                        Path pom = path == null || relative.isEmpty() ? null : path.resolve(relative).resolve("pom.xml");
                        if (pom == null || !Files.exists(pom)) {
                            throw new IllegalStateException("A parent that leaves out its groupId, artifactId or version"
                                    + " is the POM at its relativePath, but there is none"
                                    + (pom == null ? "" : " at " + pom)
                                    + " - name the parent's coordinate or point relativePath at its folder");
                        }
                        UnresolvedPom local = paths.get(path);
                        if (local == null) {
                            if (!children.add(new DependencyCoordinate("", pom.toAbsolutePath().normalize().toString(), ""))) {
                                throw new IllegalStateException("Circular parent inferred through relativePath at " + pom);
                            }
                            local = assemble(executor,
                                    repository,
                                    Files.newInputStream(pom),
                                    false,
                                    trusted,
                                    pom.getParent(),
                                    paths,
                                    children,
                                    unresolved);
                            paths.put(path, local);
                        }
                        parentGroupId = parentGroupId == null ? property(local.groupId(), local.properties()) : parentGroupId;
                        parentArtifactId = parentArtifactId == null
                                ? property(local.artifactId(), local.properties())
                                : parentArtifactId;
                        parentVersion = parentVersion == null ? property(local.version(), local.properties()) : parentVersion;
                    }
                    parent = new ParentCoordinate(parentGroupId, parentArtifactId, parentVersion, path != null ? relative : null);
                }
                Map<String, String> properties = new HashMap<>();
                Map<DependencyKey, DependencyValue> managedDependencies = new LinkedHashMap<>();
                Map<DependencyKey, DependencyValue> inheritedManagedDependencies = new LinkedHashMap<>();
                SequencedMap<DependencyKey, DependencyValue> dependencies = new LinkedHashMap<>();
                List<License> parentLicenses = List.of();
                SequencedMap<String, String> parentPlugins = Collections.emptyNavigableMap(),
                        parentAliases = Collections.emptyNavigableMap();
                MavenPomEmitter.Metadata parentMetadata = MavenPomEmitter.Metadata.NONE;
                Set<String> parentVerbatim = Set.of();
                String groupId = null, artifactId = null, version = null;
                UnresolvedPom localParent = null;
                if (parent != null) {
                    if (!children.add(new DependencyCoordinate(parent.groupId(),
                            parent.artifactId(),
                            parent.version()))) {
                        throw new IllegalStateException("Circular dependency to "
                                + parent.groupId() + ":" + parent.artifactId() + ":" + parent.version());
                    }
                    UnresolvedPom resolution = null;
                    if (path != null && !parent.relativePath().isEmpty()) {
                        resolution = paths.get(path);
                        if (resolution == null) {
                            Path candidate = path.resolve(parent.relativePath()), pom = candidate.resolve("pom.xml");
                            if (Files.exists(pom)) {
                                resolution = assemble(executor,
                                        repository,
                                        Files.newInputStream(pom),
                                        false,
                                        trusted,
                                        candidate,
                                        paths,
                                        children,
                                        unresolved);
                                paths.put(path, resolution);
                            }
                        }
                        if (resolution != null) {
                            groupId = property(resolution.groupId(), resolution.properties());
                            artifactId = property(resolution.artifactId(), resolution.properties());
                            version = property(resolution.version(), resolution.properties());
                            if (!parent.groupId().equals(groupId)
                                    || !parent.artifactId().equals(artifactId)
                                    || !parent.version().equals(version)) {
                                resolution = null;
                            } else {
                                parentPlugins = resolution.plugins();
                                parentAliases = resolution.aliases();
                                localParent = resolution;
                            }
                        }
                    }
                    if (resolution == null) {
                        resolution = assembleOrCached(executor,
                                repository,
                                parent.groupId(),
                                parent.artifactId(),
                                parent.version(),
                                null,
                                children,
                                unresolved);
                        if (resolution.missing()) {
                            throw new IllegalStateException("Cannot fetch the parent " + parent.groupId() + ":"
                                    + parent.artifactId() + ":" + parent.version()
                                    + toElementText(document.getDocumentElement(), "artifactId")
                                            .map(child -> ", the parent of " + child + ",")
                                            .orElse("")
                                    + " from the Maven repositories in use - name the repository that publishes it"
                                    + " with -Djenesis.maven.uri, or point the parent's relativePath at a local copy");
                        }
                        groupId = property(resolution.groupId(), resolution.properties());
                        artifactId = property(resolution.artifactId(), resolution.properties());
                        version = property(resolution.version(), resolution.properties());
                    }
                    properties.putAll(resolution.properties());
                    for (String property : IMPLICITS) {
                        String value = resolution.properties().get(property);
                        if (value != null) {
                            properties.put("parent." + property, value);
                            properties.put("project.parent." + property, value);
                        }
                    }
                    inheritedManagedDependencies.putAll(resolution.managedDependencies());
                    dependencies.putAll(resolution.dependencies());
                    parentLicenses = resolution.licenses();
                    parentMetadata = resolution.metadata();
                    parentVerbatim = resolution.verbatim();
                }
                IMPLICITS.forEach(property -> toElements(document.getDocumentElement(), property)
                        .findFirst()
                        .ifPresent(node -> {
                            String value = node.getTextContent().trim();
                            properties.put(property, value);
                            properties.put("project." + property, value);
                            properties.put("pom." + property, value);
                        }));
                List<Node> models = Stream.concat(Stream.of(document.getDocumentElement()),
                        toActiveProfiles(document.getDocumentElement(), path, trusted)).toList();
                models.stream()
                        .flatMap(model -> toElements(model, "properties").limit(1))
                        .flatMap(MavenPomResolver::toChildren)
                        .filter(node -> node.getNodeType() == Node.ELEMENT_NODE)
                        .forEach(node -> properties.put(node.getNodeName(), node.getTextContent().trim()));
                models.stream()
                        .flatMap(model -> toElements(model, "dependencyManagement").limit(1))
                        .flatMap(node -> toElements(node, "dependencies").limit(1))
                        .flatMap(node -> toElements(node, "dependency"))
                        .map(node -> toDependency(node, trusted))
                        .forEach(entry -> managedDependencies.put(entry.getKey(), entry.getValue()));
                Map<DependencyKey, DependencyValue> declaredManagedDependencies = new LinkedHashMap<>(managedDependencies);
                inheritedManagedDependencies.forEach(managedDependencies::putIfAbsent);
                SequencedMap<DependencyKey, DependencyValue> declaredDependencies = new LinkedHashMap<>();
                models.stream()
                        .flatMap(model -> toElements(model, "dependencies").limit(1))
                        .forEach(declarations -> {
                            SequencedMap<DependencyKey, DependencyValue> listed = new LinkedHashMap<>();
                            toElements(declarations, "dependency").map(node -> toDependency(node, false)).forEach(entry -> {
                                DependencyValue previous = listed.put(entry.getKey(), entry.getValue());
                                if (previous != null && path != null && printing != null) {
                                    printing.accept(("%s%-11s%s The pom.xml of %s declares %s:%s:%s%s twice, at version %s"
                                            + " and %s: the second declaration replaces the first, as in Maven, which warns"
                                            + (trusted
                                                    ? " as well - remove one"
                                                    : " as well - it is the artifact's own published POM, so this"
                                                            + " needs no action"))
                                            .formatted(palette.warning(),
                                                    "[DUPLICATE]",
                                                    palette.reset(),
                                                    toElementText(document.getDocumentElement(), "artifactId").orElse(""),
                                                    entry.getKey().groupId(),
                                                    entry.getKey().artifactId(),
                                                    entry.getKey().type() == null ? "jar" : entry.getKey().type(),
                                                    entry.getKey().classifier() == null ? "" : ":" + entry.getKey().classifier(),
                                                    previous.version() == null ? "none" : previous.version(),
                                                    entry.getValue().version() == null ? "none" : entry.getValue().version()));
                                }
                            });
                            declaredDependencies.putAll(listed);
                        });
                declaredDependencies.forEach(dependencies::putLast);
                Node build = extended || trusted && path != null
                        ? toElements(document.getDocumentElement(), "build").findFirst().orElse(null)
                        : null;
                List<Node> builds = extended || trusted && path != null
                        ? models.stream().flatMap(model -> toElements(model, "build").limit(1)).toList()
                        : List.of();
                List<String> subprojects = null;
                boolean bom = false;
                if (extended) {
                    Node listed = toElements(document.getDocumentElement(), "subprojects").findFirst()
                            .or(() -> toElements(document.getDocumentElement(), "modules").findFirst())
                            .orElse(null);
                    String packaging = toElementText(document.getDocumentElement(), "packaging").orElse(null);
                    if (listed != null) {
                        subprojects = Stream.concat(toElements(listed, "subproject"), toElements(listed, "module"))
                                .map(node -> node.getTextContent().trim())
                                .toList();
                    } else if (inferring && path != null && ("pom".equals(packaging) || "bom".equals(packaging))) {
                        try (Stream<Path> folders = Files.list(path)) {
                            subprojects = folders.filter(folder -> Files.isRegularFile(folder.resolve("pom.xml")))
                                    .map(folder -> folder.getFileName().toString())
                                    .sorted()
                                    .toList();
                        }
                    }
                    bom = (subprojects == null || subprojects.isEmpty())
                            && ("pom".equals(packaging) || "bom".equals(packaging))
                            && !declaredManagedDependencies.isEmpty();
                }
                String sourceDirectory = build == null ? null : toElementText(build, "sourceDirectory").orElse(null),
                        testSourceDirectory = build == null ? null : toElementText(build, "testSourceDirectory").orElse(null);
                List<String> resourceDirectories = toDirectories(builds, "resources", "resource"),
                        testResourceDirectories = toDirectories(builds, "testResources", "testResource");
                if (localParent != null) {
                    sourceDirectory = sourceDirectory == null ? localParent.sourceDirectory() : sourceDirectory;
                    testSourceDirectory = testSourceDirectory == null ? localParent.testSourceDirectory() : testSourceDirectory;
                    resourceDirectories = resourceDirectories == null ? localParent.resourceDirectories() : resourceDirectories;
                    testResourceDirectories = testResourceDirectories == null
                            ? localParent.testResourceDirectories()
                            : testResourceDirectories;
                }
                Node sources = build == null || !inferring ? null : toElements(build, "sources").findFirst().orElse(null);
                if (sources != null) {
                    SequencedMap<String, List<String>> declaredSources = new LinkedHashMap<>();
                    for (Node source : toElements(sources, "source").toList()) {
                        if (toElementText(source, "enabled").map("false"::equals).orElse(false)) {
                            continue;
                        }
                        for (String unsupported : List.of("module", "targetVersion")) {
                            if (toElementText(source, unsupported).isPresent()) {
                                throw new IllegalArgumentException("A <source> with a <" + unsupported + "> is not read"
                                        + " by Jenesis, in " + (path == null ? "a POM" : path.resolve("pom.xml"))
                                        + " - build each Java module, and each release, from a POM of its own");
                            }
                        }
                        String scope = toElementText(source, "scope").orElse("main"),
                                lang = toElementText(source, "lang").orElse("java");
                        declaredSources.computeIfAbsent(scope + "/" + lang, _ -> new ArrayList<>())
                                .add(toElementText(source, "directory").orElse("src/" + scope + "/" + lang));
                    }
                    for (String scope : List.of("main", "test")) {
                        List<String> java = declaredSources.get(scope + "/java");
                        if (java != null && java.size() > 1) {
                            throw new IllegalArgumentException("Jenesis compiles one source directory per scope, but "
                                    + (path == null ? "a POM" : path.resolve("pom.xml")) + " declares " + java
                                    + " for " + scope + " - keep one of them");
                        }
                    }
                    sourceDirectory = declaredSources.containsKey("main/java")
                            ? declaredSources.get("main/java").getFirst()
                            : sourceDirectory;
                    testSourceDirectory = declaredSources.containsKey("test/java")
                            ? declaredSources.get("test/java").getFirst()
                            : testSourceDirectory;
                    resourceDirectories = declaredSources.getOrDefault("main/resources", resourceDirectories);
                    testResourceDirectories = declaredSources.getOrDefault("test/resources", testResourceDirectories);
                }
                List<License> ownLicenses = toElements(document.getDocumentElement(), "licenses")
                        .limit(1)
                        .flatMap(node -> toElements(node, "license"))
                        .map(node -> new License(
                                null,
                                null,
                                toElementText(node, "name").orElse(null),
                                toElementText(node, "url").orElse(null)))
                        .toList();
                Element project = document.getDocumentElement(),
                        scm = (Element) toElements(project, "scm").findFirst().orElse(null);
                Set<String> verbatim = new HashSet<>();
                String child = toElementText(project, "artifactId").orElse(artifactId);
                for (String key : List.of("url", "scm.connection", "scm.developerConnection", "scm.url")) {
                    Element holder = key.equals("url") ? project : scm;
                    String append = holder == null
                            ? ""
                            : holder.getAttribute("child." + (key.equals("url") ? "project.url" : key) + ".inherit.append.path").trim();
                    if (append.isEmpty() ? parentVerbatim.contains(key) : append.equals("false")) {
                        verbatim.add(key);
                    }
                }
                MavenPomEmitter.Metadata metadata = toMetadata(project).inherit(parentMetadata, parentVerbatim, child, scm != null);
                yield new UnresolvedPom(
                        toElementText(document.getDocumentElement(), "groupId").orElse(groupId),
                        toElementText(document.getDocumentElement(), "artifactId").orElse(artifactId),
                        toElementText(document.getDocumentElement(), "version").orElse(version),
                        extended ? toElementText(document.getDocumentElement(), "packaging").orElse(null) : null,
                        sourceDirectory,
                        resourceDirectories,
                        testSourceDirectory,
                        testResourceDirectories,
                        subprojects,
                        properties,
                        managedDependencies,
                        dependencies,
                        extended
                                ? toQualifiedDependencies(document.getDocumentElement())
                                : Collections.emptyNavigableMap(),
                        extended
                                ? toAttachments(document.getDocumentElement())
                                : Collections.emptyNavigableMap(),
                        extended
                                ? toNatives(document.getDocumentElement())
                                : Collections.emptyNavigableSet(),
                        extended || trusted && path != null
                                ? inherited(toPlugins(document.getDocumentElement()), parentPlugins)
                                : Collections.emptyNavigableMap(),
                        extended || trusted && path != null
                                ? inherited(toAliases(document.getDocumentElement()), parentAliases)
                                : Collections.emptyNavigableMap(),
                        extended
                                ? toSignatures(document.getDocumentElement())
                                : Collections.emptyNavigableMap(),
                        ownLicenses.isEmpty() ? parentLicenses : ownLicenses,
                        metadata,
                        verbatim,
                        bom ? declaredManagedDependencies : null,
                        false,
                        toElements(project, "distributionManagement")
                                .flatMap(management -> toElements(management, "relocation"))
                                .findFirst()
                                .map(relocation -> new MavenPomEmitter.Relocation(
                                        toElementText(relocation, "groupId").orElse(null),
                                        toElementText(relocation, "artifactId").orElse(null),
                                        toElementText(relocation, "version").orElse(null),
                                        toElementText(relocation, "message").orElse(null)))
                                .orElse(null),
                        declaredManagedDependencies.keySet());
            }
            default -> throw new IllegalArgumentException("Unknown namespace: " + namespace);
        };
    }

    private UnresolvedPom assembleOrCached(Executor executor,
                                           MavenRepository repository,
                                           String groupId,
                                           String artifactId,
                                           String version,
                                           String checksum,
                                           Set<DependencyCoordinate> children,
                                           Map<DependencyCoordinate, UnresolvedPom> poms) throws IOException {
        DependencyCoordinate coordinates = new DependencyCoordinate(groupId, artifactId, version);
        UnresolvedPom pom = poms.get(coordinates);
        if (pom == null) {
            try {
                RepositoryItem candidate = repository.fetch(executor,
                        groupId,
                        artifactId,
                        version,
                        "pom",
                        null,
                        null).orElse(null);
                if (candidate == null) {
                    pom = new UnresolvedPom(groupId,
                            artifactId,
                            version,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null,
                            Map.of(),
                            Map.of(),
                            Collections.emptyNavigableMap(),
                            Collections.emptyNavigableMap(),
                            Collections.emptyNavigableMap(),
                            Collections.emptyNavigableSet(),
                            Collections.emptyNavigableMap(),
                            Collections.emptyNavigableMap(),
                            Collections.emptyNavigableMap(),
                            List.of(),
                            MavenPomEmitter.Metadata.NONE,
                            Set.of(),
                            null,
                            true,
                            null,
                            Set.of());
                } else {
                    Path localPath = candidate.file().map(Path::getParent).orElse(null);
                    Map<Path, UnresolvedPom> localPaths = localPath == null ? null : new HashMap<>();
                    if (checksum != null) {
                        int separator = checksum.indexOf('/');
                        if (separator < 0) {
                            throw new IllegalArgumentException(
                                    "Malformed POM checksum for " + groupId + ":" + artifactId + ":" + version
                                            + " (expected <algorithm>/<hex>): " + checksum);
                        }
                        String algorithm = checksum.substring(0, separator);
                        String expected = checksum.substring(separator + 1);
                        MessageDigest digest;
                        try {
                            digest = MessageDigest.getInstance(algorithm);
                        } catch (NoSuchAlgorithmException e) {
                            throw new IllegalStateException(e);
                        }
                        DigestInputStream digestStream = new DigestInputStream(candidate.toInputStream(), digest);
                        pom = assemble(executor, repository, drainAndValidate(digestStream,
                                digest,
                                expected,
                                groupId,
                                artifactId,
                                version), false, false, localPath, localPaths, children, poms);
                    } else {
                        pom = assemble(executor, repository, candidate.toInputStream(), false, false, localPath, localPaths, children, poms);
                    }
                }
            } catch (RuntimeException | SAXException | ParserConfigurationException e) {
                throw new IllegalStateException("Failed to resolve " + groupId + ":" + artifactId + ":" + version, e);
            }
            poms.put(coordinates, pom);
        }
        return pom;
    }

    private static InputStream drainAndValidate(DigestInputStream stream,
                                                MessageDigest digest,
                                                String expectedHex,
                                                String groupId,
                                                String artifactId,
                                                String version) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        try (stream) {
            stream.transferTo(buffer);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedHex)) {
            throw new IllegalStateException("Mismatched POM checksum for "
                    + groupId + ":" + artifactId + ":" + version
                    + " (expected " + expectedHex + ", got " + actual + ")");
        }
        return new ByteArrayInputStream(buffer.toByteArray());
    }

    private ResolvedPom resolve(Executor executor,
                                MavenRepository repository,
                                UnresolvedPom pom,
                                Map<DependencyCoordinate, UnresolvedPom> unresolved) throws IOException {
        Map<MavenDependencyKey, MavenDependencyValue> managedDependencies = managed(executor, repository, pom, unresolved);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies = new LinkedHashMap<>();
        pom.dependencies().forEach((key, value) -> dependencies.put(
                key.resolve(pom.properties()),
                value.resolve(pom.properties())));
        return new ResolvedPom(managedDependencies,
                dependencies,
                pom.licenses(),
                pom.relocation() == null ? null : new DependencyCoordinate(
                        property(pom.relocation().groupId(), pom.properties()),
                        property(pom.relocation().artifactId(), pom.properties()),
                        property(pom.relocation().version(), pom.properties())));
    }

    private SequencedMap<MavenDependencyKey, MavenDependencyValue> managed(Executor executor,
                                                                         MavenRepository repository,
                                                                         UnresolvedPom pom,
                                                                         Map<DependencyCoordinate, UnresolvedPom> unresolved)
            throws IOException {
        SequencedMap<MavenDependencyKey, MavenDependencyValue> managedDependencies = new LinkedHashMap<>();
        List<Map.Entry<MavenDependencyKey, MavenDependencyValue>> imports = new ArrayList<>();
        for (Map.Entry<DependencyKey, DependencyValue> entry : pom.managedDependencies().entrySet()) {
            MavenDependencyKey key = entry.getKey().resolve(pom.properties());
            MavenDependencyValue value = entry.getValue().resolve(pom.properties());
            if (value.scope() == MavenDependencyScope.IMPORT) {
                imports.add(Map.entry(key, value));
            } else {
                managedDependencies.putIfAbsent(key, value);
            }
        }
        for (Map.Entry<MavenDependencyKey, MavenDependencyValue> imported : imports) {
            flattenImport(executor,
                    repository,
                    imported.getKey().groupId(),
                    imported.getKey().artifactId(),
                    imported.getValue().version(),
                    imported.getValue().checksum(),
                    managedDependencies,
                    new HashSet<>(),
                    unresolved);
        }
        return managedDependencies;
    }

    private void flattenImport(Executor executor,
                               MavenRepository repository,
                               String groupId,
                               String artifactId,
                               String version,
                               String checksum,
                               Map<MavenDependencyKey, MavenDependencyValue> managedDependencies,
                               Set<DependencyCoordinate> imports,
                               Map<DependencyCoordinate, UnresolvedPom> unresolved) throws IOException {
        if (!imports.add(new DependencyCoordinate(groupId, artifactId, version))) {
            return;
        }
        UnresolvedPom imported = assembleOrCached(executor,
                repository,
                groupId,
                artifactId,
                version,
                checksum,
                new HashSet<>(),
                unresolved);
        for (Map.Entry<DependencyKey, DependencyValue> entry : imported.managedDependencies().entrySet()) {
            MavenDependencyKey importKey = entry.getKey().resolve(imported.properties());
            MavenDependencyValue importValue = entry.getValue().resolve(imported.properties());
            if (importValue.scope() == MavenDependencyScope.IMPORT) {
                flattenImport(executor,
                        repository,
                        importKey.groupId(),
                        importKey.artifactId(),
                        importValue.version(),
                        importValue.checksum(),
                        managedDependencies,
                        imports,
                        unresolved);
            } else {
                managedDependencies.putIfAbsent(importKey, importValue);
            }
        }
    }

    private ResolvedPom resolveOrCached(Executor executor,
                                        MavenRepository repository,
                                        String groupId,
                                        String artifactId,
                                        String version,
                                        Map<DependencyCoordinate, ResolvedPom> resolved,
                                        Map<DependencyCoordinate, UnresolvedPom> unresolved) throws IOException {
        DependencyCoordinate coordinates = new DependencyCoordinate(groupId, artifactId, version);
        ResolvedPom pom = resolved.get(coordinates);
        if (pom == null) {
            try {
                pom = resolve(executor, repository, assembleOrCached(executor,
                        repository,
                        groupId,
                        artifactId,
                        version,
                        null,
                        new HashSet<>(),
                        unresolved), unresolved);
            } catch (RuntimeException e) {
                throw new IllegalStateException("Failed to resolve " + groupId + ":" + artifactId + ":" + version, e);
            }
            resolved.put(coordinates, pom);
        }
        return pom;
    }

    static Stream<Node> toChildren(Node node) {
        NodeList children = node.getChildNodes();
        return IntStream.iterate(0,
                index -> index < children.getLength(),
                index -> index + 1).mapToObj(children::item);
    }

    private static Stream<Node> toElements(Node node, String name) {
        return toChildren(node).filter(child -> child.getNodeType() == Node.ELEMENT_NODE
                && child.getNodeName().equals(name));
    }

    private static List<String> toDirectories(List<Node> builds, String list, String element) {
        List<Node> declared = builds.stream().flatMap(build -> toElements(build, list).limit(1)).toList();
        return declared.isEmpty() ? null : declared.stream()
                .flatMap(node -> toElements(node, element))
                .map(child -> toElementText(child, "directory").orElse(null))
                .filter(Objects::nonNull)
                .toList();
    }

    private Stream<Node> toActiveProfiles(Node project, Path path, boolean trusted) {
        List<Node> profiles = toElements(project, "profiles")
                .limit(1)
                .flatMap(node -> toElements(node, "profile"))
                .toList();
        List<Node> activated = profiles.stream().filter(profile -> toElements(profile, "activation")
                .findFirst()
                .filter(activation -> toChildren(activation)
                        .filter(condition -> condition.getNodeType() == Node.ELEMENT_NODE)
                        .allMatch(condition -> ACTIVATIONS.contains(condition.getNodeName())))
                .filter(activation -> toElements(activation, "jdk").findFirst().isPresent()
                        || toElements(activation, "os").findFirst().isPresent())
                .filter(activation -> toElementText(activation, "jdk").map(range -> isJdk(range, path, trusted)).orElse(true))
                .filter(activation -> toElements(activation, "os").findFirst().map(this::isOs).orElse(true))
                .isPresent()).toList();
        return activated.isEmpty()
                ? profiles.stream().filter(profile -> toElements(profile, "activation")
                        .findFirst()
                        .flatMap(activation -> toElementText(activation, "activeByDefault"))
                        .filter("true"::equals)
                        .isPresent())
                : activated.stream();
    }

    private boolean isJdk(String activation, Path path, boolean trusted) {
        boolean negated = activation.startsWith("!");
        String required = negated ? activation.substring(1).trim() : activation;
        if (!required.startsWith("[") && !required.startsWith("(")) {
            return jdk.startsWith(required) != negated;
        }
        if (JDK_UNCLOSED.matcher(required).matches()) {
            required = required + ")";
        } else if (JDK_BOUND.matcher(required).matches()) {
            required = required + ",)";
        }
        Matcher matcher = JDK_RANGE.matcher(required);
        boolean matched = false;
        int end = 0;
        while (end < required.length() && matcher.find(end) && matcher.start() == end) {
            String minimum = matcher.group(2), maximum = matcher.group(3) == null ? minimum : matcher.group(4);
            if (minimum == null && maximum == null && matcher.group(3) == null) {
                break;
            }
            int fromMinimum = minimum == null ? 1 : compareJdk(minimum),
                    fromMaximum = maximum == null ? -1 : compareJdk(maximum);
            matched |= (fromMinimum > 0 || fromMinimum == 0 && matcher.group(1).equals("["))
                    && (fromMaximum < 0 || fromMaximum == 0 && matcher.group(5).equals("]"));
            end = matcher.end();
        }
        if (end != required.length() || required.endsWith(",")) {
            if (!trusted) {
                return false;
            }
            throw new IllegalArgumentException("The profile activation <jdk>" + activation + "</jdk> of "
                    + (path == null ? "a POM" : path.resolve("pom.xml"))
                    + " is neither a version prefix, such as 1.8, nor ranges of versions, such as [9,), [1.8,17)"
                    + " or [11,12),[16,)");
        }
        return matched != negated;
    }

    private boolean isOs(Node os) {
        return toChildren(os).filter(condition -> condition.getNodeType() == Node.ELEMENT_NODE).allMatch(condition -> {
            String required = condition.getTextContent().trim().toLowerCase(Locale.ENGLISH);
            boolean negated = required.startsWith("!");
            String value = negated ? required.substring(1).trim() : required;
            boolean windows = osName.contains("windows");
            boolean matched = switch (condition.getNodeName()) {
                case "family" -> switch (value) {
                    case "windows" -> windows;
                    case "win9x", "winnt" -> windows && Stream.of("95", "98", "me", "ce").anyMatch(osName::contains)
                            == value.equals("win9x");
                    case "dos" -> windows || osName.contains("os/2");
                    case "mac" -> osName.contains("mac");
                    case "unix" -> !windows
                            && !osName.contains("os/2")
                            && !osName.contains("netware")
                            && !osName.contains("openvms")
                            && (!osName.contains("mac") || osName.endsWith("x"));
                    case "tandem" -> osName.contains("nonstop_kernel");
                    case "z/os" -> osName.contains("z/os") || osName.contains("os/390");
                    default -> osName.contains(value);
                };
                case "name" -> osName.equals(value);
                case "arch" -> osArch.equals(value);
                case "version" -> osVersion.equals(value);
                default -> true;
            };
            return matched != negated;
        });
    }

    private int compareJdk(String bound) {
        String[] running = jdk.replaceAll("[^0-9._-]", "").split("[._-]+"), required = bound.split("\\.");
        for (int index = 0; index < 3; index++) {
            int left = index < running.length && !running[index].isEmpty() ? Integer.parseInt(running[index]) : 0,
                    right = index < required.length ? Integer.parseInt(required[index]) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    private static Optional<String> toElementText(Node node, String localName) {
        return toElements(node, localName).map(child -> child.getTextContent().trim()).findFirst();
    }

    private static Map.Entry<DependencyKey, DependencyValue> toDependency(Node node, boolean trusted) {
        String type = toElementText(node, "type").orElse("jar");
        String classifier = toElementText(node, "classifier").orElse(null);
        String aliased = switch (type) {
            case "test-jar" -> "tests";
            case "ejb-client" -> "client";
            case "ejb", "maven-plugin", "bundle" -> "";
            default -> null;
        };
        if (aliased != null) {
            type = "jar";
            if (classifier == null && !aliased.isEmpty()) {
                classifier = aliased;
            }
        }
        return Map.entry(
                new DependencyKey(
                        toElementText(node, "groupId").orElseThrow(missing("groupId")),
                        toElementText(node, "artifactId").orElseThrow(missing("artifactId")),
                        type,
                        classifier),
                new DependencyValue(
                        toElementText(node, "version").orElse(null),
                        toElementText(node, "scope").orElse(null),
                        toElementText(node, "systemPath").orElse(null),
                        toElements(node, "exclusions")
                                .findFirst()
                                .map(exclusions -> toElements(exclusions, "exclusion")
                                        .map(child -> new MavenDependencyName(
                                                toElementText(child, "groupId").orElseThrow(missing("exclusion.groupId")),
                                                toElementText(child, "artifactId").orElseThrow(missing("exclusion.artifactId"))))
                                        .toList())
                                .orElse(null),
                        toElementText(node, "optional").orElse(null),
                        trusted ? toCommentChecksum(node).orElse(null) : null));
    }

    private static Optional<String> toCommentChecksum(Node node) {
        List<String> matches = toChildren(node)
                .filter(child -> child.getNodeType() == Node.COMMENT_NODE)
                .map(Node::getNodeValue)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> text.startsWith(CHECKSUM_PREFIX))
                .map(text -> text.substring(CHECKSUM_PREFIX.length()).trim())
                .toList();
        if (matches.size() > 1) {
            throw new IllegalStateException("Multiple " + CHECKSUM_PREFIX + "* comments on dependency "
                    + toElementText(node, "groupId").orElse("?")
                    + ":" + toElementText(node, "artifactId").orElse("?")
                    + ":" + toElementText(node, "version").orElse("?")
                    + ": " + matches);
        }
        return matches.stream().findFirst();
    }

    private static SequencedMap<String, String> toQualifiedDependencies(Node node) {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        toChildren(node)
                .filter(child -> child.getNodeType() == Node.COMMENT_NODE)
                .map(Node::getNodeValue)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> text.startsWith("jenesis.pin"))
                .forEach(text -> {
                    for (String line : text.substring("jenesis.pin".length()).replace("&#45;", "-").split("\n")) {
                        String trimmed = line.trim().replaceAll("\\s+", " ");
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        int space = trimmed.indexOf(' ');
                        if (space < 1) {
                            continue;
                        }
                        String token = trimmed.substring(0, space).trim();
                        String value = trimmed.substring(space + 1).trim().replaceAll("\\s+", " ");
                        String guard = null;
                        if (value.endsWith(")")) {
                            int bracket = value.lastIndexOf('(');
                            if (bracket < 0) {
                                throw new IllegalArgumentException("Malformed jenesis.pin guard '"
                                        + value
                                        + "': expected <value> (<token>,<token>...)");
                            }
                            String guarded = value.substring(0, bracket).trim();
                            if (!guarded.isEmpty()) {
                                guard = Platform.of(
                                        value.substring(bracket + 1, value.length() - 1)).canonical();
                                value = guarded;
                            }
                        }
                        if (value.isEmpty()) {
                            continue;
                        }
                        String[] words = value.split(" ");
                        if (words.length > 2 || (words.length == 2 && words[1].indexOf('/') < 1)) {
                            throw new IllegalArgumentException("Malformed jenesis.pin declaration '"
                                    + token + " " + value
                                    + "': expected <token> <version> [<algorithm>/<hash>] [(<platform>)]."
                                    + " Every line inside a jenesis.pin comment is a declaration of its own,"
                                    + " so prose written among them becomes a pin; move it outside the comment");
                        }
                        String key;
                        int firstSlash = token.indexOf('/');
                        int secondSlash = firstSlash < 0 ? -1 : token.indexOf('/', firstSlash + 1);
                        if (firstSlash < 0) {
                            key = "main/module/" + token;
                        } else if (secondSlash < 0) {
                            if (firstSlash < 1 || firstSlash == token.length() - 1) {
                                throw new IllegalArgumentException("Malformed jenesis.pin token '"
                                        + token
                                        + "': expected <module>, <groupId>/<artifactId>,"
                                        + " or <group>/<repository>/<coordinate>");
                            }
                            key = "main/maven/" + token;
                        } else {
                            if (firstSlash < 1 || secondSlash == firstSlash + 1
                                    || secondSlash == token.length() - 1) {
                                throw new IllegalArgumentException("Malformed jenesis.pin token '"
                                        + token
                                        + "': expected <module>, <groupId>/<artifactId>,"
                                        + " or <group>/<repository>/<coordinate>");
                            }
                            key = token;
                        }
                        entries.put(guard == null ? key : key + "[" + guard + "]", value);
                    }
                });
        return entries;
    }

    private static SequencedMap<String, String> toSignatures(Node node) {
        SequencedMap<String, String> entries = new TreeMap<>();
        toChildren(node)
                .filter(child -> child.getNodeType() == Node.COMMENT_NODE)
                .map(Node::getNodeValue)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> text.startsWith("jenesis.signature"))
                .forEach(text -> {
                    for (String line : text.substring("jenesis.signature".length())
                            .replace("&#45;", "-")
                            .split("\n")) {
                        String declaration = line.trim().replaceAll("\\s+", " ");
                        if (declaration.isEmpty()) {
                            continue;
                        }
                        int split = declaration.indexOf(' ');
                        if (split < 1) {
                            throw new IllegalArgumentException("Malformed jenesis.signature declaration '"
                                    + declaration
                                    + "': expected <algorithm>/<fingerprint> <token>...");
                        }
                        String fingerprint = declaration.substring(0, split);
                        int slash = fingerprint.indexOf('/');
                        if (slash < 1 || slash != fingerprint.lastIndexOf('/')
                                || slash == fingerprint.length() - 1) {
                            throw new IllegalArgumentException("Malformed jenesis.signature fingerprint '"
                                    + fingerprint
                                    + "': expected <algorithm>/<fingerprint>, or unsigned/missing or unsigned/ignored for a coordinate that publishes no signature."
                                    + " Every line inside a jenesis.signature comment is a declaration of its"
                                    + " own, so prose written among them is read as one; move it outside the"
                                    + " comment");
                        }
                        String existing = entries.get(fingerprint);
                        SequencedSet<String> tokens = new TreeSet<>();
                        if (existing != null && !existing.isEmpty()) {
                            tokens.addAll(List.of(existing.split(" ")));
                        }
                        for (String token : declaration.substring(split + 1).split(" ")) {
                            if (!COORDINATE.matcher(token).matches()) {
                                throw new IllegalArgumentException("Malformed jenesis.signature token '"
                                        + token
                                        + "' for " + fingerprint
                                        + ": expected <group>/<repo>/<coordinate>, <groupId>/<artifactId>"
                                        + " or <module>, optionally ending in /*."
                                        + " Every line inside a jenesis.signature comment is a declaration of"
                                        + " its own, so prose written among them becomes a token; move it"
                                        + " outside the comment");
                            }
                            boolean wildcard = token.endsWith("/*");
                            String base = wildcard ? token.substring(0, token.length() - 2) : token;
                            int first = base.indexOf('/');
                            String expanded;
                            if (first < 0) {
                                expanded = wildcard ? "main/maven/" + base : "main/module/" + base;
                            } else if (base.indexOf('/', first + 1) < 0) {
                                expanded = "main/maven/" + base;
                            } else {
                                expanded = base;
                            }
                            tokens.add(wildcard ? expanded + "/*" : expanded);
                        }
                        entries.put(fingerprint, String.join(" ", tokens));
                    }
                });
        return entries;
    }

    private static MavenPomEmitter.Metadata toMetadata(Node node) {
        List<MavenPomEmitter.Metadata.License> licenses = toElements(node, "licenses").limit(1)
                .flatMap(declared -> toElements(declared, "license"))
                .filter(license -> !toElementText(license, "name").orElse("").isEmpty())
                .map(license -> new MavenPomEmitter.Metadata.License(toElementText(license, "name").orElseThrow(),
                        toElementText(license, "url").orElse(null),
                        toElementText(license, "distribution").orElse(null)))
                .toList();
        List<MavenPomEmitter.Metadata.Developer> developers = toElements(node, "developers").limit(1)
                .flatMap(declared -> toElements(declared, "developer"))
                .map(developer -> new MavenPomEmitter.Metadata.Developer(toElementText(developer, "id")
                                .filter(id -> !id.isEmpty())
                                .orElse(null),
                        toElementText(developer, "name").orElse(null),
                        toElementText(developer, "email").orElse(null),
                        toElementText(developer, "url").orElse(null),
                        toElementText(developer, "organization").orElse(null),
                        toElementText(developer, "organizationUrl").orElse(null),
                        toElements(developer, "roles").limit(1)
                                .flatMap(roles -> toElements(roles, "role"))
                                .map(role -> role.getTextContent().trim())
                                .filter(role -> !role.isEmpty())
                                .toList(),
                        toElementText(developer, "timezone").orElse(null)))
                .filter(developer -> developer.id() != null || !developer.derivedId().isEmpty())
                .toList();
        Function<String, MavenPomEmitter.Metadata.Management> management = name -> toElements(node, name)
                .findFirst()
                .map(declared -> new MavenPomEmitter.Metadata.Management(toElementText(declared, "system").orElse(null),
                        toElementText(declared, "url").orElse(null)))
                .filter(declared -> declared.system() != null || declared.url() != null)
                .orElse(null);
        return new MavenPomEmitter.Metadata(toElementText(node, "name").orElse(null),
                toElementText(node, "description").orElse(null),
                toElementText(node, "url").orElse(null),
                licenses,
                developers,
                toElements(node, "scm").findFirst()
                        .map(scm -> new MavenPomEmitter.Metadata.Scm(toElementText(scm, "connection").orElse(null),
                                toElementText(scm, "developerConnection").orElse(null),
                                toElementText(scm, "url").orElse(null),
                                toElementText(scm, "tag").orElse(null)))
                        .filter(scm -> !scm.equals(new MavenPomEmitter.Metadata.Scm(null, null, null, null)))
                        .orElse(null),
                toElements(node, "organization").findFirst()
                        .map(organization -> new MavenPomEmitter.Metadata.Organization(toElementText(organization, "name").orElse(null),
                                toElementText(organization, "url").orElse(null)))
                        .filter(organization -> organization.name() != null || organization.url() != null)
                        .orElse(null),
                management.apply("issueManagement"),
                management.apply("ciManagement"),
                toElementText(node, "inceptionYear").orElse(null));
    }


    private static SequencedMap<String, String> inherited(SequencedMap<String, String> own,
                                                          SequencedMap<String, String> parent) {
        parent.forEach(own::putIfAbsent);
        return own;
    }

    private static SequencedMap<String, String> toPlugins(Node node) {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        toChildren(node)
                .filter(child -> child.getNodeType() == Node.COMMENT_NODE)
                .map(Node::getNodeValue)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> text.startsWith("jenesis.plugin"))
                .forEach(text -> {
                    for (String line : text.substring("jenesis.plugin".length()).replace("&#45;", "-").split("\n")) {
                        String trimmed = line.trim().replaceAll("\\s+", " ");
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        int space = trimmed.indexOf(' ');
                        String group, token;
                        if (space > 0 && trimmed.substring(0, space).indexOf('/') < 0) {
                            group = trimmed.substring(0, space).trim();
                            token = trimmed.substring(space + 1).trim();
                        } else {
                            group = "plugin";
                            token = trimmed;
                        }
                        if (token.isEmpty()) {
                            continue;
                        }
                        if (token.indexOf(' ') >= 0) {
                            throw new IllegalArgumentException("Malformed jenesis.plugin declaration '"
                                    + trimmed
                                    + "': expected [<group>] <module> or [<group>] maven/<groupId>/<artifactId>,"
                                    + " with a version written into the coordinate, as"
                                    + " maven/<groupId>/<artifactId>/<version>, or left to pin."
                                    + " Every line inside a jenesis.plugin comment is a declaration of its own");
                        }
                        String previous = entries.put(token.indexOf('/') < 0 ? "module/" + token : token, group);
                        if (previous != null && !previous.equals(group)) {
                            throw new IllegalArgumentException("Malformed jenesis.plugin declaration '"
                                    + trimmed
                                    + "': " + token + " is named in the group " + previous + " already,"
                                    + " and a coordinate resolves in one group of a module only");
                        }
                    }
                });
        return entries;
    }

    private static SequencedMap<String, String> toAliases(Node node) {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        toChildren(node)
                .filter(child -> child.getNodeType() == Node.COMMENT_NODE)
                .map(Node::getNodeValue)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> text.startsWith("jenesis.alias"))
                .forEach(text -> {
                    for (String line : text.substring("jenesis.alias".length()).replace("&#45;", "-").split("\n")) {
                        String declaration = line.trim().replaceAll("\\s+", " ");
                        if (declaration.isEmpty()) {
                            continue;
                        }
                        String[] words = declaration.split(" ");
                        if (words.length != 2) {
                            throw new IllegalArgumentException("Malformed jenesis.alias declaration '"
                                    + declaration
                                    + "': expected <module> <groupId>/<artifactId>[/<type>[/<classifier>]]."
                                    + " Every line inside a jenesis.alias comment is a declaration of its own,"
                                    + " so prose written among them is read as one; move it outside the comment");
                        }
                        if (ModuleGraph.isSystemModule(words[0]) || words[0].indexOf('/') >= 0) {
                            throw new IllegalArgumentException("Illegal jenesis.alias name '"
                                    + words[0]
                                    + "': expected the name of a module that is not a platform module");
                        }
                        String[] segments = words[1].split("/", -1);
                        if (segments.length < 2
                                || segments.length > 4
                                || Arrays.stream(segments).anyMatch(String::isEmpty)) {
                            throw new IllegalArgumentException("Malformed jenesis.alias target '"
                                    + words[1]
                                    + "': expected <groupId>/<artifactId>[/<type>[/<classifier>]]");
                        }
                        String previous = entries.putIfAbsent(words[0], words[1]);
                        if (previous != null && !previous.equals(words[1])) {
                            throw new IllegalArgumentException("Duplicate jenesis.alias for "
                                    + words[0]
                                    + ": "
                                    + previous
                                    + " and "
                                    + words[1]);
                        }
                    }
                });
        return entries;
    }

    private static SequencedMap<String, String> toAttachments(Node node) {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        toChildren(node)
                .filter(child -> child.getNodeType() == Node.COMMENT_NODE)
                .map(Node::getNodeValue)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> text.startsWith("jenesis.attach"))
                .forEach(text -> {
                    for (String line : text.substring("jenesis.attach".length()).replace("&#45;", "-").split("\n")) {
                        String trimmed = line.trim().replaceAll("\\s+", " ");
                        if (trimmed.isEmpty()) {
                            continue;
                        }
                        int space = trimmed.indexOf(' ');
                        String token = space < 0 ? trimmed : trimmed.substring(0, space);
                        String arguments = space < 0 ? "" : trimmed.substring(space + 1).trim();
                        if (ModuleGraph.isSystemModule(token)) {
                            throw new IllegalArgumentException("Illegal jenesis.attach token '"
                                    + token
                                    + "': platform modules cannot be attached");
                        }
                        String key;
                        int firstSlash = token.indexOf('/');
                        int secondSlash = firstSlash < 0 ? -1 : token.indexOf('/', firstSlash + 1);
                        if (firstSlash < 0) {
                            key = "main/module/" + token;
                        } else if (secondSlash < 0) {
                            if (firstSlash < 1 || firstSlash == token.length() - 1) {
                                throw new IllegalArgumentException("Malformed jenesis.attach token '"
                                        + token
                                        + "': expected <module>, <groupId>/<artifactId>,"
                                        + " or <group>/<repository>/<coordinate>");
                            }
                            key = "main/maven/" + token;
                        } else {
                            if (firstSlash < 1 || secondSlash == firstSlash + 1 || secondSlash == token.length() - 1) {
                                throw new IllegalArgumentException("Malformed jenesis.attach token '"
                                        + token
                                        + "': expected <module>, <groupId>/<artifactId>,"
                                        + " or <group>/<repository>/<coordinate>");
                            }
                            key = token;
                        }
                        String previous = entries.putIfAbsent(key, arguments);
                        if (previous != null && !previous.equals(arguments)) {
                            throw new IllegalArgumentException("Duplicate jenesis.attach for "
                                    + key
                                    + ": '"
                                    + previous
                                    + "' and '"
                                    + arguments
                                    + "'");
                        }
                    }
                });
        return entries;
    }

    private static SequencedSet<String> toNatives(Node node) {
        SequencedSet<String> entries = new LinkedHashSet<>();
        toChildren(node)
                .filter(child -> child.getNodeType() == Node.COMMENT_NODE)
                .map(Node::getNodeValue)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(text -> text.startsWith("jenesis.native"))
                .forEach(text -> {
                    String declaration = text.substring("jenesis.native".length()).trim();
                    if (declaration.isEmpty()) {
                        throw new IllegalArgumentException("A jenesis.native comment names no module:"
                                + " name each module or <groupId>/<artifactId> granted native access,"
                                + " the project's own included");
                    }
                    for (String token : declaration.split("\\s+")) {
                        if (ModuleGraph.isSystemModule(token)) {
                            throw new IllegalArgumentException("Illegal jenesis.native token '"
                                    + token
                                    + "': platform modules cannot be granted native access");
                        }
                        int firstSlash = token.indexOf('/');
                        int secondSlash = firstSlash < 0 ? -1 : token.indexOf('/', firstSlash + 1);
                        if (firstSlash < 0) {
                            entries.add("main/module/" + token);
                        } else if (secondSlash < 0 && firstSlash > 0 && firstSlash < token.length() - 1) {
                            entries.add("main/maven/" + token);
                        } else if (secondSlash > firstSlash + 1 && firstSlash > 0 && secondSlash < token.length() - 1) {
                            entries.add(token);
                        } else {
                            throw new IllegalArgumentException("Malformed jenesis.native token '"
                                    + token
                                    + "': expected <module>, <groupId>/<artifactId>,"
                                    + " or <group>/<repository>/<coordinate>");
                        }
                    }
                });
        return entries;
    }

    private static String property(String text, Map<String, String> properties) {
        return property(text, properties, Set.of());
    }

    private static String directory(String text, Map<String, String> properties) {
        Map<String, String> located = new HashMap<>(properties);
        located.put("basedir", ".");
        located.put("project.basedir", ".");
        return property(text, located);
    }

    private static String property(String text, Map<String, String> properties, Set<String> previous) {
        if (text != null && text.contains("$")) {
            Matcher matcher = PROPERTY.matcher(text);
            StringBuilder sb = new StringBuilder();
            while (matcher.find()) {
                String property = matcher.group(2);
                String replacement = properties.get(property);
                if (replacement == null) {
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(matcher.group()));
                } else {
                    HashSet<String> duplicates = new HashSet<>(previous);
                    if (!duplicates.add(property)) {
                        throw new IllegalStateException("Circular property definition of: " + property);
                    }
                    matcher.appendReplacement(sb, Matcher.quoteReplacement(property(replacement, properties, duplicates)));
                }
            }
            return matcher.appendTail(sb).toString();
        } else {
            return text;
        }
    }

    private static boolean isFloating(String version) {
        return version.equals("RELEASE")
                || version.equals("LATEST")
                || MavenDefaultVersionNegotiator.isRange(version);
    }

    private static MavenDependencyValue defaultScope(MavenDependencyValue value) {
        return value.scope() == null ? new MavenDependencyValue(value.version(),
                MavenDependencyScope.COMPILE,
                value.systemPath(),
                value.exclusions(),
                value.optional(),
                value.checksum()) : value;
    }

    private static MavenDependencyValue reconcile(MavenDependencyKey key,
                                                  MavenDependencyValue pinned,
                                                  MavenDependencyValue declared) {
        if (pinned.version() != null
                && declared.version() != null
                && !pinned.version().equals(declared.version())) {
            throw new IllegalStateException("Pinned version "
                    + pinned.version()
                    + " for "
                    + key.groupId() + ":" + key.artifactId()
                    + " conflicts with pinned version "
                    + declared.version());
        }
        if (pinned.checksum() != null
                && declared.checksum() != null
                && !pinned.checksum().equals(declared.checksum())) {
            throw new IllegalStateException("Conflicting checksums for "
                    + key.groupId() + ":" + key.artifactId() + ":" + pinned.version()
                    + " (" + pinned.checksum() + " and " + declared.checksum() + ")");
        }
        return merge(pinned, declared);
    }

    private static MavenDependencyValue merge(MavenDependencyValue left, MavenDependencyValue right) {
        return right == null ? left : new MavenDependencyValue(
                left.version() == null ? right.version() : left.version(),
                left.scope() == null ? right.scope() : left.scope(),
                left.systemPath() == null ? right.systemPath() : left.systemPath(),
                left.exclusions() == null ? right.exclusions() : left.exclusions(),
                left.optional() == null ? right.optional() : left.optional(),
                left.checksum() == null ? right.checksum() : left.checksum());
    }

    static Supplier<IllegalStateException> missing(String property) {
        return () -> new IllegalStateException("Property not defined: " + property);
    }

    private record DependencyKey(String groupId,
                                 String artifactId,
                                 String type,
                                 String classifier) {
        private MavenDependencyKey resolve(Map<String, String> properties) {
            return new MavenDependencyKey(property(groupId, properties),
                    property(artifactId, properties),
                    property(type, properties),
                    property(classifier, properties));
        }
    }

    private record DependencyValue(String version,
                                   String scope,
                                   String systemPath,
                                   List<MavenDependencyName> exclusions,
                                   String optional,
                                   String checksum) {
        private MavenDependencyValue resolve(Map<String, String> properties) {
            String resolvedScope = property(scope, properties);
            String resolvedVersion = property(version, properties);
            MavenDependencyKey.validate("version", resolvedVersion);
            int space = checksum == null ? -1 : checksum.indexOf(' ');
            return new MavenDependencyValue(resolvedVersion,
                    resolvedScope == null ? null : MavenDependencyScope.of(resolvedScope),
                    systemPath == null ? null : Path.of(property(systemPath, properties)),
                    exclusions == null ? null : exclusions.stream().map(exclusion -> new MavenDependencyName(
                            property(exclusion.groupId(), properties),
                            property(exclusion.artifactId(), properties))).toList(),
                    optional == null ? null : Boolean.valueOf(property(optional, properties)),
                    space < 0 ? checksum : checksum.substring(space + 1).trim().equals(resolvedVersion)
                            ? checksum.substring(0, space)
                            : null
            );
        }
    }

    private record DependencyCoordinate(String groupId, String artifactId, String version) {
    }

    private record ParentCoordinate(String groupId, String artifactId, String version, String relativePath) {
    }

    private record UnresolvedPom(String groupId,
                                 String artifactId,
                                 String version,
                                 String packaging,
                                 String sourceDirectory,
                                 List<String> resourceDirectories,
                                 String testSourceDirectory,
                                 List<String> testResourceDirectories,
                                 List<String> modules,
                                 Map<String, String> properties,
                                 Map<DependencyKey, DependencyValue> managedDependencies,
                                 SequencedMap<DependencyKey, DependencyValue> dependencies,
                                 SequencedMap<String, String> qualifiedDependencies,
                                 SequencedMap<String, String> attachments,
                                 SequencedSet<String> natives,
                                 SequencedMap<String, String> plugins,
                                 SequencedMap<String, String> aliases,
                                 SequencedMap<String, String> signatures,
                                 List<License> licenses,
                                 MavenPomEmitter.Metadata metadata,
                                 Set<String> verbatim,
                                 Map<DependencyKey, DependencyValue> bom,
                                 boolean missing,
                                 MavenPomEmitter.Relocation relocation,
                                 Set<DependencyKey> declaredManagement) {
    }

    private record ResolvedPom(Map<MavenDependencyKey, MavenDependencyValue> managedDependencies,
                               SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                               List<License> licenses,
                               DependencyCoordinate relocation) {
    }

    private record ContextualPom(ResolvedPom pom,
                                 boolean root,
                                 MavenDependencyScope scope,
                                 Set<MavenDependencyName> exclusions,
                                 MavenDependencyKey origin,
                                 String originVersion) {
    }

    private record PendingPom(CompletableFuture<ResolvedPom> pom,
                              MavenDependencyScope scope,
                              Set<MavenDependencyName> exclusions,
                              MavenDependencyKey origin,
                              String originVersion) {

        private ContextualPom awaited() throws IOException {
            try {
                return new ContextualPom(pom.join(), false, scope, exclusions, origin, originVersion);
            } catch (CompletionException e) {
                switch (e.getCause()) {
                    case IOException cause -> throw cause;
                    case RuntimeException cause -> throw cause;
                    case null, default -> throw e;
                }
            }
        }
    }

    private record Traversal(SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                             List<Resolver.Edge> edges,
                             SequencedMap<String, List<License>> licenses) {
    }

    private static class DependencyResolution {

        private final SequencedSet<String> observedVersions = new LinkedHashSet<>();
        private final Map<String, String> checksums = new HashMap<>();
        private String currentVersion;
        private MavenDependencyScope currentScope, widestScope;
        private Path systemPath;
        private List<MavenDependencyName> exclusions;
        private Boolean optional;

        private void bindChecksum(MavenDependencyKey key, String version, String checksum) {
            if (checksum == null) {
                return;
            }
            String existing = checksums.putIfAbsent(version, checksum);
            if (existing != null && !existing.equals(checksum)) {
                throw new IllegalStateException("Conflicting checksums for "
                        + key.groupId() + ":" + key.artifactId() + ":" + version
                        + " (" + existing + " and " + checksum + ")");
            }
        }

        private String selectChecksum() {
            String checksum = checksums.get(currentVersion);
            if (checksum != null) {
                return checksum;
            }
            if (checksums.size() == 1) {
                Map.Entry<String, String> only = checksums.entrySet().iterator().next();
                if (isFloating(only.getKey())) {
                    return only.getValue();
                }
            }
            return null;
        }

    }
}

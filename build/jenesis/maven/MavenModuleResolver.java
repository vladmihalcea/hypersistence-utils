package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.DependencyScope;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class MavenModuleResolver implements Resolver {

    private final String mavenPrefix;
    private final MavenResolver delegate;
    private final transient Repository discovery;

    public MavenModuleResolver(String mavenPrefix, MavenResolver delegate, Repository discovery) {
        this.mavenPrefix = mavenPrefix;
        this.delegate = delegate;
        this.discovery = discovery;
    }

    @Override
    public SequencedSet<String> managedPrefixes() {
        return new LinkedHashSet<>(Set.of(mavenPrefix));
    }

    @Override
    public Resolver.Bom bom(Executor executor,
                            String prefix,
                            Map<String, Repository> repositories,
                            String coordinate,
                            String version,
                            String checksum,
                            boolean latest) throws IOException {
        if (!latest || version.isEmpty()) {
            return Resolver.super.bom(executor, prefix, repositories, coordinate, version, checksum, latest);
        }
        Repository repository = repositories.getOrDefault(Resolver.base(prefix), discovery);
        RepositoryItem item = repository.fetch(executor, coordinate + ":pom").orElse(null);
        if (item == null) {
            return Resolver.super.bom(executor, prefix, repositories, coordinate, version, checksum, latest);
        }
        String discovered = pomVersion(item.toInputStream(), coordinate);
        Resolver.Resolved resolved = Resolver.materialize(executor,
                repository,
                coordinate + "/" + discovered + ":properties",
                null);
        return new Resolver.Bom(resolved.file(),
                resolved.internal(),
                true,
                discovered,
                Resolver.bomEntries(SequencedProperties.ofFiles(resolved.file())));
    }

    private static String pomVersion(InputStream stream, String coordinate) throws IOException {
        Document document;
        try (stream) {
            document = MavenDefaultVersionNegotiator.toDocumentBuilderFactory().newDocumentBuilder().parse(stream);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Failed to parse discovery POM for " + coordinate, e);
        }
        NodeList children = document.getDocumentElement().getChildNodes();
        String parent = null;
        for (int index = 0; index < children.getLength(); index++) {
            Node node = children.item(index);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            String name = node.getNodeName();
            if (name.equals("version")) {
                return node.getTextContent().trim();
            } else if (name.equals("parent")) {
                NodeList nested = node.getChildNodes();
                for (int inner = 0; inner < nested.getLength(); inner++) {
                    Node candidate = nested.item(inner);
                    if (candidate.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    if (candidate.getNodeName().equals("version")) {
                        parent = candidate.getTextContent().trim();
                    }
                }
            }
        }
        if (parent == null) {
            throw new IllegalStateException("No version in discovery POM for " + coordinate);
        }
        return parent;
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
        Repository repository = repositories.getOrDefault(Resolver.base(prefix), discovery);
        List<MavenResolver.RootPom> rootPoms = new ArrayList<>();
        SequencedMap<MavenDependencyKey, MavenDependencyValue> mavenPins = new LinkedHashMap<>();
        SequencedMap<String, Map.Entry<String, String>> classified = new LinkedHashMap<>();
        try {
            for (Map.Entry<String, SequencedSet<String>> entry : coordinates.entrySet()) {
                String coordinate = entry.getKey();
                String pinned = versions.get(coordinate);
                if (pinned != null && pinned.startsWith(":")) {
                    int space = pinned.indexOf(' ');
                    String qualifier = space < 0 ? pinned : pinned.substring(0, space);
                    int divider = qualifier.indexOf(':', 1);
                    String classifier = divider < 0 ? qualifier.substring(1) : qualifier.substring(1, divider);
                    String version = divider < 0 ? null : qualifier.substring(divider + 1);
                    if (classifier.isEmpty() || version != null && version.isEmpty()) {
                        throw new IllegalArgumentException("Malformed classifier '" + qualifier + "' for " + coordinate
                                + ": expected :<classifier> or :<classifier>:<version>");
                    }
                    classified.put(coordinate, Map.entry(classifier, space < 0 ? "" : pinned.substring(space + 1).trim()));
                    pinned = version;
                }
                MavenDependencyValue managed = toManagedValue(coordinate, pinned);
                String fetchCoord = managed == null
                        ? coordinate + ":pom"
                        : coordinate + "/" + managed.version() + ":pom";
                RepositoryItem item = repository.fetch(executor, fetchCoord)
                        .orElseThrow(() -> new IllegalArgumentException("The module name " + coordinate
                                + " has no artifact behind it"
                                + (managed == null ? "" : " in version " + managed.version())
                                + ": no POM is found for it, as the module repositories name no Maven artifact"
                                + " that declares it - which is so for a jar that declares no module name and is"
                                + " required by the name its file name derives - or the artifact they name"
                                + " publishes no POM; map the name to its artifact with @jenesis.alias "
                                + coordinate + " <groupId>/<artifactId> in module-info.java, or with"
                                + " <!--jenesis.alias " + coordinate + " <groupId>/<artifactId>--> in pom.xml"
                                + (managed == null ? "" : ", or pin a version the artifact is published in")));
                List<MavenDependencyName> exclusions = null;
                if (!entry.getValue().isEmpty()) {
                    exclusions = new ArrayList<>();
                    for (String exclude : entry.getValue()) {
                        int separator = exclude.indexOf('/');
                        if (separator < 1 || separator == exclude.length() - 1) {
                            throw new IllegalArgumentException("Malformed exclusion '"
                                    + exclude
                                    + "' for "
                                    + coordinate
                                    + ": expected <groupId>/<artifactId>");
                        }
                        exclusions.add(new MavenDependencyName(
                                exclude.substring(0, separator), exclude.substring(separator + 1)));
                    }
                }
                rootPoms.add(new MavenResolver.RootPom(item.toInputStream(),
                        managed == null ? null : managed.checksum(),
                        coordinate,
                        managed != null,
                        exclusions));
            }
            for (Map.Entry<String, String> pin : versions.entrySet()) {
                if (pin.getKey().indexOf('/') < 0) {
                    continue;
                }
                MavenDependencyValue managed = toManagedValue(pin.getKey(), pin.getValue());
                if (managed != null) {
                    mavenPins.put(MavenDependencyKey.parseKey(pin.getKey()), managed);
                }
            }
            managedExclusions.forEach((coordinate, excludes) -> mavenPins.merge(MavenDependencyKey.parseKey(coordinate),
                    new MavenDependencyValue(null, null, null, excludes.stream().map(exclude -> {
                        int separator = exclude.indexOf('/');
                        if (separator < 1 || separator == exclude.length() - 1) {
                            throw new IllegalArgumentException("Malformed managed exclusion '" + exclude + "' for "
                                    + coordinate + ": expected <groupId>/<artifactId>");
                        }
                        return new MavenDependencyName(exclude.substring(0, separator), exclude.substring(separator + 1));
                    }).toList(), null, null),
                    (pinned, excluded) -> pinned.exclusions(excluded.exclusions())));
        } catch (RuntimeException | IOException e) {
            for (MavenResolver.RootPom opened : rootPoms) {
                try {
                    opened.pom().close();
                } catch (IOException suppressed) {
                    e.addSuppressed(suppressed);
                }
            }
            throw e;
        }
        MavenRepository mavenRepo = MavenRepository.of(repositories.getOrDefault(mavenPrefix, Repository.empty()));
        MavenResolver.Closure resolution = delegate.dependencies(
                executor, mavenRepo, rootPoms, mavenPins, MavenDependencyScope.COMPILE, mavenPrefix);
        SequencedMap<MavenDependencyKey, MavenDependencyValue> closure = new LinkedHashMap<>();
        Map<MavenDependencyKey, String> checksums = new HashMap<>();
        resolution.dependencies().forEach((key, value) -> {
            Map.Entry<String, String> variant = resolution.roots().entrySet().stream()
                    .filter(root -> root.getValue().equals(key) && classified.containsKey(root.getKey()))
                    .map(root -> classified.get(root.getKey()))
                    .findFirst()
                    .orElse(null);
            if (variant == null || !key.jar()) {
                closure.put(key, value);
            } else {
                MavenDependencyKey selected = new MavenDependencyKey(key.groupId(), key.artifactId(), "jar", variant.getKey());
                closure.put(selected, value);
                String pinned = versions.getOrDefault(selected.coordinate(null, null), "");
                int space = pinned.indexOf(' ');
                checksums.put(selected,
                        !variant.getValue().isEmpty() || space < 0 || !pinned.substring(0, space).equals(value.version())
                                ? variant.getValue()
                                : pinned.substring(space + 1).trim());
            }
        });
        SequencedMap<String, MavenDependencyKey> roots = new LinkedHashMap<>();
        resolution.roots().forEach((module, key) -> roots.put(module,
                classified.containsKey(module) && key.jar()
                        ? new MavenDependencyKey(key.groupId(), key.artifactId(), "jar", classified.get(module).getKey())
                        : key));
        SequencedMap<String, String> result = new LinkedHashMap<>();
        closure.forEach((key, value) -> {
            if (key.jar()) {
                result.put(key.coordinate(mavenPrefix, value.version()),
                        checksums.getOrDefault(key, value.checksum() == null ? "" : value.checksum()));
            }
        });
        SequencedMap<String, Resolver.Resolved> materialized = new LinkedHashMap<>(
                Resolver.materializeAll(executor, repositories, mavenPrefix, result));
        Map<String, ModuleDescriptor> descriptors = new ConcurrentHashMap<>();
        List<CompletableFuture<?>> pending = new ArrayList<>();
        closure.forEach((key, value) -> {
            String withVersion = key.coordinate(mavenPrefix, value.version());
            Resolver.Resolved artifact = materialized.get(withVersion);
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
        roots.forEach((module, key) -> {
            MavenDependencyValue value = closure.get(key);
            if (value == null) {
                return;
            }
            String withVersion = key.coordinate(mavenPrefix, value.version());
            Resolver.Resolved artifact = materialized.get(withVersion);
            if (artifact == null || artifact.internal()) {
                return;
            }
            ModuleDescriptor descriptor = descriptors.get(withVersion);
            if (descriptor != null && !descriptor.name().equals(module)) {
                throw new IllegalStateException("Expected module " + module
                        + " but the resolved jar for " + withVersion + " declares module " + descriptor.name());
            }
            boolean unversioned = descriptor != null
                    && !descriptor.isAutomatic()
                    && descriptor.rawVersion().isEmpty();
            materialized.putIfAbsent("module/" + module + (unversioned ? "" : "/" + value.version()),
                    new Resolver.Resolved(artifact.file(), "", artifact.internal()));
        });
        SequencedMap<String, Resolver.Vertex> nodes = new LinkedHashMap<>();
        closure.forEach((key, value) -> {
            String withVersion = key.coordinate(mavenPrefix, value.version());
            ModuleDescriptor descriptor = descriptors.get(withVersion);
            Resolver.Resolved artifact = materialized.get(withVersion);
            nodes.put(key.coordinate(mavenPrefix, null), new Resolver.Vertex(
                    value.version(),
                    descriptor == null ? null : descriptor.name(),
                    descriptor != null && descriptor.isAutomatic(),
                    artifact != null && artifact.internal(),
                    resolution.licenses().getOrDefault(withVersion, List.of())));
        });
        return new Resolver.Resolution(materialized, resolution.edges(), nodes);
    }

    private static MavenDependencyValue toManagedValue(String coordinate, String pinned) {
        if (pinned == null || pinned.isEmpty()) {
            return null;
        }
        int space = pinned.indexOf(' ');
        String version = space < 0 ? pinned : pinned.substring(0, space);
        if (version.startsWith(":")) {
            throw new IllegalArgumentException("Malformed pin " + version + " for " + coordinate
                    + ": a Maven coordinate names its classifier in the coordinate, as"
                    + " maven/<groupId>/<artifactId>/jar/<classifier>");
        }
        return new MavenDependencyValue(version,
                null,
                null,
                null,
                null,
                space < 0 ? null : pinned.substring(space + 1).trim());
    }
}

package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.Environment;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;
import build.jenesis.module.JenesisRepository;

public class MavenModuleRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();
    private static final int DEFAULT_SEGMENTS = 2;

    private final MavenRepository repository;
    private final String group;
    private final int segments;
    private final Map<String, MavenDependencyKey> mapping;
    private final DocumentBuilderFactory documentBuilderFactory;
    private final Map<MavenDependencyName, Optional<Metadata>> metadata = new ConcurrentHashMap<>();

    public MavenModuleRepository() {
        this(MavenDefaultRepository.of());
    }

    public MavenModuleRepository(MavenRepository repository) {
        this(repository, null, segments(), null, MavenDefaultVersionNegotiator.toDocumentBuilderFactory());
    }

    public static MavenModuleRepository ofEnvironment(Environment environment, MavenRepository repository) {
        return new MavenModuleRepository(repository).segments(environment.number("maven.segments", DEFAULT_SEGMENTS));
    }

    private MavenModuleRepository(MavenRepository repository,
                                  String group,
                                  int segments,
                                  Map<String, MavenDependencyKey> mapping,
                                  DocumentBuilderFactory documentBuilderFactory) {
        this.repository = repository;
        this.group = group;
        this.segments = segments;
        this.mapping = mapping;
        this.documentBuilderFactory = documentBuilderFactory;
    }

    public MavenModuleRepository repository(MavenRepository repository) {
        return new MavenModuleRepository(repository, group, segments, mapping, documentBuilderFactory);
    }

    public MavenModuleRepository group(String group) {
        if (group != null) {
            SAFE_SEGMENT.accept("group id", group);
        }
        return new MavenModuleRepository(repository, group, segments, mapping, documentBuilderFactory);
    }

    public MavenModuleRepository segments(int segments) {
        return new MavenModuleRepository(repository, group, checkedSegments(segments), mapping, documentBuilderFactory);
    }

    public MavenModuleRepository mapping(Map<String, MavenDependencyKey> mapping) {
        return new MavenModuleRepository(repository,
                group,
                segments,
                mapping == null ? null : Map.copyOf(mapping),
                documentBuilderFactory);
    }

    public static int segments() {
        return DEFAULT_SEGMENTS;
    }

    public static int checkedSegments(int segments) {
        if (segments < 1) {
            throw new IllegalArgumentException("Expected at least one leading segment of a module name to form "
                    + "the group id but got " + segments);
        }
        return segments;
    }

    public static String groupId(String module) {
        return groupId(module, segments());
    }

    public static String groupId(String module, int segments) {
        int limit = checkedSegments(segments), index = -1;
        for (int count = 0; count < limit; count++) {
            int next = module.indexOf('.', index + 1);
            if (next < 0) {
                return module;
            }
            index = next;
        }
        return module.substring(0, index);
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor,
                                          String module,
                                          String classifier,
                                          String version,
                                          String type) throws IOException {
        SAFE_SEGMENT.accept("module name", module);
        if (classifier != null) {
            SAFE_SEGMENT.accept("classifier", classifier);
        }
        if (version != null) {
            SAFE_SEGMENT.accept("version", version);
        }
        String groupId, artifactId, suffix = type == null ? "jar" : type;
        if (mapping == null) {
            groupId = group == null ? groupId(module, segments) : group;
            artifactId = module;
        } else {
            MavenDependencyKey mapped = mapping.get(module);
            if (mapped == null) {
                return Optional.empty();
            }
            groupId = mapped.groupId();
            artifactId = mapped.artifactId();
            if (classifier == null) {
                classifier = mapped.classifier();
            }
            if (suffix.equals("jar") || suffix.startsWith("jar.")) {
                suffix = mapped.type() + suffix.substring("jar".length());
            }
        }
        int dot = suffix.indexOf('.');
        String resolved;
        if (version == null || version.equals("RELEASE") || version.equals("LATEST")) {
            Metadata published = metadata(executor, groupId, artifactId).orElse(null);
            if (published == null) {
                return Optional.empty();
            }
            resolved = published.resolve(version, groupId, artifactId);
        } else {
            resolved = version;
        }
        return repository.fetch(executor,
                groupId,
                artifactId,
                resolved,
                dot < 0 ? suffix : suffix.substring(0, dot),
                classifier,
                dot < 0 ? null : suffix.substring(dot + 1));
    }

    private Optional<Metadata> metadata(Executor executor, String groupId, String artifactId) throws IOException {
        MavenDependencyName name = new MavenDependencyName(groupId, artifactId);
        Optional<Metadata> cached = metadata.get(name);
        if (cached == null) {
            RepositoryItem item = repository.fetchMetadata(executor, groupId, artifactId, null).orElse(null);
            cached = item == null ? Optional.empty() : Optional.of(toMetadata(item, groupId, artifactId));
            metadata.put(name, cached);
        }
        return cached;
    }

    private Metadata toMetadata(RepositoryItem item, String groupId, String artifactId) throws IOException {
        Document document;
        try (InputStream inputStream = item.toInputStream()) {
            document = documentBuilderFactory.newDocumentBuilder().parse(inputStream);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Failed to parse the Maven metadata of "
                    + groupId + ":" + artifactId, e);
        }
        Node versioning = MavenPomResolver.toChildren(document.getDocumentElement())
                .filter(node -> Objects.equals(node.getNodeName(), "versioning"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No versioning element in the Maven metadata of "
                        + groupId + ":" + artifactId));
        return new Metadata(element(versioning, "release"),
                element(versioning, "latest"),
                MavenPomResolver.toChildren(versioning)
                        .filter(node -> Objects.equals(node.getNodeName(), "versions"))
                        .flatMap(MavenPomResolver::toChildren)
                        .filter(node -> Objects.equals(node.getNodeName(), "version"))
                        .map(node -> node.getTextContent().trim())
                        .filter(value -> !value.isEmpty())
                        .toList());
    }

    private static String element(Node versioning, String name) {
        return MavenPomResolver.toChildren(versioning)
                .filter(node -> Objects.equals(node.getNodeName(), name))
                .findFirst()
                .map(node -> node.getTextContent().trim())
                .filter(value -> !value.isEmpty())
                .orElse(null);
    }

    private record Metadata(String release, String latest, List<String> versions) {

        private String resolve(String version, String groupId, String artifactId) {
            if (Objects.equals(version, "LATEST")) {
                return latest == null ? newest(version, groupId, artifactId, _ -> true) : latest;
            }
            if (release != null && !MavenDefaultVersionNegotiator.isStable(release)) {
                return versions.stream()
                        .filter(MavenDefaultVersionNegotiator::isStable)
                        .max(MavenDefaultVersionNegotiator::compareVersions)
                        .orElse(release);
            }
            return release == null
                    ? newest(version, groupId, artifactId, MavenDefaultVersionNegotiator::isStable)
                    : release;
        }

        private String newest(String version, String groupId, String artifactId, Predicate<String> predicate) {
            return versions.stream()
                    .filter(predicate)
                    .max(MavenDefaultVersionNegotiator::compareVersions)
                    .orElseThrow(() -> new IllegalStateException("No version of "
                            + groupId + ":" + artifactId
                            + " resolves " + (version == null ? "RELEASE" : version)
                            + " in its Maven metadata: " + versions
                            + " (pin a version of the requiring module or publish a release)"));
        }
    }
}

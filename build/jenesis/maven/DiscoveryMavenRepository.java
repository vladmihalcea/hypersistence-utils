package build.jenesis.maven;

import module java.base;
import build.jenesis.DiscoveredLocation;
import build.jenesis.Discovery;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;

public class DiscoveryMavenRepository implements MavenRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final Discovery discovery;
    private final Path local;
    private final Repository.Connection connection;
    private final Consumer<String> printing;
    private final Palette palette;
    private final Map<URI, MavenRepository> repositories = new ConcurrentHashMap<>();

    public DiscoveryMavenRepository() {
        this(new Discovery(), null, new Repository.Connection(), null, Palette.NONE);
    }

    public static DiscoveryMavenRepository ofEnvironment(Environment environment) {
        return ofEnvironment(environment, Discovery.ofEnvironment(environment));
    }

    public static DiscoveryMavenRepository ofEnvironment(Environment environment, Discovery discovery) {
        return new DiscoveryMavenRepository(discovery,
                                            MavenDefaultRepository.localRepository(environment),
                                            Repository.Connection.ofEnvironment(environment),
                                            environment.flag("print.fetch") ? environment.out() : null,
                                            Palette.ofEnvironment(environment));
    }

    public DiscoveryMavenRepository(Discovery discovery,
                                    Path local,
                                    Repository.Connection connection,
                                    Consumer<String> printing,
                                    Palette palette) {
        this.discovery = discovery;
        this.local = local;
        this.connection = connection;
        this.printing = printing;
        this.palette = palette;
    }

    public DiscoveryMavenRepository discovery(Discovery discovery) {
        return new DiscoveryMavenRepository(discovery, local, connection, printing, palette);
    }

    public DiscoveryMavenRepository local(Path local) {
        return new DiscoveryMavenRepository(discovery, local, connection, printing, palette);
    }

    public DiscoveryMavenRepository connection(Repository.Connection connection) {
        return new DiscoveryMavenRepository(discovery, local, connection, printing, palette);
    }

    public DiscoveryMavenRepository printing(Consumer<String> printing, Palette palette) {
        return new DiscoveryMavenRepository(discovery, local, connection, printing, palette);
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor,
                                          String groupId,
                                          String artifactId,
                                          String version,
                                          String type,
                                          String classifier,
                                          String checksum) throws IOException {
        DiscoveredLocation location = discovery.lookup(groupId, "maven", artifactId).orElse(null);
        if (location == null || !location.admits(version)) {
            return Optional.empty();
        }
        if (!location.template()) {
            return repository(location.root(connection))
                    .fetch(executor, groupId, artifactId, version, type, classifier, checksum);
        }
        String extension = (type == null ? "jar" : type) + (checksum == null ? "" : "." + checksum);
        SAFE_SEGMENT.accept("artifact id", artifactId);
        SAFE_SEGMENT.accept("version", version);
        SAFE_SEGMENT.accept("type", extension);
        if (classifier != null) {
            SAFE_SEGMENT.accept("classifier", classifier);
        }
        Map<String, String> values = new HashMap<>();
        values.put("groupId", groupId);
        values.put("groupPath", groupId.replace('.', '/'));
        values.put("artifactId", artifactId);
        values.put("version", version);
        values.put("-classifier", classifier == null ? "" : "-" + classifier);
        values.put("type", extension);
        return location.fetch(values, checksum == null, connection, printing, palette);
    }

    @Override
    public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String checksum) throws IOException {
        DiscoveredLocation location = discovery.lookup(groupId, "maven", artifactId).orElse(null);
        if (location == null) {
            return Optional.empty();
        }
        if (location.template()) {
            SAFE_SEGMENT.accept("artifact id", artifactId);
            if (checksum != null) {
                return Optional.empty();
            }
            Map<String, String> values = Map.of("groupId", groupId,
                    "groupPath", groupId.replace('.', '/'),
                    "artifactId", artifactId);
            if (location.listsVersions()) {
                MavenMetadata listed = location.metadata(values, connection).orElse(null);
                return listed == null ? Optional.empty() : Optional.of(listed.toItem());
            }
            String version = location.latest(values, connection).orElse(null);
            if (version == null || !location.admits(version)) {
                return Optional.empty();
            }
            return Optional.of(new MavenMetadata(groupId,
                    artifactId,
                    version,
                    MavenDefaultVersionNegotiator.isStable(version) ? version : null,
                    null,
                    List.of(version)).toItem());
        }
        MavenRepository repository = repository(location.root(connection));
        return (location.since() == null && location.suffixes() == null
                ? repository
                : repository.versions(location::admits)).fetchMetadata(executor, groupId, artifactId, checksum);
    }

    private MavenRepository repository(URI root) {
        return repositories.computeIfAbsent(root, _ -> new MavenDefaultRepository(
                root,
                local,
                MavenDefaultRepository.validations(root),
                null).connection(connection).printing(printing, palette));
    }
}

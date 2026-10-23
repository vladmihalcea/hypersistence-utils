package build.jenesis;

import module java.base;
import build.jenesis.maven.MavenDependencyKey;

public class DiscoverySources implements Repository {

    public static final String NAME = "sources";

    private final Discovery discovery;
    private final Repository.Connection connection;

    public DiscoverySources(Discovery discovery) {
        this(discovery, new Repository.Connection());
    }

    public static DiscoverySources ofEnvironment(Environment environment, Discovery discovery) {
        return new DiscoverySources(discovery, Repository.Connection.ofEnvironment(environment));
    }

    public DiscoverySources(Discovery discovery, Repository.Connection connection) {
        this.discovery = discovery;
        this.connection = connection;
    }

    public DiscoverySources discovery(Discovery discovery) {
        return new DiscoverySources(discovery, connection);
    }

    public DiscoverySources connection(Repository.Connection connection) {
        return new DiscoverySources(discovery, connection);
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension) throws IOException {
        int slash = coordinate.indexOf('/');
        if (extension != null || slash < 0) {
            return Optional.empty();
        }
        String rest = coordinate.substring(slash + 1), namespace, name, version;
        Map<String, String> values = new HashMap<>();
        switch (coordinate.substring(0, slash)) {
            case "maven" -> {
                MavenDependencyKey.Versioned parsed = MavenDependencyKey.tryParse(rest);
                namespace = parsed.key().groupId();
                name = parsed.key().artifactId();
                version = parsed.version();
                values.put("groupId", namespace);
                values.put("groupPath", namespace.replace('.', '/'));
                values.put("artifactId", name);
                values.put("module", null);
                values.put("-suffix", null);
            }
            case "module" -> {
                int last = rest.lastIndexOf('/');
                namespace = name = last < 0 ? rest : rest.substring(0, last);
                version = last < 0 ? null : rest.substring(last + 1);
                values.put("groupId", null);
                values.put("groupPath", null);
                values.put("artifactId", null);
                values.put("module", name);
            }
            default -> {
                return Optional.empty();
            }
        }
        if (version == null) {
            return Optional.empty();
        }
        DiscoveredLocation location = discovery.lookup(namespace, NAME, name).orElse(null);
        if (location == null || !location.admits(version)) {
            return Optional.empty();
        }
        if (!location.template() || location.coordinate() || !location.target().contains("{version}")) {
            throw new IllegalArgumentException(location.key() + " in " + location.source() + " names "
                    + location.target() + ", where it expects a template of the source archive of a version, such as"
                    + " https://github.com/<owner>/<repository>/archive/refs/tags/v{version}.zip");
        }
        if (values.get("module") != null) {
            values.put("-suffix", location.suffix(name));
        }
        values.put("version", version);
        return location.resolve(values, connection)
                .map(uri -> () -> new ByteArrayInputStream(uri.toString().getBytes(StandardCharsets.UTF_8)));
    }
}

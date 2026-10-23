package build.jenesis.module;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultVersionNegotiator;
public class JenesisRawGitRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();
    private static final String GITHUB_DATA =
            "https://raw.githubusercontent.com/jenesis/jenesis-modules/main/data/modules/";

    private final Scope scope;
    private final URI data;
    private final URI repository;
    private final String token;
    private final Predicate<String> predicate;
    private final Repository.Connection connection;
    private final Boolean prerelease;
    private final Boolean speculative;
    private final Consumer<String> printing;
    private final Palette palette;
    private final Map<String, Optional<String>> tsvCache = new ConcurrentHashMap<>();

    public JenesisRawGitRepository(Scope scope, URI data, URI repository) {
        this(scope, data, repository, null);
    }

    public JenesisRawGitRepository(Scope scope, URI data, URI repository, String token) {
        this(Environment.NONE, scope, data, repository, token);
    }

    public static JenesisRawGitRepository ofEnvironment(Environment environment,
                                                        Scope scope,
                                                        URI data,
                                                        URI repository,
                                                        String token) {
        return new JenesisRawGitRepository(environment, scope, data, repository, token);
    }

    private JenesisRawGitRepository(Environment environment,
                                    Scope scope,
                                    URI data,
                                    URI repository,
                                    String token) {
        this(scope,
             trailingSlash(data),
             trailingSlash(repository),
             token,
             _ -> true,
             Repository.Connection.ofEnvironment(environment),
             environment.flagOrNull("module.prerelease"),
             environment.flagOrNull("module.speculative"),
             environment.flag("print.fetch") ? environment.out() : null,
             Palette.ofEnvironment(environment));
    }

    private JenesisRawGitRepository(Scope scope,
                                    URI data,
                                    URI repository,
                                    String token,
                                    Predicate<String> predicate,
                                    Repository.Connection connection,
                                    Boolean prerelease,
                                    Boolean speculative,
                                    Consumer<String> printing,
                                    Palette palette) {
        this.scope = scope;
        this.data = data;
        this.repository = repository;
        this.token = token;
        this.predicate = predicate;
        this.connection = connection;
        this.prerelease = prerelease;
        this.speculative = speculative;
        this.printing = printing;
        this.palette = palette;
    }

    public JenesisRawGitRepository groups(Predicate<String> predicate) {
        return new JenesisRawGitRepository(scope,
                                           data,
                                           repository,
                                           token,
                                           predicate,
                                           connection,
                                           prerelease,
                                           speculative,
                                           printing,
                                           palette);
    }

    public JenesisRawGitRepository connection(Repository.Connection connection) {
        return new JenesisRawGitRepository(scope,
                                           data,
                                           repository,
                                           token,
                                           predicate,
                                           connection,
                                           prerelease,
                                           speculative,
                                           printing,
                                           palette);
    }

    public JenesisRawGitRepository prerelease(Boolean prerelease) {
        return new JenesisRawGitRepository(scope,
                                           data,
                                           repository,
                                           token,
                                           predicate,
                                           connection,
                                           prerelease,
                                           speculative,
                                           printing,
                                           palette);
    }

    public JenesisRawGitRepository speculative(Boolean speculative) {
        return new JenesisRawGitRepository(scope,
                                           data,
                                           repository,
                                           token,
                                           predicate,
                                           connection,
                                           prerelease,
                                           speculative,
                                           printing,
                                           palette);
    }

    public JenesisRawGitRepository printing(Consumer<String> printing, Palette palette) {
        return new JenesisRawGitRepository(scope,
                                           data,
                                           repository,
                                           token,
                                           predicate,
                                           connection,
                                           prerelease,
                                           speculative,
                                           printing,
                                           palette);
    }

    public static JenesisRepository of(Scope scope) {
        return ofEnvironment(Environment.NONE, scope);
    }

    public static JenesisRepository ofEnvironment(Environment environment, Scope scope) {
        Repository.Credential credential = Repository.Credential.of(environment, "maven.token", "MAVEN_REPOSITORY_TOKEN");
        String index = environment.getProperty("module.index", System.getenv("JENESIS_INDEX_URI"));
        String property = environment.getProperty("maven.uri");
        String variable = System.getenv("MAVEN_REPOSITORY_URI");
        Set<String> visited = new HashSet<>();
        String text;
        Repository.Origin origin;
        if (property != null) {
            text = property;
            origin = Repository.Origin.of(environment, "maven.uri");
        } else if (variable != null) {
            text = variable;
            visited.add("MAVEN_REPOSITORY_URI");
            origin = Repository.Origin.ENVIRONMENT;
        } else {
            text = "https://repo1.maven.org/maven2/";
            origin = Repository.Origin.DEFAULT;
        }
        JenesisRepository repository = chain(environment,
                                            text,
                                            visited,
                                            scope,
                                            credential,
                                            URI.create(index == null ? GITHUB_DATA : index),
                                            origin,
                                            null,
                                            null);
        if (repository == null) {
            throw new IllegalStateException("No Maven repository is configured by: " + text);
        }
        return repository;
    }

    private static JenesisRepository chain(Environment environment,
                                           String text,
                                           Set<String> visited,
                                           Scope scope,
                                           Repository.Credential credential,
                                           URI index,
                                           Repository.Origin origin,
                                           Predicate<String> inherited,
                                           JenesisRepository repository) {
        for (String entry : text.split(",")) {
            String candidate = entry.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            int separator = candidate.indexOf('|');
            String location = (separator < 0 ? candidate : candidate.substring(0, separator)).strip();
            if (location.isEmpty()) {
                throw new IllegalStateException("No URI in Maven repository entry: " + candidate);
            }
            List<String> groups = new ArrayList<>();
            if (separator >= 0) {
                for (String argument : candidate.substring(separator + 1).split("\\|")) {
                    String group = argument.strip();
                    if (!group.isEmpty()) {
                        groups.add(group);
                    }
                }
            }
            Predicate<String> own = groups.isEmpty() ? null : value -> {
                for (String group : groups) {
                    if (value.equals(group) || value.startsWith(group + ".")) {
                        return true;
                    }
                }
                return false;
            };
            Predicate<String> effective;
            if (inherited == null) {
                effective = own;
            } else if (own == null) {
                effective = inherited;
            } else {
                Predicate<String> combining = own;
                effective = value -> inherited.test(value) && combining.test(value);
            }
            Repository.Credential granted = repository == null ? credential : credential.token(null);
            JenesisRepository current;
            if (location.startsWith("@")) {
                String name = location.substring(1);
                String value;
                Repository.Origin spliced;
                if (name.isEmpty()) {
                    String variable = System.getenv("MAVEN_REPOSITORY_URI");
                    if (variable != null && visited.add("MAVEN_REPOSITORY_URI")) {
                        name = "MAVEN_REPOSITORY_URI";
                        value = variable;
                        spliced = Repository.Origin.ENVIRONMENT;
                    } else {
                        name = null;
                        value = "https://repo1.maven.org/maven2/";
                        spliced = Repository.Origin.DEFAULT;
                    }
                } else {
                    String declared = environment.getProperty(name);
                    value = declared == null ? System.getenv(name) : declared;
                    spliced = declared == null ? Repository.Origin.ENVIRONMENT : Repository.Origin.of(environment, name);
                    if (value == null) {
                        throw new IllegalStateException("Unresolved repository reference: @" + name);
                    }
                    if (!visited.add(name)) {
                        throw new IllegalStateException("Circular repository reference: @" + name);
                    }
                }
                current = chain(environment, value, visited, scope, granted, index, spliced, effective, null);
                if (name != null) {
                    visited.remove(name);
                }
                if (current == null) {
                    throw new IllegalStateException("No Maven repository is configured by: " + value);
                }
            } else {
                JenesisRawGitRepository base = ofEnvironment(environment,
                                                             scope,
                                                             index,
                                                             URI.create(location),
                                                             granted.grant(origin));
                current = effective == null ? base : base.groups(effective);
            }
            repository = repository == null ? current : current.prepend(repository);
        }
        return repository;
    }

    private static URI trailingSlash(URI uri) {
        String text = uri.toString();
        return text.endsWith("/") ? uri : URI.create(text + "/");
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
        Coordinate resolved = resolve(module, classifier, version);
        if (resolved == null || !predicate.test(resolved.groupId())) {
            return Optional.empty();
        }
        SAFE_SEGMENT.accept("resolved groupId", resolved.groupId());
        SAFE_SEGMENT.accept("resolved artifactId", resolved.artifactId());
        SAFE_SEGMENT.accept("resolved version", resolved.version());
        String path = resolved.groupId().replace('.', '/')
                + "/" + resolved.artifactId()
                + "/" + resolved.version()
                + "/" + resolved.artifactId() + "-" + resolved.version()
                + (classifier == null ? "" : "-" + classifier) + "." + type;
        URI base = repository.normalize();
        URI location = base.resolve(path).normalize();
        URI contained = base.relativize(location);
        if (contained.isAbsolute() || contained.getPath().startsWith("..")) {
            throw new IllegalArgumentException("Resolved location " + location + " escapes repository root " + repository);
        }
        return open(connection, location, token).map(stream -> {
            if (printing != null) {
                printing.accept("%s%-11s%s %s".formatted(palette.info(),
                        "[FETCHED]",
                        palette.reset(),
                        location));
            }
            AtomicReference<InputStream> first = new AtomicReference<>(stream);
            return (RepositoryItem) () -> {
                InputStream reopened = first.getAndSet(null);
                return reopened != null ? reopened : Repository.open(connection, location, token);
            };
        });
    }

    private Coordinate resolve(String moduleName, String classifier, String version) throws IOException {
        String tsvName = (scope == Scope.MODULE ? "modules" : "artifacts")
                + (classifier == null ? "" : "-" + classifier) + ".tsv";
        URI tsvUri = data.resolve(moduleName.replace('.', '/') + "/" + tsvName);
        Optional<String> tsv = tsvCache.get(tsvUri.toString());
        if (tsv == null) {
            Optional<InputStream> stream = open(connection, tsvUri, null);
            if (stream.isEmpty()) {
                tsv = Optional.empty();
            } else {
                try (InputStream open = stream.get()) {
                    tsv = Optional.of(new String(open.readAllBytes(), StandardCharsets.UTF_8));
                }
            }
            tsvCache.put(tsvUri.toString(), tsv);
        }
        return tsv.isEmpty() ? null : pickRow(tsv.get(), version);
    }

    private Coordinate pickRow(String tsv, String version) {
        Coordinate newest = null;
        for (String line : tsv.split("\n")) {
            if (line.isEmpty()) {
                continue;
            }
            String[] columns = line.split("\t");
            if (columns.length < 4) {
                continue;
            }
            Coordinate row = scope == Scope.MODULE
                    ? new Coordinate(columns[1], columns[2], columns[3])
                    : new Coordinate(columns[2], columns[3], columns[0]);
            if (newest == null) {
                newest = row;
            }
            if (version == null
                    ? prerelease != null && prerelease
                            || MavenDefaultVersionNegotiator.isStable(columns[0])
                            && MavenDefaultVersionNegotiator.isStable(row.version())
                    : columns[0].equals(version)) {
                return row;
            }
        }
        if (version != null && newest != null && (speculative == null || speculative)) {
            return new Coordinate(newest.groupId(), newest.artifactId(), version);
        }
        return null;
    }

    private static Optional<InputStream> open(Repository.Connection connection,
                                              URI uri,
                                              String token) throws IOException {
        try {
            return Optional.of(Repository.open(connection, uri, token));
        } catch (FileNotFoundException _) {
            return Optional.empty();
        }
    }

    private record Coordinate(String groupId, String artifactId, String version) {
    }
}

package build.jenesis.module;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenDependencyKey;
import build.jenesis.maven.MavenModuleRepository;

public class JenesisModuleRepository implements JenesisRepository {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();
    private static final String MODULE = "module", MAVEN = "maven", MAPPED = "mapped";

    private final URI root;
    private final String token;
    private final Repository.Connection connection;
    private final URI maven;
    private final Boolean prerelease;
    private final Boolean speculative;
    private final Consumer<String> printing;
    private final Palette palette;

    public static JenesisRepository of(Scope scope) {
        return ofEnvironment(Environment.NONE, scope);
    }

    public static JenesisRepository ofEnvironment(Environment environment, Scope scope) {
        Repository.Credential credential = Repository.Credential.of(environment,
                                                                    "module.token",
                                                                    "JENESIS_REPOSITORY_TOKEN");
        Repository.Credential maven = Repository.Credential.of(environment, "maven.token", "MAVEN_REPOSITORY_TOKEN");
        String property = environment.getProperty("module.uri");
        String variable = System.getenv("JENESIS_REPOSITORY_URI");
        Set<String> visited = new HashSet<>();
        String text;
        Repository.Origin origin;
        if (property != null) {
            text = property;
            origin = Repository.Origin.of(environment, "module.uri");
        } else if (variable != null) {
            text = variable;
            visited.add("JENESIS_REPOSITORY_URI");
            origin = Repository.Origin.ENVIRONMENT;
        } else {
            text = "https://repo.jenesis.build/";
            origin = Repository.Origin.DEFAULT;
        }
        JenesisRepository repository = text.isBlank()
                ? JenesisRepository.of(Repository.empty())
                : chain(environment, text, visited, scope, credential, maven, origin, MODULE, null);
        if (repository == null) {
            throw new IllegalStateException("No Jenesis module repository is configured by: " + text);
        }
        String local = environment.getProperty("module.local", System.getenv("JENESIS_REPOSITORY_LOCAL"));
        return repository.prepend(ofEnvironment(environment, (local == null
                ? Path.of(System.getProperty("user.home")).resolve(".jenesis")
                : Path.of(local)).toUri()));
    }

    private static JenesisRepository chain(Environment environment,
                                           String text,
                                           Set<String> visited,
                                           Scope scope,
                                           Repository.Credential credential,
                                           Repository.Credential maven,
                                           Repository.Origin origin,
                                           String kind,
                                           JenesisRepository repository) {
        for (String entry : text.split(",")) {
            String candidate = entry.strip();
            if (candidate.isEmpty()) {
                continue;
            }
            int separator = candidate.indexOf('|');
            String location = (separator < 0 ? candidate : candidate.substring(0, separator)).strip();
            if (location.isEmpty()) {
                throw new IllegalStateException("No URI in Jenesis module repository entry: " + candidate);
            }
            String type = kind;
            Integer segments = null;
            int colon = location.indexOf(':');
            if (colon > 1 && isType(location.substring(0, colon)) && isArgument(location.substring(colon + 1))) {
                type = location.substring(0, colon);
                location = location.substring(colon + 1).strip();
                int next = location.indexOf(':');
                if (next > 0 && isCount(location.substring(0, next)) && isArgument(location.substring(next + 1))) {
                    segments = toSegments(location.substring(0, next), candidate);
                    location = location.substring(next + 1).strip();
                }
            }
            if (!type.equals(MODULE) && !type.equals(MAVEN) && !type.equals(MAPPED)) {
                throw new IllegalArgumentException("Unknown repository type in Jenesis module repository entry: "
                        + candidate
                        + " (expected '" + MODULE + "', '" + MAVEN + "' or '" + MAPPED + "')");
            }
            if (segments != null && !type.equals(MAVEN)) {
                throw new IllegalArgumentException("A group id segment count applies only to a '"
                        + MAVEN
                        + "' entry: "
                        + candidate);
            }
            if (location.isEmpty()) {
                throw new IllegalStateException("No URI in Jenesis module repository entry: " + candidate);
            }
            Repository.Credential granted = repository == null ? credential : credential.token(null);
            Repository.Credential grantedMaven = repository == null ? maven : maven.token(null);
            JenesisRepository current;
            if (type.equals(MAPPED)) {
                int scheme = location.indexOf(':');
                int from = location.startsWith("@") ? 0 : scheme < 0
                        ? -1
                        : location.startsWith("://", scheme) ? location.indexOf('/', scheme + 3) : scheme + 1;
                if (location.startsWith("file:") && from >= 0 && location.length() > from + 2
                        && Character.isLetter(location.charAt(from + 1)) && location.charAt(from + 2) == ':') {
                    from += 3;
                }
                int split = from < 0 ? -1 : location.indexOf(':', from);
                if (split < 0 || split == location.length() - 1) {
                    throw new IllegalArgumentException("Expected " + MAPPED + ":<Maven repository URI or @>:<list>[;<list>...] "
                            + "in Jenesis module repository entry: " + candidate);
                }
                String target = location.substring(0, split).strip();
                if (target.startsWith("@") && !target.equals("@")) {
                    throw new IllegalArgumentException("A " + MAPPED + " entry names the Maven repository the build uses by @ alone, "
                            + "or another one by its URI, not " + target + ", in Jenesis module repository entry: " + candidate);
                }
                URI uri = target.equals("@") ? null : URI.create(target.endsWith("/") ? target : target + "/");
                String token = uri == null ? null : grantedMaven.grant(origin);
                Repository.Connection connection = Repository.Connection.ofEnvironment(environment);
                SequencedMap<String, MavenDependencyKey> mapping = new LinkedHashMap<>();
                Map<String, Map.Entry<URI, String>> declared = new HashMap<>();
                for (String named : location.substring(split + 1).split(";")) {
                    String list = named.strip();
                    if (list.isEmpty()) {
                        continue;
                    }
                    int prefix = list.indexOf(':');
                    URI source;
                    if (prefix > 1) {
                        source = URI.create(list);
                    } else {
                        Path path = Path.of(list);
                        if (!path.isAbsolute()) {
                            throw new IllegalArgumentException("A module list is named by a URI or an absolute path, not "
                                    + list + ", in Jenesis module repository entry: " + candidate);
                        }
                        source = path.toUri();
                    }
                    SequencedProperties properties = new SequencedProperties();
                    try (InputStream in = Repository.open(connection, source, uri != null
                            && Objects.equals(uri.getScheme(), source.getScheme())
                            && uri.getHost() != null
                            && uri.getHost().equalsIgnoreCase(source.getHost())
                            && uri.getPort() == source.getPort() ? token : null)) {
                        properties.load(in);
                    } catch (FileNotFoundException e) {
                        throw new IllegalStateException("The module list " + source + " of Jenesis module repository entry "
                                + candidate + " does not exist", e);
                    } catch (IOException e) {
                        throw new IllegalStateException("Cannot read the module list " + source
                                + " of Jenesis module repository entry " + candidate, e);
                    }
                    for (String module : properties.stringPropertyNames()) {
                        String value = properties.value(module);
                        if (value == null) {
                            throw new IllegalArgumentException("The module list " + source + " maps " + module
                                    + " to nothing, where it expects <groupId>/<artifactId>[/<type>[/<classifier>]]");
                        }
                        MavenDependencyKey key;
                        try {
                            key = MavenDependencyKey.parseKey(value);
                        } catch (IllegalArgumentException e) {
                            throw new IllegalArgumentException("The module list " + source + " maps " + module + " to " + value
                                    + ", where it expects <groupId>/<artifactId>[/<type>[/<classifier>]]", e);
                        }
                        MavenDependencyKey previous = mapping.putIfAbsent(module, key);
                        if (previous != null && !previous.equals(key)) {
                            Map.Entry<URI, String> first = declared.get(module);
                            throw new IllegalArgumentException("The module lists " + first.getKey() + " and " + source
                                    + " map " + module + " to different coordinates, " + first.getValue() + " and " + value);
                        }
                        declared.putIfAbsent(module, Map.entry(source, value));
                    }
                }
                current = MavenModuleRepository.ofEnvironment(environment, uri == null
                        ? MavenDefaultRepository.ofEnvironment(environment)
                        : MavenDefaultRepository.ofEnvironment(environment, uri, token)).mapping(mapping);
            } else if (location.startsWith("@")) {
                String name = location.substring(1);
                String value;
                Repository.Origin spliced;
                if (name.isEmpty()) {
                    String variable = System.getenv("JENESIS_REPOSITORY_URI");
                    if (variable != null && visited.add("JENESIS_REPOSITORY_URI")) {
                        name = "JENESIS_REPOSITORY_URI";
                        value = variable;
                        spliced = Repository.Origin.ENVIRONMENT;
                    } else {
                        name = null;
                        value = "https://repo.jenesis.build/";
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
                current = chain(environment, value, visited, scope, granted, grantedMaven, spliced, type, null);
                if (name != null) {
                    visited.remove(name);
                }
                if (current == null) {
                    throw new IllegalStateException("No Jenesis module repository is configured by: " + value);
                }
            } else if (type.equals(MAVEN)) {
                MavenModuleRepository convention = MavenModuleRepository.ofEnvironment(environment,
                        MavenDefaultRepository.ofEnvironment(environment,
                                                             URI.create(location.endsWith("/") ? location : location + "/"),
                                                             grantedMaven.grant(origin)));
                current = segments == null ? convention : convention.segments(segments);
            } else {
                current = ofEnvironment(environment,
                        URI.create((location.endsWith("/") ? location : location + "/")
                                   + (scope == Scope.MODULE ? "module/" : "artifact/")),
                        granted.grant(origin));
            }
            List<String> modules = new ArrayList<>();
            if (separator >= 0) {
                for (String argument : candidate.substring(separator + 1).split("\\|")) {
                    String module = argument.strip();
                    if (!module.isEmpty()) {
                        modules.add(module);
                    }
                }
            }
            if (!modules.isEmpty()) {
                current = current.filter(value -> {
                    for (String module : modules) {
                        if (value.equals(module) || value.startsWith(module + ".")) {
                            return true;
                        }
                    }
                    return false;
                });
            }
            repository = repository == null ? current : current.prepend(repository);
        }
        return repository;
    }

    private static boolean isType(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < 'a' || character > 'z') {
                return false;
            }
        }
        return true;
    }

    private static boolean isCount(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character < '0' || character > '9') {
                return false;
            }
        }
        return true;
    }

    private static boolean isArgument(String value) {
        String remainder = value.strip();
        return !remainder.startsWith("/")
                && (remainder.isEmpty() || remainder.startsWith("@") || remainder.indexOf(':') > 0);
    }

    private static int toSegments(String value, String candidate) {
        int segments;
        try {
            segments = Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid group id segment count in Jenesis module repository entry: "
                    + candidate, e);
        }
        if (segments < 1) {
            throw new IllegalArgumentException("Expected at least one group id segment in "
                    + "Jenesis module repository entry: "
                    + candidate);
        }
        return segments;
    }

    public JenesisModuleRepository(URI root) {
        this(root, null);
    }

    public JenesisModuleRepository(URI root, String token) {
        this(Environment.NONE, root, token);
    }

    public static JenesisModuleRepository ofEnvironment(Environment environment, URI root) {
        return new JenesisModuleRepository(environment, root, null);
    }

    public static JenesisModuleRepository ofEnvironment(Environment environment, URI root, String token) {
        return new JenesisModuleRepository(environment, root, token);
    }

    private JenesisModuleRepository(Environment environment, URI root, String token) {
        this(root,
             token,
             Repository.Connection.ofEnvironment(environment),
             toMavenRepository(environment.getProperty("maven.uri",
                                                       System.getenv("MAVEN_REPOSITORY_URI"))),
             environment.flagOrNull("module.prerelease"),
             environment.flagOrNull("module.speculative"),
             environment.flag("print.fetch") ? environment.out() : null,
             Palette.ofEnvironment(environment));
    }

    private JenesisModuleRepository(URI root,
                                    String token,
                                    Repository.Connection connection,
                                    URI maven,
                                    Boolean prerelease,
                                    Boolean speculative,
                                    Consumer<String> printing,
                                    Palette palette) {
        String text = root.toString();
        this.root = text.endsWith("/") ? root : URI.create(text + "/");
        this.token = token;
        this.connection = connection;
        this.maven = maven;
        this.prerelease = prerelease;
        this.speculative = speculative;
        this.printing = printing;
        this.palette = palette;
    }

    public JenesisModuleRepository connection(Repository.Connection connection) {
        return new JenesisModuleRepository(root, token, connection, maven, prerelease, speculative, printing, palette);
    }

    public JenesisModuleRepository maven(URI maven) {
        return new JenesisModuleRepository(root, token, connection, maven, prerelease, speculative, printing, palette);
    }

    public JenesisModuleRepository prerelease(Boolean prerelease) {
        return new JenesisModuleRepository(root, token, connection, maven, prerelease, speculative, printing, palette);
    }

    public JenesisModuleRepository speculative(Boolean speculative) {
        return new JenesisModuleRepository(root, token, connection, maven, prerelease, speculative, printing, palette);
    }

    public JenesisModuleRepository printing(Consumer<String> printing, Palette palette) {
        return new JenesisModuleRepository(root, token, connection, maven, prerelease, speculative, printing, palette);
    }

    private static URI toMavenRepository(String declaration) {
        if (declaration == null) {
            return null;
        }
        for (String entry : declaration.split(",")) {
            String candidate = entry.strip();
            if (candidate.isEmpty() || candidate.indexOf('|') >= 0 || candidate.startsWith("@")) {
                continue;
            }
            URI uri = URI.create(candidate.endsWith("/") ? candidate : candidate + "/");
            if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
                continue;
            }
            if (uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null) {
                continue;
            }
            return uri;
        }
        return null;
    }

    private Map<String, String> toHeaders(URI uri) {
        if (!"https".equals(uri.getScheme()) && !"http".equals(uri.getScheme())) {
            return Map.of();
        }
        SequencedMap<String, String> headers = new LinkedHashMap<>();
        if (maven != null) {
            headers.put("Jenesis-Repository", maven.toString());
        }
        if (prerelease != null) {
            headers.put("Jenesis-Prerelease", prerelease.toString());
        }
        if (speculative != null) {
            headers.put("Jenesis-BestEffort", speculative.toString());
        }
        return headers;
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
        String fileName = (classifier == null ? module : module + "-" + classifier) + "." + type;
        String relative = version == null
                ? module + "/" + fileName
                : module + "/" + version + "/" + fileName;
        URI base = root.normalize();
        URI uri = base.resolve(relative).normalize();
        URI contained = base.relativize(uri);
        if (contained.isAbsolute() || contained.getPath().startsWith("..")) {
            throw new IllegalArgumentException("Resolved location " + uri + " escapes repository root " + root);
        }
        if ("file".equals(uri.getScheme())) {
            Path file = Path.of(uri);
            return Files.isRegularFile(file)
                    ? Optional.of(RepositoryItem.ofLocalFile(file))
                    : Optional.empty();
        }
        Map<String, String> headers = toHeaders(uri);
        InputStream stream;
        try {
            stream = Repository.open(connection, uri, token, headers);
        } catch (FileNotFoundException _) {
            return Optional.empty();
        }
        if (printing != null) {
            printing.accept("%s%-11s%s %s".formatted(palette.info(),
                    "[FETCHED]",
                    palette.reset(),
                    uri));
        }
        AtomicReference<InputStream> first = new AtomicReference<>(stream);
        return Optional.of(() -> {
            InputStream reopened = first.getAndSet(null);
            return reopened != null ? reopened : Repository.open(connection, uri, token, headers);
        });
    }

}

package build.jenesis;

import module java.base;

public class OpenPgpRepository implements Repository {

    public static final List<URI> DEFAULTS = List.of(
            URI.create("https://keyserver.ubuntu.com/"),
            URI.create("https://keys.openpgp.org/"));
    private static final ConcurrentMap<String, Object> FETCHING = new ConcurrentHashMap<>();

    private final URI server;
    private final Path local;
    private final Repository.Connection connection;

    public OpenPgpRepository(URI server) {
        this(server, null, new Repository.Connection());
    }

    public static OpenPgpRepository ofEnvironment(Environment environment, URI server) {
        return new OpenPgpRepository(server, null, Repository.Connection.ofEnvironment(environment));
    }

    private OpenPgpRepository(URI server, Path local, Repository.Connection connection) {
        this.server = server;
        this.local = local;
        this.connection = connection;
    }

    public OpenPgpRepository local(Path local) {
        return new OpenPgpRepository(server, local, connection);
    }

    public OpenPgpRepository connection(Repository.Connection connection) {
        return new OpenPgpRepository(server, local, connection);
    }

    public static Repository of() {
        return ofEnvironment(Environment.NONE);
    }

    public static Repository ofEnvironment(Environment environment) {
        String property = environment.getProperty("openpgp.uri");
        String text = property == null ? System.getenv("OPENPGP_REPOSITORY_URI") : property;
        Path local = Path.of(environment.getProperty("openpgp.local",
                                                     System.getenv("OPENPGP_REPOSITORY_LOCAL") == null
                                                     ? ".jenesis/keys"
                                                     : System.getenv("OPENPGP_REPOSITORY_LOCAL")));
        List<URI> servers = new ArrayList<>();
        servers(environment, text == null ? "@" : text, new HashSet<>(), servers);
        List<OpenPgpRepository> chain = servers.stream()
                .map(server -> OpenPgpRepository.ofEnvironment(environment, server).local(local))
                .toList();
        return (executor, coordinate, extension) -> {
            if (extension != null) {
                return Optional.empty();
            }
            Object lock = FETCHING.computeIfAbsent(coordinate, _ -> new Object());
            synchronized (lock) {
                try {
                    Optional<RepositoryItem> candidate = cached(local, coordinate);
                    if (candidate.isPresent()) {
                        return candidate;
                    }
                    SequencedMap<String, String> refused = new LinkedHashMap<>();
                    for (OpenPgpRepository repository : chain) {
                        try {
                            Optional<RepositoryItem> served = repository.fetch(executor, coordinate);
                            if (served.isPresent()) {
                                return served;
                            }
                            refused.put(repository.server.toString(), "404");
                        } catch (IOException e) {
                            refused.put(repository.server.toString(),
                                    e.getMessage() == null ? e.toString() : e.getMessage());
                        }
                    }
                    if (refused.values().stream().anyMatch(answer -> !answer.equals("404"))) {
                        throw new IOException(refused.entrySet().stream()
                                .map(entry -> entry.getKey() + " answered " + entry.getValue())
                                .collect(Collectors.joining("; ")));
                    }
                    return Optional.empty();
                } finally {
                    FETCHING.remove(coordinate, lock);
                }
            }
        };
    }

    private static Optional<RepositoryItem> cached(Path local, String coordinate) {
        Path candidate = local.resolve(coordinate + ".gpg");
        return Files.isRegularFile(candidate) ? Optional.of(RepositoryItem.ofFile(candidate)) : Optional.empty();
    }

    private static void servers(Environment environment,
                                String text,
                                Set<String> visited,
                                List<URI> target) {
        for (String entry : text.split(",")) {
            String candidate = entry.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith("@")) {
                String name = candidate.substring(1);
                if (name.isEmpty()) {
                    String uri = System.getenv("OPENPGP_REPOSITORY_URI");
                    if (uri != null && visited.add("OPENPGP_REPOSITORY_URI")) {
                        servers(environment, uri, visited, target);
                        visited.remove("OPENPGP_REPOSITORY_URI");
                    } else {
                        target.addAll(DEFAULTS);
                    }
                } else {
                    String value = environment.getProperty(name, System.getenv(name));
                    if (value == null) {
                        throw new IllegalStateException("Unresolved key server reference: @" + name);
                    }
                    if (!visited.add(name)) {
                        throw new IllegalStateException("Circular key server reference: @" + name);
                    }
                    servers(environment, value, visited, target);
                    visited.remove(name);
                }
            } else {
                target.add(URI.create(candidate.endsWith("/") ? candidate : candidate + "/"));
            }
        }
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension) throws IOException {
        if (extension != null) {
            return Optional.empty();
        }
        if (local != null) {
            Optional<RepositoryItem> candidate = cached(local, coordinate);
            if (candidate.isPresent()) {
                return candidate;
            }
        }
        return served(coordinate);
    }

    private Optional<RepositoryItem> served(String coordinate) throws IOException {
        URI uri = server.resolve("pks/lookup?op=get&options=mr&search=0x" + coordinate);
        byte[] key;
        try (InputStream stream = Repository.open(connection, uri, null)) {
            key = stream.readAllBytes();
        } catch (FileNotFoundException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new IOException(e.getMessage() == null ? e.toString() : e.getMessage(), e);
        }
        if (local == null) {
            return Optional.of(() -> new ByteArrayInputStream(key));
        }
        Files.createDirectories(local);
        Path target = local.resolve(coordinate + ".gpg");
        Path temporary = Files.createTempFile(local, "key", ".gpg");
        Files.write(temporary, key);
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        return Optional.of(RepositoryItem.ofFile(target));
    }
}

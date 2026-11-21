package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.BuildStep;
import build.jenesis.Discovery;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;

public class MavenDefaultRepository implements MavenRepository {

    private final URI repository;
    private final Path local;
    private final boolean writable;
    private final Map<String, URI> validations;
    private final Consumer<String> callback;
    private final String token;
    private final Repository.Connection connection;

    public static MavenRepository of() {
        return ofEnvironment(Environment.NONE);
    }

    public static MavenRepository ofEnvironment(Environment environment) {
        return ofEnvironment(environment,
                environment.flag("repository.discovery") ? Discovery.ofEnvironment(environment) : null);
    }

    public static MavenRepository ofEnvironment(Environment environment, Discovery discovery) {
        Path local = localRepository(environment);
        Repository.Credential credential = Repository.Credential.of(environment, "maven.token", "MAVEN_REPOSITORY_TOKEN");
        Consumer<String> printing = environment.flag("print.fetch") ? environment.out() : null;
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
        MavenRepository repository = text.isBlank()
                ? MavenRepository.of(Repository.empty())
                : chain(environment, text, visited, local, credential, origin, printing, null);
        if (repository == null) {
            throw new IllegalStateException("No Maven repository is configured by: " + text);
        }
        return discovery == null
                ? repository
                : repository.overlay(DiscoveryMavenRepository.ofEnvironment(environment, discovery));
    }

    public static MavenRepository of(URI repository, String token) {
        return ofEnvironment(Environment.NONE, repository, token);
    }

    public static MavenRepository ofEnvironment(Environment environment,
                                                URI repository,
                                                String token) {
        return single(environment,
                repository.toString().endsWith("/") ? repository : URI.create(repository + "/"),
                localRepository(environment),
                token,
                environment.flag("print.fetch") ? environment.out() : null);
    }

    static Path localRepository(Environment environment) {
        String override = environment.getProperty("maven.local", System.getenv("MAVEN_REPOSITORY_LOCAL"));
        if (override == null) {
            Path candidate = Path.of(System.getProperty("user.home"), ".m2", "repository");
            return Files.isDirectory(candidate) ? candidate : null;
        }
        Path local = Path.of(override);
        if (!Files.isDirectory(local)) {
            throw new IllegalStateException("Local Maven repository does not point at a directory: " + local);
        }
        return local;
    }

    private static MavenRepository single(Environment environment,
                                          URI uri,
                                          Path local,
                                          String token,
                                          Consumer<String> printing) {
        Palette palette = Palette.ofEnvironment(environment);
        return ofEnvironment(environment,
                uri,
                local,
                validations(uri),
                printing == null ? null : path -> printing.accept("%s%-11s%s %s".formatted(
                        palette.info(),
                        "[FETCHED]",
                        palette.reset(),
                        uri.resolve(path))),
                token);
    }

    static Map<String, URI> validations(URI uri) {
        SequencedMap<String, URI> validations = new LinkedHashMap<>();
        validations.put("SHA512", uri);
        validations.put("SHA256", uri);
        validations.put("SHA1", uri);
        return Collections.unmodifiableMap(validations);
    }

    private static MavenRepository chain(Environment environment,
                                         String text,
                                         Set<String> visited,
                                         Path local,
                                         Repository.Credential credential,
                                         Repository.Origin origin,
                                         Consumer<String> printing,
                                         MavenRepository repository) {
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
            MavenRepository current;
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
                current = chain(environment, value, visited, local, credential, spliced, printing, null);
                if (name != null) {
                    visited.remove(name);
                }
                if (current == null) {
                    throw new IllegalStateException("No Maven repository is configured by: " + value);
                }
            } else {
                current = single(environment,
                        URI.create(location.endsWith("/") ? location : location + "/"),
                        local,
                        credential.grant(origin),
                        printing);
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
            if (!groups.isEmpty()) {
                current = current.filter(value -> {
                    for (String group : groups) {
                        if (value.equals(group) || value.startsWith(group + ".")) {
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

    public MavenDefaultRepository(URI repository, Path local, Map<String, URI> validations, Consumer<String> callback) {
        this(repository, local, validations, callback, null);
    }

    public MavenDefaultRepository(URI repository,
                                  Path local,
                                  Map<String, URI> validations,
                                  Consumer<String> callback,
                                  String token) {
        this(Environment.NONE, repository, local, validations, callback, token);
    }

    public static MavenDefaultRepository ofEnvironment(Environment environment,
                                                URI repository,
                                                Path local,
                                                Map<String, URI> validations,
                                                Consumer<String> callback,
                                                String token) {
        return new MavenDefaultRepository(environment, repository, local, validations, callback, token);
    }

    private MavenDefaultRepository(Environment environment,
                                   URI repository,
                                   Path local,
                                   Map<String, URI> validations,
                                   Consumer<String> callback,
                                   String token) {
        this(repository, local, validations, callback, token, Repository.Connection.ofEnvironment(environment));
    }

    private MavenDefaultRepository(URI repository,
                                   Path local,
                                   Map<String, URI> validations,
                                   Consumer<String> callback,
                                   String token,
                                   Repository.Connection connection) {
        this.repository = repository;
        this.local = local;
        this.writable = local != null && Files.isWritable(local);
        this.validations = validations;
        this.callback = callback;
        this.token = token;
        this.connection = connection;
    }

    public MavenDefaultRepository connection(Repository.Connection connection) {
        return new MavenDefaultRepository(repository, local, validations, callback, token, connection);
    }

    public MavenDefaultRepository printing(Consumer<String> printing, Palette palette) {
        return new MavenDefaultRepository(repository, local, validations, printing == null
                ? null
                : path -> printing.accept("%s%-11s%s %s".formatted(
                        palette.info(),
                        "[FETCHED]",
                        palette.reset(),
                        repository.resolve(path))), token, connection);
    }

    @SuppressWarnings("unchecked")
    public static <F extends BiFunction<URI, String, Optional<URI>> & Serializable> F versionResolver() {
        return (F) (BiFunction<URI, String, Optional<URI>> & Serializable) (uri, version) -> {
            String path = uri.getPath();
            if (path == null) {
                return Optional.empty();
            }
            int last = path.lastIndexOf('/');
            if (last <= 0) {
                return Optional.empty();
            }
            int versionStart = path.lastIndexOf('/', last - 1);
            if (versionStart <= 0) {
                return Optional.empty();
            }
            int artifactStart = path.lastIndexOf('/', versionStart - 1);
            if (artifactStart < 0) {
                return Optional.empty();
            }
            String artifactId = path.substring(artifactStart + 1, versionStart);
            String existingVersion = path.substring(versionStart + 1, last);
            String filename = path.substring(last + 1);
            String prefix = artifactId + "-" + existingVersion;
            if (!filename.startsWith(prefix)) {
                return Optional.empty();
            }
            String tail = filename.substring(prefix.length());
            int dot = tail.lastIndexOf('.');
            if (dot < 0) {
                return Optional.empty();
            }
            String classifier = tail.substring(0, dot);
            if (!classifier.isEmpty() && !classifier.startsWith("-")) {
                return Optional.empty();
            }
            String extension = tail.substring(dot);
            String newPath = path.substring(0, versionStart + 1)
                    + version
                    + "/" + artifactId + "-" + version + classifier + extension;
            try {
                return Optional.of(new URI(
                        uri.getScheme(),
                        uri.getUserInfo(),
                        uri.getHost(),
                        uri.getPort(),
                        newPath,
                        uri.getQuery(),
                        uri.getFragment()));
            } catch (URISyntaxException _) {
                return Optional.empty();
            }
        };
    }

    @Override
    public Optional<RepositoryItem> fetch(Executor executor,
                                          String groupId,
                                          String artifactId,
                                          String version,
                                          String type,
                                          String classifier,
                                          String checksum) throws IOException {
        String folder = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/",
                suffix = (classifier == null ? "" : "-" + classifier) + "." + type + (checksum == null ? "" : ("." + checksum)),
                path = folder + artifactId + "-" + version + suffix;
        boolean unstamped = false;
        if (version.endsWith("-SNAPSHOT") && (local == null || !Files.exists(BuildStep.resolveContained(local, path)))) {
            String snapshot = snapshot(folder, version, type, classifier);
            if (snapshot != null) {
                path = folder + artifactId + "-" + snapshot + suffix;
            } else {
                unstamped = true;
            }
        }
        if (callback != null) {
            callback.accept(path);
        }
        return fetch(repository, unstamped ? null : local, path, checksum == null).materialize();
    }

    private String snapshot(String folder, String version, String type, String classifier) throws IOException {
        if (connection.offline()) {
            return null;
        }
        Optional<RepositoryItem> metadata = fetch(repository, null, folder + "maven-metadata.xml", false).materialize();
        if (metadata.isEmpty()) {
            return null;
        }
        Document document;
        try (InputStream inputStream = metadata.get().toInputStream()) {
            document = MavenDefaultVersionNegotiator.toDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IllegalStateException("Cannot read the snapshot metadata "
                    + repository.resolve(folder + "maven-metadata.xml") + " - the repository serves no valid"
                    + " maven-metadata.xml for " + version, e);
        }
        Node versioning = children(document.getDocumentElement(), "versioning").findFirst().orElse(null);
        if (versioning == null) {
            return null;
        }
        Optional<String> value = children(versioning, "snapshotVersions")
                .flatMap(snapshotVersions -> children(snapshotVersions, "snapshotVersion"))
                .filter(snapshotVersion -> type.equals(text(snapshotVersion, "extension"))
                        && Objects.equals(classifier == null ? "" : classifier, Objects.requireNonNullElse(text(snapshotVersion, "classifier"), "")))
                .map(snapshotVersion -> text(snapshotVersion, "value"))
                .filter(Objects::nonNull)
                .findFirst();
        if (value.isPresent()) {
            return value.get();
        }
        Node snapshot = children(versioning, "snapshot").findFirst().orElse(null);
        if (snapshot == null || "true".equals(text(snapshot, "localCopy"))) {
            return null;
        }
        String timestamp = text(snapshot, "timestamp"), buildNumber = text(snapshot, "buildNumber");
        return timestamp == null || buildNumber == null
                ? null
                : version.substring(0, version.length() - "SNAPSHOT".length()) + timestamp + "-" + buildNumber;
    }

    private static Stream<Node> children(Node node, String name) {
        return MavenPomResolver.toChildren(node).filter(child -> name.equals(child.getNodeName()));
    }

    private static String text(Node node, String name) {
        return children(node, name).findFirst().map(child -> child.getTextContent().strip()).orElse(null);
    }

    @Override
    public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String checksum) throws IOException {
        String path = groupId.replace('.', '/')
                + "/" + artifactId
                + "/maven-metadata.xml" + (checksum == null ? "" : "." + checksum);
        if (callback != null) {
            callback.accept(path);
        }
        return fetch(repository, null, path, checksum == null).materialize();
    }

    private LazyRepositoryItem fetch(URI repository, Path store, String path, boolean validate) throws IOException {
        Path cached = store == null ? null : BuildStep.resolveContained(store, path);
        if (cached != null) {
            if (Files.exists(cached)) {
                return new StoredRepositoryItem(cached);
            } else if (writable) {
                try {
                    Files.createDirectories(cached.getParent());
                } catch (IOException _) {
                    cached = null;
                }
            }
        }
        SequencedMap<LazyRepositoryItem, MessageDigest> digests = new LinkedHashMap<>();
        if (validate) {
            for (Map.Entry<String, URI> entry : validations.entrySet()) {
                digests.put(fetch(entry.getValue(),
                                store,
                                path + "." + entry.getKey().toLowerCase(Locale.ROOT),
                                false),
                        digest(entry.getKey()));
            }
        }
        URI uri = repository.resolve(path);
        int dash = path.lastIndexOf('/'), dot = path.indexOf('.', dash);
        return new LatentRepositoryItem(writable ? cached : null,
                uri,
                digests,
                path.substring(dash + 1, dot),
                path.substring(dot),
                token,
                connection);
    }

    private static Optional<Path> download(Repository.Connection connection,
                                           URI uri,
                                           SequencedMap<LazyRepositoryItem, MessageDigest> digests,
                                           String prefix,
                                           String suffix,
                                           String token,
                                           Path directory) throws IOException {
        Path temporary = directory == null
                ? Files.createTempFile(prefix, suffix)
                : Files.createTempFile(directory, prefix, suffix);
        try {
            for (int attempt = 0; ; attempt++) {
                try (InputStream stream = Repository.open(connection, uri, token)) {
                    Files.copy(stream, temporary, StandardCopyOption.REPLACE_EXISTING);
                    break;
                } catch (FileNotFoundException _) {
                    Files.deleteIfExists(temporary);
                    return Optional.empty();
                } catch (SocketException | SocketTimeoutException e) {
                    if (attempt >= connection.retries()) {
                        throw e;
                    }
                    Thread.sleep(connection.backoff().toMillis() << attempt);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Files.deleteIfExists(temporary);
            throw new IOException("Interrupted while fetching " + uri, e);
        } catch (Throwable t) {
            Files.deleteIfExists(temporary);
            throw t;
        }
        if (digests.isEmpty()) {
            return Optional.of(temporary);
        }
        try {
            try (FileChannel channel = FileChannel.open(temporary)) {
                ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    for (MessageDigest digest : digests.values()) {
                        digest.update(buffer);
                        buffer.rewind();
                    }
                    buffer.clear();
                }
            }
            for (Map.Entry<LazyRepositoryItem, MessageDigest> entry : digests.entrySet()) {
                Optional<InputStream> candidate = entry.getKey().toLazyInputStream();
                if (candidate.isEmpty()) {
                    continue;
                }
                byte[] published;
                try (InputStream inputStream = candidate.get()) {
                    published = inputStream.readAllBytes();
                }
                if (!Arrays.equals(toChecksum(published), entry.getValue().digest())) {
                    for (LazyRepositoryItem item : digests.keySet()) {
                        item.deleteIfPresent();
                    }
                    throw new IllegalStateException("Failed checksum validation for "
                            + uri
                            + " against its "
                            + entry.getValue().getAlgorithm()
                            + " checksum");
                }
                entry.getKey().storeIfNotPresent(published);
                break;
            }
        } catch (Throwable t) {
            Files.deleteIfExists(temporary);
            throw t;
        }
        return Optional.of(temporary);
    }

    private static MessageDigest digest(String algorithm) {
        try {
            return MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Unknown checksum algorithm: " + algorithm, e);
        }
    }

    private static byte[] toChecksum(byte[] published) {
        String text = new String(published, StandardCharsets.UTF_8).strip();
        int end = 0;
        while (end < text.length() && !Character.isWhitespace(text.charAt(end))) {
            end++;
        }
        return HexFormat.of().parseHex(text.substring(0, end));
    }

    private static Path move(Path source, Path target) throws IOException {
        try {
            return Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException _) {
            return Files.move(source, target);
        }
    }

    private interface LazyRepositoryItem {

        default void deleteIfPresent() throws IOException {
        }

        default void storeIfNotPresent(byte[] bytes) throws IOException {
        }

        default Optional<RepositoryItem> materialize() throws IOException {
            return toLazyInputStream().map(inputStream -> () -> inputStream);
        }

        Optional<InputStream> toLazyInputStream() throws IOException;
    }

    record StoredRepositoryItem(Path path) implements LazyRepositoryItem, RepositoryItem {

        @Override
        public void deleteIfPresent() throws IOException {
            Files.delete(path);
        }

        @Override
        public Optional<RepositoryItem> materialize() {
            return Optional.of(this);
        }

        @Override
        public Optional<InputStream> toLazyInputStream() throws IOException {
            return Optional.of(toInputStream());
        }

        @Override
        public InputStream toInputStream() throws IOException {
            return Files.newInputStream(path);
        }

        @Override
        public Optional<Path> file() {
            return Optional.of(path);
        }

        @Override
        public boolean local() {
            return true;
        }
    }

    record LatentRepositoryItem(Path path,
                                URI uri,
                                SequencedMap<LazyRepositoryItem, MessageDigest> digests,
                                String prefix,
                                String suffix,
                                String token,
                                Repository.Connection connection) implements LazyRepositoryItem {

        @Override
        public void storeIfNotPresent(byte[] bytes) throws IOException {
            if (path == null) {
                return;
            }
            Files.createDirectories(path.getParent());
            Path temporary = Files.createTempFile(path.getParent(), prefix, suffix);
            try (OutputStream outputStream = Files.newOutputStream(temporary)) {
                outputStream.write(bytes);
            } catch (Throwable t) {
                Files.delete(temporary);
                throw t;
            }
            try {
                move(temporary, path);
            } catch (IOException _) {
                Files.deleteIfExists(temporary);
            }
        }

        @Override
        public Optional<InputStream> toLazyInputStream() throws IOException {
            Optional<Path> temporary = download(connection, uri, digests, prefix, suffix, token, null);
            if (temporary.isEmpty()) {
                return Optional.empty();
            }
            Path file = temporary.get();
            InputStream stream;
            try {
                stream = Files.newInputStream(file);
            } catch (Throwable t) {
                Files.deleteIfExists(file);
                throw t;
            }
            return Optional.of(new FilterInputStream(stream) {
                @Override
                public void close() throws IOException {
                    try {
                        super.close();
                    } finally {
                        Files.deleteIfExists(file);
                    }
                }
            });
        }

        @Override
        public Optional<RepositoryItem> materialize() throws IOException {
            if (path == null) {
                return LazyRepositoryItem.super.materialize();
            }
            Files.createDirectories(path.getParent());
            Optional<Path> temporary = download(connection, uri, digests, prefix, suffix, token, path.getParent());
            if (temporary.isEmpty()) {
                return Optional.empty();
            }
            try {
                return Optional.of(new StoredRepositoryItem(move(temporary.get(), path)));
            } catch (IOException _) {
                return Optional.of(new StoredRepositoryItem(temporary.get()));
            }
        }
    }
}

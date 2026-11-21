package build.jenesis;

import module java.base;
import java.security.cert.X509Certificate;

@FunctionalInterface
public interface Repository {

    Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension) throws IOException;

    default Optional<RepositoryItem> fetch(Executor executor, String coordinate) throws IOException {
        return fetch(executor, coordinate, null);
    }

    default Repository prepend(Repository repository) {
        return (executor, coordinate, extension) -> {
            Optional<RepositoryItem> candidate = repository.fetch(executor, coordinate, extension);
            return candidate.isPresent() ? candidate : fetch(executor, coordinate, extension);
        };
    }

    default Repository cached(Path folder) {
        return cached(folder, false);
    }

    default Repository materialized(Path folder) {
        return cached(folder, true);
    }

    default Repository observed(Observer observer) {
        return (executor, coordinate, extension) -> {
            Optional<RepositoryItem> candidate = fetch(executor, coordinate, extension);
            if (candidate.isPresent()) {
                observer.fetched(coordinate, extension, candidate.get());
            }
            return candidate;
        };
    }

    default Repository spilled(Path folder) {
        return (executor, coordinate, extension) -> {
            Optional<RepositoryItem> candidate = fetch(executor, coordinate, extension);
            RepositoryItem item = candidate.orElse(null);
            if (item == null || item.file().isPresent()) {
                return candidate;
            }
            return Optional.of(item.spill(folder.resolve(PathPlacement.stem(coordinate)
                    + (extension == null ? ".jar" : "." + extension))));
        };
    }

    private Repository cached(Path folder, boolean snapshot) {
        if (folder == null) {
            return this;
        }
        ConcurrentMap<String, Path> cache = new ConcurrentHashMap<>();
        Set<String> internal = ConcurrentHashMap.newKeySet();
        Repository origin = this;
        return new Repository() {

            @Override
            public Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension)
                    throws IOException {
                return locate(executor, coordinate, extension == null ? ".jar" : "." + extension, extension);
            }

            private Optional<RepositoryItem> locate(Executor executor,
                                                    String coordinate,
                                                    String suffix,
                                                    String extension) throws IOException {
                try {
                    Path candidate = folder.resolve(PathPlacement.stem(coordinate) + suffix);
                    Path target = cache.computeIfAbsent(coordinate + suffix, key -> {
                        if (Files.exists(candidate)) {
                            return candidate;
                        }
                        try {
                            RepositoryItem item = (extension == null
                                    ? origin.fetch(executor, coordinate)
                                    : origin.fetch(executor, coordinate, extension)).orElse(null);
                            if (item == null) {
                                return null;
                            }
                            Path file = item.file().orElse(null);
                            if (file != null && (item.internal() || !snapshot && item.local())) {
                                if (item.internal()) {
                                    internal.add(key);
                                }
                                return file;
                            }
                            if (file != null) {
                                BuildStep.linkOrCopy(candidate, file);
                            } else {
                                Path temporary = Files.createTempFile(candidate.getParent(), "fetch", suffix);
                                try (InputStream inputStream = item.toInputStream()) {
                                    Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
                                } catch (Throwable t) {
                                    Files.deleteIfExists(temporary);
                                    throw t;
                                }
                                Files.move(temporary, candidate, StandardCopyOption.ATOMIC_MOVE);
                            }
                            return candidate;
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
                    return target == null
                            ? Optional.empty()
                            : Optional.of(RepositoryItem.ofFile(target, internal.contains(coordinate + suffix)));
                } catch (UncheckedIOException e) {
                    throw e.getCause();
                }
            }
        };
    }

    static Repository empty() {
        return (_, _, _) -> Optional.empty();
    }

    static InputStream open(URI uri, String token) throws IOException {
        return open(new Connection(), uri, token);
    }

    static InputStream open(Connection connection, URI uri, String token) throws IOException {
        return open(connection, uri, token, Map.of());
    }

    static InputStream open(Connection settings,
                            URI uri,
                            String token,
                            Map<String, String> headers) throws IOException {
        attempts:
        for (int attempt = 0; ; attempt++) {
            URI current = uri;
            try {
                for (int redirect = 0; redirect < 8; redirect++) {
                    String scheme = current.getScheme();
                    if (settings.offline() && !"file".equals(scheme)) {
                        throw new IOException("Refusing to fetch "
                                + current
                                + " while offline: only what the local caches hold is used"
                                + " (build once without -Djenesis.repository.offline=true to fill them)");
                    }
                    if (scheme != null && !scheme.equals("https") && !scheme.equals("file") && !settings.insecure()) {
                        throw new IllegalStateException("Refusing to fetch over insecure scheme '"
                                + scheme
                                + "': "
                                + current
                                + " (set -Djenesis.repository.insecure=true to allow plaintext repositories)");
                    }
                    URLConnection connection = connect(current, settings.insecure());
                    connection.setConnectTimeout(settings.connectTimeout());
                    connection.setReadTimeout(settings.readTimeout());
                    if (!(connection instanceof HttpURLConnection http)) {
                        return connection.getInputStream();
                    }
                    http.setInstanceFollowRedirects(false);
                    http.setRequestProperty("User-Agent", "Jenesis");
                    if (Objects.equals(uri.getScheme(), current.getScheme())
                            && uri.getHost() != null
                            && uri.getHost().equalsIgnoreCase(current.getHost())
                            && uri.getPort() == current.getPort()) {
                        if (token != null) {
                            http.setRequestProperty("Authorization", token);
                        }
                        for (Map.Entry<String, String> header : headers.entrySet()) {
                            http.setRequestProperty(header.getKey(), header.getValue());
                        }
                    }
                    int status = http.getResponseCode();
                    if (status >= 300 && status < 400) {
                        String location = http.getHeaderField("Location");
                        if (location != null) {
                            http.getInputStream().close();
                            current = current.resolve(location);
                            if (!"https".equals(current.getScheme()) && !"http".equals(current.getScheme())) {
                                throw new IllegalStateException("Refusing to follow a redirect to "
                                        + current
                                        + " (redirected from "
                                        + uri
                                        + "): a redirect is only followed to an http or https location");
                            }
                            continue;
                        }
                    }
                    if ((status == 429 || status >= 500) && attempt < settings.retries()) {
                        long delay = retryAfterMillis(http.getHeaderField("Retry-After"),
                                settings.backoff().toMillis() << Math.min(attempt, 20));
                        InputStream error = http.getErrorStream();
                        if (error != null) {
                            error.close();
                        }
                        pause(delay, uri);
                        continue attempts;
                    }
                    if (status == 429) {
                        throw new IOException(current + " answered 429 Too Many Requests after " + (attempt + 1)
                                + " attempt(s): the server limits how often this machine asks, so build again later or"
                                + " configure a mirror of this repository in its place");
                    }
                    return http.getInputStream();
                }
                throw new IOException("Exceeded redirect limit fetching " + uri);
            } catch (SocketException | SocketTimeoutException | SSLException | EOFException e) {
                if (attempt >= settings.retries()) {
                    throw new IOException("Failed to fetch "
                            + uri
                            + " after "
                            + (attempt + 1)
                            + " attempt(s): "
                            + e
                            + (e instanceof SSLHandshakeException && !settings.insecure()
                                    ? " (set -Djenesis.repository.insecure=true to accept a certificate that does not"
                                            + " verify, as a self-signed one does)"
                                    : ""), e);
                }
                pause(settings.backoff().toMillis() << attempt, uri);
            }
        }
    }

    static URLConnection connect(URI uri, boolean insecure) throws IOException {
        URLConnection connection = uri.toURL().openConnection();
        if (insecure && connection instanceof HttpsURLConnection https) {
            SSLContext context;
            try {
                context = SSLContext.getInstance("TLS");
                context.init(null, new TrustManager[] {new X509ExtendedTrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, Socket socket) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, Socket socket) {
                    }

                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                    }

                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType, SSLEngine engine) {
                    }

                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[0];
                    }
                }}, null);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Cannot switch off the certificate check for " + uri, e);
            }
            https.setSSLSocketFactory(context.getSocketFactory());
            https.setHostnameVerifier((_, _) -> true);
        }
        return connection;
    }

    private static void pause(long delay, URI uri) throws InterruptedIOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while retrying " + uri);
        }
    }

    static long retryAfterMillis(String header, long fallback) {
        if (header == null) {
            return fallback;
        }
        String trimmed = header.trim();
        try {
            return Math.min(Long.parseLong(trimmed), 30) * 1000;
        } catch (NumberFormatException _) {
            try {
                long seconds = Duration.between(Instant.now(),
                        ZonedDateTime.parse(trimmed, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant()).toSeconds();
                return Math.min(Math.max(seconds, 0), 30) * 1000;
            } catch (DateTimeParseException _) {
                return fallback;
            }
        }
    }

    enum Origin {

        USER,
        PROJECT,
        ENVIRONMENT,
        DEFAULT;

        public static Origin of(Environment environment, String key) {
            List<String> provided = environment.entries("make.provided");
            return provided != null && provided.contains(key) ? PROJECT : USER;
        }
    }

    @FunctionalInterface
    interface Observer {

        void fetched(String coordinate, String extension, RepositoryItem item) throws IOException;
    }

    record Credential(String token, Origin origin) {

        public static Credential of(Environment environment, String key, String variable) {
            String value = environment.getProperty(key);
            if (value != null) {
                return new Credential(value, Origin.of(environment, key));
            }
            String fallback = System.getenv(variable);
            return fallback == null
                    ? new Credential(null, Origin.USER)
                    : new Credential(fallback, Origin.ENVIRONMENT);
        }

        public Credential token(String token) {
            return new Credential(token, origin);
        }

        public String grant(Origin target) {
            if (token == null || origin == Origin.PROJECT) {
                return null;
            }
            if (target == Origin.DEFAULT || target == Origin.PROJECT) {
                return null;
            }
            return origin == Origin.ENVIRONMENT && target != Origin.ENVIRONMENT ? null : token;
        }
    }

    record Connection(int retries,
                      Duration backoff,
                      boolean insecure,
                      int connectTimeout,
                      int readTimeout,
                      boolean offline) {

        public Connection {
            if (retries < 0) {
                throw new IllegalArgumentException("Retries cannot be negative: " + retries);
            }
            if (backoff.isNegative()) {
                throw new IllegalArgumentException("Backoff cannot be negative: " + backoff);
            }
            if (connectTimeout < 0) {
                throw new IllegalArgumentException("Connect timeout cannot be negative: " + connectTimeout);
            }
            if (readTimeout < 0) {
                throw new IllegalArgumentException("Read timeout cannot be negative: " + readTimeout);
            }
        }

        public Connection() {
            this(Environment.NONE);
        }

        public static Connection ofEnvironment(Environment environment) {
            return new Connection(environment);
        }

        private Connection(Environment environment) {
            this(environment.number("repository.retries", 2),
                 Duration.ofMillis(environment.number("repository.backoff", 125)),
                 environment.flag("repository.insecure"),
                 environment.number("repository.connect", 10_000),
                 environment.number("repository.read", 30_000),
                 environment.flag("repository.offline"));
        }

        public Connection retries(int retries) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection backoff(Duration backoff) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection insecure(boolean insecure) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection connectTimeout(int connectTimeout) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection readTimeout(int readTimeout) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }

        public Connection offline(boolean offline) {
            return new Connection(retries, backoff, insecure, connectTimeout, readTimeout, offline);
        }
    }

    static Repository ofUris(Map<String, URI> uris) {
        return ofUris(uris, null);
    }

    static <F extends BiFunction<URI, String, Optional<URI>> & Serializable> Repository ofUris(
            Map<String, URI> uris,
            F versionResolver) {
        return ofUris(uris, versionResolver, new Connection().retries(0).backoff(Duration.ZERO), null);
    }

    static <F extends BiFunction<URI, String, Optional<URI>> & Serializable> Repository ofUris(
            Map<String, URI> uris,
            F versionResolver,
            Connection connection,
            Consumer<URI> callback) {
        return (_, coordinate, extension) -> {
            if (extension != null) {
                return Optional.empty();
            }
            URI candidate = uris.get(coordinate);
            if (candidate == null && versionResolver != null) {
                int slash = coordinate.lastIndexOf('/');
                if (slash > 0) {
                    URI base = uris.get(coordinate.substring(0, slash));
                    if (base != null) {
                        candidate = versionResolver.apply(base, coordinate.substring(slash + 1)).orElse(null);
                    }
                }
            }
            if (candidate == null) {
                return Optional.empty();
            }
            URI uri = candidate;
            if (callback != null) {
                callback.accept(uri);
            }
            if (Objects.equals("file", uri.getScheme())) {
                return Optional.of(RepositoryItem.ofFile(Path.of(uri), true));
            } else {
                return Optional.of(() -> open(connection, uri, null));
            }
        };
    }

    static Repository ofFiles(Map<String, Path> files) {
        return (_, coordinate, extension) -> {
            Path file = extension == null ? files.get(coordinate) : null;
            return file == null ? Optional.empty() : Optional.of(RepositoryItem.ofFile(file));
        };
    }

    static Repository files() {
        return (_, coordinate, extension) -> {
            Path file = Paths.get(extension == null ? coordinate : coordinate + "." + extension);
            return Files.exists(file) ? Optional.of(RepositoryItem.ofFile(file)) : Optional.empty();
        };
    }

    static Map<String, Repository> ofProperties(String suffix,
                                                Iterable<Path> folders,
                                                BiFunction<Path, String, URI> resolver,
                                                Path cache) throws IOException {
        return ofProperties(suffix, folders, resolver, null, cache);
    }

    static <F extends BiFunction<URI, String, Optional<URI>> & Serializable> Map<String, Repository> ofProperties(
            String suffix,
            Iterable<Path> folders,
            BiFunction<Path, String, URI> resolver,
            F versionResolver,
            Path cache) throws IOException {
        Map<String, Map<String, URI>> artifacts = new HashMap<>();
        for (Path folder : folders) {
            Path file = folder.resolve(suffix);
            if (Files.exists(file)) {
                SequencedProperties properties = SequencedProperties.ofFiles(file);
                for (String coordinate : properties.stringPropertyNames()) {
                    String location = properties.getProperty(coordinate);
                    if (!location.isEmpty()) {
                        int index = coordinate.indexOf('/');
                        artifacts.computeIfAbsent(
                                coordinate.substring(0, index),
                                _ -> new HashMap<>()).put(coordinate.substring(index + 1), resolver.apply(folder, location));
                    }
                }
            }
        }
        return artifacts.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), Repository.ofUris(entry.getValue(), versionResolver).cached(cache)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    static Map<String, Repository> prepend(Map<String, ? extends Repository> left,
                                           Map<String, ? extends Repository> right) {
        return Stream.concat(left.entrySet().stream(), right.entrySet().stream()).collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                Repository::prepend));
    }
}

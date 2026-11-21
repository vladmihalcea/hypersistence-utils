package build.jenesis;

import module java.base;

public final class BuildExecutorHttpCache implements BuildExecutorCache {

    public static final String KEY = "Jenesis-Cache-Key", PROJECT = "Jenesis-Cache-Project";

    private final URI uri;
    private final String key;
    private final String project;
    private final String algorithm;
    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final boolean read;
    private final boolean write;
    private final boolean insecure;

    public BuildExecutorHttpCache(URI uri) {
        this(uri, null, null, "SHA-256", Duration.parse("PT1S"), Duration.parse("PT10S"), true, true, false);
    }

    public static BuildExecutorHttpCache ofEnvironment(Environment environment, URI uri) {
        BuildExecutorHttpCache cache = new BuildExecutorHttpCache(uri);
        String key = environment.getProperty("cache.key", System.getenv("JENESIS_CACHE_KEY"));
        if (key != null) {
            cache = cache.key(key);
        }
        String project = environment.getProperty("cache.project", System.getenv("JENESIS_CACHE_PROJECT"));
        if (project != null) {
            cache = cache.project(project);
        }
        String connect = environment.getProperty("cache.connect");
        if (connect != null) {
            cache = cache.connectTimeout(Duration.parse(connect));
        }
        String read = environment.getProperty("cache.read");
        if (read != null) {
            cache = cache.readTimeout(Duration.parse(read));
        }
        Boolean insecure = environment.flagOrNull("cache.insecure");
        return insecure == null ? cache : cache.insecure(insecure);
    }

    private BuildExecutorHttpCache(URI uri,
                                   String key,
                                   String project,
                                   String algorithm,
                                   Duration connectTimeout,
                                   Duration readTimeout,
                                   boolean read,
                                   boolean write,
                                   boolean insecure) {
        this.uri = uri;
        this.key = key;
        this.project = project;
        this.algorithm = algorithm;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
        this.read = read;
        this.write = write;
        this.insecure = insecure;
    }

    public BuildExecutorHttpCache insecure(boolean insecure) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache key(String key) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache project(String project) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache algorithm(String algorithm) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache connectTimeout(Duration connectTimeout) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache read(boolean read) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache write(boolean write) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    public BuildExecutorHttpCache readTimeout(Duration readTimeout) {
        return new BuildExecutorHttpCache(uri, key, project, algorithm, connectTimeout, readTimeout, read, write, insecure);
    }

    @Override
    public Optional<BuildStepResult> fetch(Executor executor,
                                           String identity,
                                           byte[] step,
                                           SequencedMap<String, Map<Path, byte[]>> inputs,
                                           boolean remote,
                                           Path target) throws IOException {
        if (!read || !remote) {
            return Optional.empty();
        }
        HttpURLConnection connection = connect("GET", step, inputs);
        try {
            if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return Optional.empty();
            }
            try (InputStream stream = connection.getInputStream()) {
                unzip(stream, target);
            }
            return Optional.of(new BuildStepResult(true));
        } catch (IOException _) {
            clean(target);
            return Optional.empty();
        } finally {
            connection.disconnect();
        }
    }

    @Override
    public void store(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      boolean remote,
                      Path output,
                      String digest,
                      Map<Path, byte[]> checksums) throws IOException {
        if (write && remote) {
            upload(step, inputs, output);
        }
    }

    @Override
    public boolean stores() {
        return write;
    }

    @Override
    public void touch(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      boolean remote) {
        if (!read || !remote) {
            return;
        }
        try {
            executor.execute(() -> {
                try {
                    HttpURLConnection connection = connect("HEAD", step, inputs);
                    try {
                        connection.getResponseCode();
                    } finally {
                        connection.disconnect();
                    }
                } catch (IOException | RuntimeException _) {
                }
            });
        } catch (RejectedExecutionException _) {
        }
    }

    private void upload(byte[] step, SequencedMap<String, Map<Path, byte[]>> inputs, Path output) {
        Path temporary = null;
        try {
            temporary = Files.createTempFile("jenesis-cache", ".zip");
            zip(output, temporary);
            HttpURLConnection connection = connect("PUT", step, inputs);
            connection.setDoOutput(true);
            connection.setFixedLengthStreamingMode(Files.size(temporary));
            connection.setRequestProperty("Content-Type", "application/zip");
            connection.setRequestProperty("Expect", "100-continue");
            try (OutputStream out = connection.getOutputStream()) {
                Files.copy(temporary, out);
            } catch (ProtocolException _) {
            }
            connection.getResponseCode();
            connection.disconnect();
        } catch (IOException _) {
        } finally {
            if (temporary != null) {
                try {
                    Files.deleteIfExists(temporary);
                } catch (IOException _) {
                }
            }
        }
    }

    private HttpURLConnection connect(String method, byte[] step, SequencedMap<String, Map<Path, byte[]>> inputs) throws IOException {
        String base = uri.toString();
        if (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        URI target = URI.create(base
                + "/" + HexFormat.of().formatHex(step)
                + "/" + HexFormat.of().formatHex(fold(inputs)));
        String scheme = target.getScheme(), host = target.getHost();
        boolean loopback = "localhost".equals(host) || "127.0.0.1".equals(host) || "::1".equals(host);
        if (!"https".equals(scheme) && !loopback && !insecure) {
            throw new IllegalStateException("Refusing to send the cache key over insecure scheme '"
                    + scheme
                    + "': "
                    + target
                    + " (set -Djenesis.cache.insecure=true to allow plaintext)");
        }
        HttpURLConnection connection = (HttpURLConnection) Repository.connect(target, insecure);
        connection.setRequestMethod(method);
        connection.setConnectTimeout((int) Math.min(connectTimeout.toMillis(), Integer.MAX_VALUE));
        connection.setReadTimeout((int) Math.min(readTimeout.toMillis(), Integer.MAX_VALUE));
        connection.setInstanceFollowRedirects(false);
        connection.setRequestProperty("User-Agent", "Jenesis");
        if (key != null) {
            connection.setRequestProperty(KEY, key);
        }
        if (project != null) {
            connection.setRequestProperty(PROJECT, project);
        }
        return connection;
    }

    private byte[] fold(SequencedMap<String, Map<Path, byte[]>> inputs) {
        MessageDigest message;
        try {
            message = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        inputs.forEach((argument, files) -> {
            message.update(argument.getBytes(StandardCharsets.UTF_8));
            message.update((byte) 0);
            List<Map.Entry<Path, byte[]>> ordered = new ArrayList<>(files.entrySet());
            ordered.sort(Comparator.comparing(entry -> entry.getKey().toString().replace('\\', '/')));
            for (Map.Entry<Path, byte[]> entry : ordered) {
                message.update(entry.getKey().toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                message.update((byte) 0);
                message.update(entry.getValue());
            }
        });
        return message.digest();
    }

    private static void zip(Path source, Path target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)
                        throws IOException {
                    if (!directory.equals(source)) {
                        zip.putNextEntry(new ZipEntry(name(source, directory) + "/"));
                        zip.closeEntry();
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    zip.putNextEntry(new ZipEntry(name(source, file)));
                    Files.copy(file, zip);
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static String trimmed(String name) {
        return name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
    }

    private static String name(Path source, Path path) {
        return source.relativize(path).toString().replace('\\', '/');
    }

    private static void unzip(InputStream source, Path target) throws IOException {
        Path base = target.normalize();
        ZipInputStream zip = new ZipInputStream(source);
        for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
            if (entry.isDirectory()) {
                Files.createDirectories(BuildStep.resolveContained(base, trimmed(entry.getName())));
                continue;
            }
            Path destination = BuildStep.resolveContained(base, entry.getName());
            Files.createDirectories(destination.getParent());
            Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void clean(Path target) {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(target)) {
            for (Path child : children) {
                Files.walkFileTree(child, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.deleteIfExists(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                        Files.deleteIfExists(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        } catch (IOException _) {
        }
    }
}

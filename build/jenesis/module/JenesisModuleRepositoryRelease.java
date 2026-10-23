package build.jenesis.module;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.SafeSegment;

public class JenesisModuleRepositoryRelease implements BuildStep {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();

    private final URI repository;
    private final boolean publish;
    private final transient String token;
    private final transient Repository.Connection connection;
    private final transient Consumer<String> printing;
    private final transient Palette palette;

    public JenesisModuleRepositoryRelease(URI repository) {
        this(repository, false, null, new Repository.Connection(), null, Palette.NONE);
    }

    public static URI configured(Environment environment) {
        String location = environment.value("release.uri");
        if (location == null) {
            location = System.getenv("JENESIS_RELEASE_URI");
        }
        return location == null || location.isBlank() ? null : URI.create(location.strip());
    }

    public static JenesisModuleRepositoryRelease ofEnvironment(Environment environment, URI repository) {
        Repository.Origin origin = environment.getProperty("release.uri") != null
                ? Repository.Origin.of(environment, "release.uri")
                : System.getenv("JENESIS_RELEASE_URI") != null ? Repository.Origin.ENVIRONMENT : Repository.Origin.USER;
        return new JenesisModuleRepositoryRelease(repository,
                environment.flag("release.publish"),
                Repository.Credential.of(environment, "release.token", "JENESIS_RELEASE_TOKEN").grant(origin),
                Repository.Connection.ofEnvironment(environment),
                environment.out(),
                Palette.ofEnvironment(environment));
    }

    private JenesisModuleRepositoryRelease(URI repository,
                                           boolean publish,
                                           String token,
                                           Repository.Connection connection,
                                           Consumer<String> printing,
                                           Palette palette) {
        if (!"https".equals(repository.getScheme()) && !"http".equals(repository.getScheme())) {
            throw new IllegalArgumentException("Cannot release to " + repository
                    + ": a Jenesis module repository is addressed by an https: or http: URI");
        }
        String text = repository.toString();
        this.repository = text.endsWith("/") ? repository : URI.create(text + "/");
        this.publish = publish;
        this.token = token;
        this.connection = connection;
        this.printing = printing;
        this.palette = palette;
    }

    public JenesisModuleRepositoryRelease repository(URI repository) {
        return new JenesisModuleRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public JenesisModuleRepositoryRelease publish(boolean publish) {
        return new JenesisModuleRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public JenesisModuleRepositoryRelease token(String token) {
        return new JenesisModuleRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public JenesisModuleRepositoryRelease connection(Repository.Connection connection) {
        return new JenesisModuleRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public JenesisModuleRepositoryRelease printing(Consumer<String> printing, Palette palette) {
        return new JenesisModuleRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        if (publish && connection.offline()) {
            throw new IllegalStateException("Cannot release to " + repository
                    + " while offline (unset -Djenesis.repository.offline to release)");
        }
        if ("http".equals(repository.getScheme()) && !connection.insecure()) {
            throw new IllegalStateException("Refusing to release over insecure scheme 'http': "
                    + repository
                    + " (set -Djenesis.repository.insecure=true to allow a plaintext repository)");
        }
        SequencedMap<String, Map.Entry<String, Path>> released = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed() || !Files.isDirectory(argument.folder())) {
                continue;
            }
            try (Stream<Path> modules = Files.list(argument.folder())) {
                for (Path module : modules.toList()) {
                    String name = module.getFileName().toString();
                    SAFE_SEGMENT.accept("module name", name);
                    List<Path> versions;
                    if (Files.isDirectory(module)) {
                        try (Stream<Path> children = Files.list(module)) {
                            versions = children.toList();
                        }
                    } else {
                        versions = List.of();
                    }
                    if (versions.size() != 1 || !Files.isDirectory(versions.getFirst())) {
                        throw new IllegalStateException("Cannot release " + name
                                + ": a released module is staged under exactly one version, as"
                                + " <module>/<version>/<module>.jar, so set jenesis.project.version to give it one");
                    }
                    String version = versions.getFirst().getFileName().toString();
                    SAFE_SEGMENT.accept("version", version);
                    Path jar = versions.getFirst().resolve(name + ".jar");
                    if (!Files.isRegularFile(jar)) {
                        throw new IllegalStateException("Cannot release " + name + " " + version
                                + ": no " + name + ".jar is staged for it");
                    }
                    if (released.put(name, Map.entry("module/" + name + "/" + version + "/" + name + ".jar", jar)) != null) {
                        throw new IllegalStateException("Cannot release " + name + ": it is staged more than once");
                    }
                }
            }
        }
        for (Map.Entry<String, Path> entry : released.sequencedValues()) {
            URI target = repository.resolve(entry.getKey());
            if (publish) {
                upload(target, entry.getValue());
            }
            if (printing != null) {
                printing.accept("%s%-11s%s %s".formatted(palette.status(),
                        publish ? "[RELEASED]" : "[DRY RUN]",
                        palette.reset(),
                        target));
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private void upload(URI target, Path file) throws IOException {
        for (int attempt = 0; ; attempt++) {
            int status;
            String reason;
            try {
                HttpURLConnection http = (HttpURLConnection) Repository.connect(target, connection.insecure());
                try {
                    http.setRequestMethod("PUT");
                    http.setConnectTimeout(connection.connectTimeout());
                    http.setReadTimeout(connection.readTimeout());
                    http.setInstanceFollowRedirects(false);
                    http.setDoOutput(true);
                    http.setFixedLengthStreamingMode(Files.size(file));
                    http.setRequestProperty("User-Agent", "Jenesis");
                    http.setRequestProperty("Content-Type", "application/octet-stream");
                    if (token != null) {
                        http.setRequestProperty("Authorization", token);
                    }
                    try (OutputStream out = http.getOutputStream()) {
                        Files.copy(file, out);
                    }
                    status = http.getResponseCode();
                    InputStream body = status < 400 ? http.getInputStream() : http.getErrorStream();
                    if (body == null) {
                        reason = "";
                    } else {
                        try (body) {
                            reason = new String(body.readNBytes(512), StandardCharsets.UTF_8).strip();
                        }
                    }
                } finally {
                    http.disconnect();
                }
            } catch (SocketException | SocketTimeoutException | SSLException | EOFException e) {
                if (attempt >= connection.retries()) {
                    throw new IOException("Failed to release " + file.getFileName() + " to " + target
                            + " after " + (attempt + 1) + " attempt(s): " + e, e);
                }
                pause(connection.backoff().toMillis() << Math.min(attempt, 20), target);
                continue;
            }
            if (status >= 200 && status < 300) {
                return;
            }
            if ((status == 429 || status >= 500) && attempt < connection.retries()) {
                pause(connection.backoff().toMillis() << Math.min(attempt, 20), target);
                continue;
            }
            String detail = reason.isEmpty() ? "" : " (" + reason + ")";
            if (status == 401 || status == 403) {
                throw new IllegalStateException("The repository refused to accept " + target
                        + " with status " + status + detail
                        + ": set jenesis.release.token to a key that may publish to it");
            }
            if (status == 409) {
                throw new IllegalStateException("The repository holds other content at " + target
                        + detail + ", so release under a new version");
            }
            throw new IOException("The repository answered " + target + " with status " + status + detail);
        }
    }

    private static void pause(long delay, URI target) throws InterruptedIOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while releasing to " + target);
        }
    }
}

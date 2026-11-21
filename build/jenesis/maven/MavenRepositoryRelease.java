package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Repository;
import build.jenesis.SafeSegment;

public class MavenRepositoryRelease implements BuildStep {

    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();
    private static final List<String> CHECKSUMS = List.of("md5", "sha1", "sha256", "sha512"),
            UNCHECKED = List.of(".asc", ".sigstore", ".sigstore.json");
    private static final DateTimeFormatter UPDATED = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").withZone(ZoneOffset.UTC),
            TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMdd.HHmmss").withZone(ZoneOffset.UTC);
    private static final String METADATA = "maven-metadata.xml", SNAPSHOT = "SNAPSHOT";

    private final URI repository;
    private final boolean publish;
    private final transient String token;
    private final transient Repository.Connection connection;
    private final transient Consumer<String> printing;
    private final transient Palette palette;

    public MavenRepositoryRelease(URI repository) {
        this(repository, false, null, new Repository.Connection(), null, Palette.NONE);
    }

    public static URI configured(Environment environment) {
        String location = environment.value("deploy.uri");
        if (location == null) {
            location = System.getenv("MAVEN_RELEASE_URI");
        }
        return location == null || location.isBlank() ? null : URI.create(location.strip());
    }

    public static MavenRepositoryRelease ofEnvironment(Environment environment, URI repository) {
        Repository.Origin origin = environment.getProperty("deploy.uri") != null
                ? Repository.Origin.of(environment, "deploy.uri")
                : System.getenv("MAVEN_RELEASE_URI") != null ? Repository.Origin.ENVIRONMENT : Repository.Origin.USER;
        return new MavenRepositoryRelease(repository,
                environment.flag("release.publish"),
                Repository.Credential.of(environment, "deploy.token", "MAVEN_RELEASE_TOKEN").grant(origin),
                Repository.Connection.ofEnvironment(environment),
                environment.out(),
                Palette.ofEnvironment(environment));
    }

    private MavenRepositoryRelease(URI repository,
                                   boolean publish,
                                   String token,
                                   Repository.Connection connection,
                                   Consumer<String> printing,
                                   Palette palette) {
        if (!"https".equals(repository.getScheme()) && !"http".equals(repository.getScheme())) {
            throw new IllegalArgumentException("Cannot release to " + repository
                    + ": a Maven repository is addressed by an https: or http: URI");
        }
        String text = repository.toString();
        this.repository = text.endsWith("/") ? repository : URI.create(text + "/");
        this.publish = publish;
        this.token = token;
        this.connection = connection;
        this.printing = printing;
        this.palette = palette;
    }

    public MavenRepositoryRelease repository(URI repository) {
        return new MavenRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public MavenRepositoryRelease publish(boolean publish) {
        return new MavenRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public MavenRepositoryRelease token(String token) {
        return new MavenRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public MavenRepositoryRelease connection(Repository.Connection connection) {
        return new MavenRepositoryRelease(repository, publish, token, connection, printing, palette);
    }

    public MavenRepositoryRelease printing(Consumer<String> printing, Palette palette) {
        return new MavenRepositoryRelease(repository, publish, token, connection, printing, palette);
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
        SequencedMap<String, SequencedMap<String, SequencedMap<String, Path>>> staged = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed() || !Files.isDirectory(argument.folder())) {
                continue;
            }
            List<Path> files;
            try (Stream<Path> walk = Files.walk(argument.folder())) {
                files = walk.filter(Files::isRegularFile).sorted().toList();
            }
            for (Path file : files) {
                String name = file.getFileName().toString();
                if (name.startsWith("maven-metadata") || CHECKSUMS.stream().anyMatch(extension -> name.endsWith("." + extension))) {
                    continue;
                }
                Path relative = argument.folder().relativize(file);
                int count = relative.getNameCount();
                if (count < 4) {
                    throw new IllegalStateException("Cannot release " + relative + ": a Maven repository holds a file"
                            + " at <group path>/<artifactId>/<version>/<file>, as the stage step lays out its tree");
                }
                for (int index = 0; index < count - 3; index++) {
                    SAFE_SEGMENT.accept("groupId segment", relative.getName(index).toString());
                }
                String artifactId = relative.getName(count - 3).toString(), version = relative.getName(count - 2).toString();
                SAFE_SEGMENT.accept("artifactId", artifactId);
                SAFE_SEGMENT.accept("version", version);
                SAFE_SEGMENT.accept("file name", name);
                String prefix = artifactId + "-" + version;
                String rest = name.startsWith(prefix) ? name.substring(prefix.length()) : "";
                int dot = rest.indexOf('.');
                if (dot < 0 || dot == rest.length() - 1 || dot > 0 && (!rest.startsWith("-") || dot == 1)) {
                    throw new IllegalStateException("Cannot release " + relative + ": a file of " + artifactId + " "
                            + version + " is named " + prefix + "[-<classifier>].<extension>");
                }
                String artifact = relative.subpath(0, count - 2).toString().replace(File.separatorChar, '/');
                if (staged.computeIfAbsent(artifact, _ -> new TreeMap<>())
                        .computeIfAbsent(version, _ -> new TreeMap<>())
                        .put(rest, file) != null) {
                    throw new IllegalStateException("Cannot release " + relative + ": it is staged more than once");
                }
            }
        }
        if (!publish) {
            if (printing != null) {
                for (Map.Entry<String, SequencedMap<String, SequencedMap<String, Path>>> artifact : staged.entrySet()) {
                    String artifactId = artifact.getKey().substring(artifact.getKey().lastIndexOf('/') + 1);
                    for (Map.Entry<String, SequencedMap<String, Path>> version : artifact.getValue().entrySet()) {
                        URI folder = repository.resolve(artifact.getKey() + "/" + version.getKey() + "/");
                        for (String file : version.getValue().keySet()) {
                            printing.accept("%s%-11s%s %s".formatted(palette.status(),
                                    "[DRY RUN]",
                                    palette.reset(),
                                    folder.resolve(artifactId + "-" + version.getKey() + file)));
                        }
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        Instant now = Instant.now();
        String updated = UPDATED.format(now);
        for (Map.Entry<String, SequencedMap<String, SequencedMap<String, Path>>> artifact : staged.entrySet()) {
            int slash = artifact.getKey().lastIndexOf('/');
            String groupId = artifact.getKey().substring(0, slash).replace('/', '.'),
                    artifactId = artifact.getKey().substring(slash + 1);
            Path written = context.next().resolve(artifact.getKey());
            for (Map.Entry<String, SequencedMap<String, Path>> version : artifact.getValue().entrySet()) {
                URI folder = repository.resolve(artifact.getKey() + "/" + version.getKey() + "/");
                Path versionWritten = Files.createDirectories(written.resolve(version.getKey()));
                if (!version.getKey().endsWith(SNAPSHOT)) {
                    for (Map.Entry<String, Path> file : version.getValue().entrySet()) {
                        released(upload(folder, artifactId + "-" + version.getKey() + file.getKey(), file.getValue(), versionWritten));
                    }
                    continue;
                }
                Document existing = metadata(folder.resolve(METADATA));
                SequencedMap<String, SnapshotVersion> snapshots = new LinkedHashMap<>();
                int buildNumber = 1;
                if (existing != null) {
                    String number = text(existing.getDocumentElement(), "buildNumber");
                    try {
                        buildNumber = number == null ? 1 : Integer.parseInt(number) + 1;
                    } catch (NumberFormatException _) {
                        throw new IllegalStateException("Cannot release " + groupId + ":" + artifactId + ":" + version.getKey()
                                + ": " + folder.resolve(METADATA) + " names the build number '" + number
                                + "', which is no number, so repair or remove it there");
                    }
                    NodeList nodes = existing.getElementsByTagNameNS("*", "snapshotVersion");
                    for (int index = 0; index < nodes.getLength(); index++) {
                        Element node = (Element) nodes.item(index);
                        SnapshotVersion snapshot = new SnapshotVersion(Objects.requireNonNullElse(text(node, "classifier"), ""),
                                text(node, "extension"),
                                text(node, "value"),
                                text(node, "updated"));
                        if (snapshot.extension() != null && snapshot.value() != null) {
                            snapshots.put(snapshot.classifier() + ":" + snapshot.extension(), snapshot);
                        }
                    }
                }
                String value = version.getKey().substring(0, version.getKey().length() - SNAPSHOT.length())
                        + TIMESTAMP.format(now) + "-" + buildNumber;
                for (Map.Entry<String, Path> file : version.getValue().entrySet()) {
                    released(upload(folder, artifactId + "-" + value + file.getKey(), file.getValue(), versionWritten));
                    int dot = file.getKey().indexOf('.');
                    SnapshotVersion snapshot = new SnapshotVersion(dot == 0 ? "" : file.getKey().substring(1, dot),
                            file.getKey().substring(dot + 1),
                            value,
                            updated);
                    snapshots.remove(snapshot.classifier() + ":" + snapshot.extension());
                    snapshots.put(snapshot.classifier() + ":" + snapshot.extension(), snapshot);
                }
                Document document = document();
                Element metadata = (Element) document.appendChild(document.createElement("metadata"));
                metadata.setAttribute("modelVersion", "1.1.0");
                append(metadata, "groupId", groupId);
                append(metadata, "artifactId", artifactId);
                append(metadata, "version", version.getKey());
                Element versioning = append(metadata, "versioning", null);
                Element snapshot = append(versioning, "snapshot", null);
                append(snapshot, "timestamp", TIMESTAMP.format(now));
                append(snapshot, "buildNumber", Integer.toString(buildNumber));
                append(versioning, "lastUpdated", updated);
                Element snapshotVersions = append(versioning, "snapshotVersions", null);
                for (SnapshotVersion entry : snapshots.values()) {
                    Element node = append(snapshotVersions, "snapshotVersion", null);
                    if (!entry.classifier().isEmpty()) {
                        append(node, "classifier", entry.classifier());
                    }
                    append(node, "extension", entry.extension());
                    append(node, "value", entry.value());
                    if (entry.updated() != null) {
                        append(node, "updated", entry.updated());
                    }
                }
                upload(folder, METADATA, write(document, versionWritten.resolve(METADATA)), versionWritten);
            }
            URI folder = repository.resolve(artifact.getKey() + "/");
            Document existing = metadata(folder.resolve(METADATA));
            SequencedSet<String> versions = new LinkedHashSet<>(artifact.getValue().keySet());
            if (existing != null) {
                NodeList lists = existing.getElementsByTagNameNS("*", "versions");
                for (int list = 0; list < lists.getLength(); list++) {
                    NodeList nodes = ((Element) lists.item(list)).getElementsByTagNameNS("*", "version");
                    for (int index = 0; index < nodes.getLength(); index++) {
                        String text = nodes.item(index).getTextContent().strip();
                        if (!text.isEmpty()) {
                            versions.add(text);
                        }
                    }
                }
            }
            List<String> sorted = versions.stream().sorted(MavenDefaultVersionNegotiator::compareVersions).toList();
            Document document = document();
            Element metadata = (Element) document.appendChild(document.createElement("metadata"));
            append(metadata, "groupId", groupId);
            append(metadata, "artifactId", artifactId);
            Element versioning = append(metadata, "versioning", null);
            append(versioning, "latest", sorted.getLast());
            sorted.stream().filter(candidate -> !candidate.endsWith(SNAPSHOT)).reduce((_, right) -> right)
                    .ifPresent(release -> append(versioning, "release", release));
            Element listed = append(versioning, "versions", null);
            for (String candidate : sorted) {
                append(listed, "version", candidate);
            }
            append(versioning, "lastUpdated", updated);
            upload(folder, METADATA, write(document, Files.createDirectories(written).resolve(METADATA)), written);
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private void released(URI target) {
        if (printing != null) {
            printing.accept("%s%-11s%s %s".formatted(palette.status(), "[RELEASED]", palette.reset(), target));
        }
    }

    private URI upload(URI folder, String name, Path file, Path written) throws IOException {
        URI target = folder.resolve(name);
        exchange(target, file);
        if (UNCHECKED.stream().anyMatch(name::endsWith)) {
            return target;
        }
        List<MessageDigest> digests = new ArrayList<>();
        for (String extension : CHECKSUMS) {
            try {
                digests.add(MessageDigest.getInstance(extension.equals("md5") ? "MD5" : "SHA-" + extension.substring(3)));
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException("This JVM offers no " + extension + " digest to checksum " + name, e);
            }
        }
        try (InputStream input = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            for (int read = input.read(buffer); read != -1; read = input.read(buffer)) {
                for (MessageDigest digest : digests) {
                    digest.update(buffer, 0, read);
                }
            }
        }
        for (int index = 0; index < CHECKSUMS.size(); index++) {
            String checksum = name + "." + CHECKSUMS.get(index);
            exchange(folder.resolve(checksum), Files.writeString(written.resolve(checksum),
                    HexFormat.of().formatHex(digests.get(index).digest())));
        }
        return target;
    }

    private Document metadata(URI target) throws IOException {
        byte[] content = exchange(target, null);
        if (content == null) {
            return null;
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            return factory.newDocumentBuilder().parse(new ByteArrayInputStream(content));
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("Cannot merge a release into " + target
                    + ": the repository serves no valid Maven metadata there, so repair or remove it", e);
        }
    }

    private byte[] exchange(URI target, Path file) throws IOException {
        String method = file == null ? "GET" : "PUT";
        for (int attempt = 0; ; attempt++) {
            int status;
            byte[] body;
            try {
                HttpURLConnection http = (HttpURLConnection) Repository.connect(target, connection.insecure());
                try {
                    http.setRequestMethod(method);
                    http.setConnectTimeout(connection.connectTimeout());
                    http.setReadTimeout(connection.readTimeout());
                    http.setInstanceFollowRedirects(false);
                    http.setRequestProperty("User-Agent", "Jenesis");
                    if (token != null) {
                        http.setRequestProperty("Authorization", token);
                    }
                    if (file != null) {
                        http.setDoOutput(true);
                        http.setFixedLengthStreamingMode(Files.size(file));
                        http.setRequestProperty("Content-Type", "application/octet-stream");
                        try (OutputStream out = http.getOutputStream()) {
                            Files.copy(file, out);
                        }
                    }
                    status = http.getResponseCode();
                    InputStream stream = status < 400 ? http.getInputStream() : http.getErrorStream();
                    if (stream == null) {
                        body = new byte[0];
                    } else {
                        try (stream) {
                            body = file == null && status / 100 == 2 ? stream.readAllBytes() : stream.readNBytes(512);
                        }
                    }
                } finally {
                    http.disconnect();
                }
            } catch (SocketException | SocketTimeoutException | SSLException | EOFException e) {
                if (attempt >= connection.retries()) {
                    throw new IOException("Failed to " + method + " " + target
                            + " after " + (attempt + 1) + " attempt(s): " + e, e);
                }
                pause(connection.backoff().toMillis() << Math.min(attempt, 20), target);
                continue;
            }
            if (status / 100 == 2) {
                return body;
            }
            if (file == null && status == 404) {
                return null;
            }
            if ((status == 429 || status >= 500) && attempt < connection.retries()) {
                pause(connection.backoff().toMillis() << Math.min(attempt, 20), target);
                continue;
            }
            String reason = new String(body, StandardCharsets.UTF_8).strip();
            String detail = reason.isEmpty() ? "" : " (" + reason + ")";
            if (status == 401 || status == 403) {
                throw new IllegalStateException("The repository refused " + method + " " + target
                        + " with status " + status + detail
                        + ": set jenesis.deploy.token to an Authorization header value that may publish"
                        + " to it, as Basic <credentials> or Bearer <token>");
            }
            if (status == 409) {
                throw new IllegalStateException("The repository holds other content at " + target
                        + detail + ", so release under a new version");
            }
            throw new IOException("The repository answered " + method + " " + target + " with status " + status + detail);
        }
    }

    private static String text(Element parent, String name) {
        NodeList nodes = parent.getElementsByTagNameNS("*", name);
        if (nodes.getLength() == 0) {
            return null;
        }
        String text = nodes.item(0).getTextContent().strip();
        return text.isEmpty() ? null : text;
    }

    private static Element append(Element parent, String name, String text) {
        Element element = (Element) parent.appendChild(parent.getOwnerDocument().createElement(name));
        if (text != null) {
            element.setTextContent(text);
        }
        return element;
    }

    private static Document document() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path write(Document document, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), new StreamResult(out));
        } catch (TransformerException e) {
            throw new IOException(e);
        }
        return target;
    }

    private static void pause(long delay, URI target) throws InterruptedIOException {
        try {
            Thread.sleep(delay);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("Interrupted while releasing to " + target);
        }
    }

    private record SnapshotVersion(String classifier, String extension, String value, String updated) {
    }
}

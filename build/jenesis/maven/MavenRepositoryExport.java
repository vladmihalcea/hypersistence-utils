package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.SafeSegment;
import build.jenesis.SequencedProperties;

public class MavenRepositoryExport implements BuildStep {

    private static final String INSTALLED = "_remote.repositories";
    private static final SafeSegment SAFE_SEGMENT = new SafeSegment();
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    private final Path target;

    public MavenRepositoryExport() {
        this(Path.of(System.getProperty("user.home")).resolve(".m2").resolve("repository"));
    }

    public static MavenRepositoryExport ofEnvironment(Environment environment) {
        String override = environment.getProperty("maven.local", System.getenv("MAVEN_REPOSITORY_LOCAL"));
        return override == null ? new MavenRepositoryExport() : new MavenRepositoryExport(Path.of(override));
    }

    public MavenRepositoryExport(Path target) {
        this.target = target;
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
        SequencedMap<Path, Coordinates> stagedByVersionDir = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path folder = argument.folder();
            if (!Files.isDirectory(folder)) {
                continue;
            }
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    String name = file.getFileName().toString();
                    if (!name.endsWith(".pom")) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path versionDir = file.getParent();
                    if (versionDir == null) {
                        return FileVisitResult.CONTINUE;
                    }
                    Coordinates parsed = Coordinates.parse(file);
                    if (parsed == null) {
                        throw new IOException("Cannot parse maven coordinates from " + file);
                    }
                    stagedByVersionDir.put(versionDir, parsed);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        if (stagedByVersionDir.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        Files.createDirectories(target);
        String timestamp = TIMESTAMP.format(Instant.now());
        SequencedMap<Path, SequencedSet<String>> versionsByArtifactDir = new LinkedHashMap<>();
        SequencedMap<Path, Coordinates> sampleByArtifactDir = new LinkedHashMap<>();
        for (Map.Entry<Path, Coordinates> entry : stagedByVersionDir.entrySet()) {
            Path stagedVersionDir = entry.getKey();
            Coordinates coordinates = entry.getValue();
            SAFE_SEGMENT.accept("groupId", coordinates.groupId());
            SAFE_SEGMENT.accept("artifactId", coordinates.artifactId());
            SAFE_SEGMENT.accept("version", coordinates.version());
            Path targetArtifactDir = target
                    .resolve(coordinates.groupId().replace('.', '/'))
                    .resolve(coordinates.artifactId());
            Path targetVersionDir = targetArtifactDir.resolve(coordinates.version());
            if (!targetVersionDir.normalize().startsWith(target.normalize())) {
                throw new IOException("Resolved path escapes the repository root: " + targetVersionDir);
            }
            Files.createDirectories(targetVersionDir);
            SequencedSet<String> exported = new TreeSet<>();
            try (DirectoryStream<Path> files = Files.newDirectoryStream(stagedVersionDir)) {
                for (Path source : files) {
                    if (!Files.isRegularFile(source)) {
                        continue;
                    }
                    String name = source.getFileName().toString();
                    if (name.endsWith(".xml") || name.equals(INSTALLED)) {
                        continue;
                    }
                    Path destination = targetVersionDir.resolve(name);
                    Files.deleteIfExists(destination);
                    BuildStep.linkOrCopy(destination, source);
                    exported.add(name);
                }
            }
            if (coordinates.version().endsWith("-SNAPSHOT")) {
                writeSnapshotMetadata(targetVersionDir, coordinates, timestamp);
            }
            Path marker = targetVersionDir.resolve(INSTALLED);
            SequencedSet<String> entries = new TreeSet<>();
            if (Files.isRegularFile(marker)) {
                entries.addAll(SequencedProperties.ofFiles(marker).stringPropertyNames());
            }
            exported.forEach(name -> entries.add(name + ">"));
            StringBuilder body = new StringBuilder()
                    .append("#NOTE: This is a Maven Resolver internal implementation file, its format can be changed without prior notice.\n")
                    .append("#")
                    .append(DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now()))
                    .append('\n');
            entries.forEach(installed -> body.append(installed).append("=\n"));
            Files.writeString(marker, body.toString());
            versionsByArtifactDir.computeIfAbsent(targetArtifactDir, _ -> new LinkedHashSet<>())
                    .add(coordinates.version());
            sampleByArtifactDir.putIfAbsent(targetArtifactDir, coordinates);
        }
        for (Map.Entry<Path, SequencedSet<String>> entry : versionsByArtifactDir.entrySet()) {
            writeArtifactMetadata(entry.getKey(),
                    sampleByArtifactDir.get(entry.getKey()),
                    entry.getValue(),
                    timestamp);
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void writeArtifactMetadata(Path artifactDir,
                                              Coordinates sample,
                                              SequencedSet<String> versions,
                                              String timestamp) throws IOException {
        SequencedSet<String> merged = new LinkedHashSet<>(versions);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(artifactDir)) {
            for (Path entry : entries) {
                if (Files.isDirectory(entry)) {
                    merged.add(entry.getFileName().toString());
                }
            }
        }
        merged.addAll(readMetadataVersions(artifactDir.resolve("maven-metadata-local.xml")));
        List<String> sorted = merged.stream()
                .sorted(MavenDefaultVersionNegotiator::compareVersions)
                .toList();
        String release = sorted.stream()
                .filter(version -> !version.endsWith("-SNAPSHOT"))
                .reduce((_, right) -> right)
                .orElse(null);
        String latest = sorted.isEmpty() ? null : sorted.getLast();
        Document document = newDocument();
        Element metadata = (Element) document.appendChild(document.createElement("metadata"));
        metadata.appendChild(document.createElement("groupId")).setTextContent(sample.groupId());
        metadata.appendChild(document.createElement("artifactId")).setTextContent(sample.artifactId());
        Element versioning = (Element) metadata.appendChild(document.createElement("versioning"));
        if (latest != null) {
            versioning.appendChild(document.createElement("latest")).setTextContent(latest);
        }
        if (release != null) {
            versioning.appendChild(document.createElement("release")).setTextContent(release);
        }
        Element versionsNode = (Element) versioning.appendChild(document.createElement("versions"));
        for (String version : sorted) {
            versionsNode.appendChild(document.createElement("version")).setTextContent(version);
        }
        versioning.appendChild(document.createElement("lastUpdated")).setTextContent(timestamp);
        writeXml(document, artifactDir.resolve("maven-metadata-local.xml"));
    }

    private static SequencedSet<String> readMetadataVersions(Path metadata) throws IOException {
        if (!Files.isRegularFile(metadata)) {
            return Collections.emptyNavigableSet();
        }
        SequencedSet<String> versions = new LinkedHashSet<>();
        try (InputStream stream = Files.newInputStream(metadata)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            NodeList nodes = factory.newDocumentBuilder().parse(stream).getElementsByTagName("version");
            for (int index = 0; index < nodes.getLength(); index++) {
                String text = nodes.item(index).getTextContent().trim();
                if (!text.isEmpty()) {
                    versions.add(text);
                }
            }
        } catch (ParserConfigurationException | SAXException _) {
            return Collections.emptyNavigableSet();
        }
        return versions;
    }

    private static void writeSnapshotMetadata(Path versionDir,
                                              Coordinates coordinates,
                                              String timestamp) throws IOException {
        SequencedSet<String> snapshotKeys = new TreeSet<>();
        String prefix = coordinates.artifactId() + "-" + coordinates.version();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(versionDir)) {
            for (Path file : files) {
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                String name = file.getFileName().toString();
                if (!name.startsWith(prefix)) {
                    continue;
                }
                int dot = name.lastIndexOf('.');
                if (dot < 0) {
                    continue;
                }
                String extension = name.substring(dot + 1);
                if (extension.equals("xml") || extension.equals("repositories")) {
                    continue;
                }
                String middle = name.substring(prefix.length(), dot);
                String classifier = middle.startsWith("-") ? middle.substring(1) : "";
                snapshotKeys.add(extension + "|" + classifier);
            }
        }
        Document document = newDocument();
        Element metadata = (Element) document.appendChild(document.createElement("metadata"));
        metadata.setAttribute("modelVersion", "1.1.0");
        metadata.appendChild(document.createElement("groupId")).setTextContent(coordinates.groupId());
        metadata.appendChild(document.createElement("artifactId")).setTextContent(coordinates.artifactId());
        Element versioning = (Element) metadata.appendChild(document.createElement("versioning"));
        versioning.appendChild(document.createElement("lastUpdated")).setTextContent(timestamp);
        Element snapshot = (Element) versioning.appendChild(document.createElement("snapshot"));
        snapshot.appendChild(document.createElement("localCopy")).setTextContent("true");
        Element snapshotVersions = (Element) versioning.appendChild(document.createElement("snapshotVersions"));
        for (String key : snapshotKeys) {
            int separator = key.indexOf('|');
            String extension = key.substring(0, separator);
            String classifier = key.substring(separator + 1);
            Element snapshotVersion = (Element) snapshotVersions.appendChild(document.createElement("snapshotVersion"));
            if (!classifier.isEmpty()) {
                snapshotVersion.appendChild(document.createElement("classifier")).setTextContent(classifier);
            }
            snapshotVersion.appendChild(document.createElement("extension")).setTextContent(extension);
            snapshotVersion.appendChild(document.createElement("value")).setTextContent(coordinates.version());
            snapshotVersion.appendChild(document.createElement("updated")).setTextContent(timestamp);
        }
        metadata.appendChild(document.createElement("version")).setTextContent(coordinates.version());
        writeXml(document, versionDir.resolve("maven-metadata-local.xml"));
    }

    private static Document newDocument() {
        try {
            return DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void writeXml(Document document, Path target) throws IOException {
        try (OutputStream out = Files.newOutputStream(target)) {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(document), new StreamResult(out));
        } catch (TransformerException e) {
            throw new IOException(e);
        }
    }

    private record Coordinates(String groupId, String artifactId, String version) {

        private static Coordinates parse(Path pom) {
            String groupId = null, artifactId = null, version = null;
            try (InputStream stream = Files.newInputStream(pom)) {
                DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                factory.setNamespaceAware(true);
                factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                Element project = factory.newDocumentBuilder().parse(stream).getDocumentElement();
                NodeList children = project.getChildNodes();
                for (int index = 0; index < children.getLength(); index++) {
                    Node node = children.item(index);
                    if (node.getNodeType() != Node.ELEMENT_NODE) {
                        continue;
                    }
                    String name = node.getLocalName() == null ? node.getNodeName() : node.getLocalName();
                    String text = node.getTextContent().trim();
                    switch (name) {
                        case "groupId" -> groupId = text;
                        case "artifactId" -> artifactId = text;
                        case "version" -> version = text;
                    }
                }
            } catch (IOException | ParserConfigurationException | SAXException _) {
                return null;
            }
            if (groupId == null || artifactId == null || version == null) {
                return null;
            }
            return new Coordinates(groupId, artifactId, version);
        }
    }
}

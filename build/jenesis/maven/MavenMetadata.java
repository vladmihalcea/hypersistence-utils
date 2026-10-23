package build.jenesis.maven;

import module java.base;
import module java.xml;
import build.jenesis.RepositoryItem;

public record MavenMetadata(String groupId,
                     String artifactId,
                     String latest,
                     String release,
                     String lastUpdated,
                     List<String> versions) {

    public static MavenMetadata of(RepositoryItem item) throws IOException {
        Document document;
        try (InputStream inputStream = item.toInputStream()) {
            document = MavenDefaultVersionNegotiator.toDocumentBuilderFactory().newDocumentBuilder().parse(inputStream);
        } catch (SAXException | ParserConfigurationException e) {
            throw new IOException("Failed to parse Maven metadata", e);
        }
        Element root = document.getDocumentElement();
        Node versioning = child(root, "versioning");
        Node versions = versioning == null ? null : child(versioning, "versions");
        return new MavenMetadata(text(root, "groupId"),
                text(root, "artifactId"),
                versioning == null ? null : text(versioning, "latest"),
                versioning == null ? null : text(versioning, "release"),
                versioning == null ? null : text(versioning, "lastUpdated"),
                versions == null ? List.of() : MavenPomResolver.toChildren(versions)
                        .filter(node -> Objects.equals(node.getNodeName(), "version"))
                        .map(node -> node.getTextContent().trim())
                        .filter(value -> !value.isEmpty())
                        .toList());
    }

    MavenMetadata merge(MavenMetadata other) {
        SequencedSet<String> merged = new TreeSet<>(MavenDefaultVersionNegotiator::compareVersions);
        merged.addAll(versions);
        merged.addAll(other.versions);
        return new MavenMetadata(groupId == null ? other.groupId : groupId,
                artifactId == null ? other.artifactId : artifactId,
                highest(latest, other.latest),
                highest(release, other.release),
                lastUpdated == null || other.lastUpdated != null && other.lastUpdated.compareTo(lastUpdated) > 0
                        ? other.lastUpdated
                        : lastUpdated,
                List.copyOf(merged));
    }

    public MavenMetadata filter(Predicate<String> admits) {
        List<String> admitted = versions.stream().filter(admits).toList();
        return new MavenMetadata(groupId,
                artifactId,
                latest != null && admits.test(latest)
                        ? latest
                        : admitted.stream().max(MavenDefaultVersionNegotiator::compareVersions).orElse(null),
                release != null && admits.test(release)
                        ? release
                        : admitted.stream()
                                .filter(MavenDefaultVersionNegotiator::isStable)
                                .max(MavenDefaultVersionNegotiator::compareVersions)
                                .orElse(null),
                lastUpdated,
                admitted);
    }

    RepositoryItem toItem() throws IOException {
        Document document;
        try {
            document = MavenDefaultVersionNegotiator.toDocumentBuilderFactory().newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        Element root = document.createElement("metadata");
        document.appendChild(root);
        append(document, root, "groupId", groupId);
        append(document, root, "artifactId", artifactId);
        Element versioning = document.createElement("versioning");
        root.appendChild(versioning);
        append(document, versioning, "latest", latest);
        append(document, versioning, "release", release);
        Element list = document.createElement("versions");
        versioning.appendChild(list);
        for (String version : versions) {
            append(document, list, "version", version);
        }
        append(document, versioning, "lastUpdated", lastUpdated);
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            TransformerFactory factory = TransformerFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.newTransformer().transform(new DOMSource(document), new StreamResult(outputStream));
        } catch (TransformerException e) {
            throw new IOException("Failed to write merged Maven metadata of " + groupId + ":" + artifactId, e);
        }
        byte[] bytes = outputStream.toByteArray();
        return () -> new ByteArrayInputStream(bytes);
    }

    private static String highest(String left, String right) {
        if (left == null || right == null) {
            return left == null ? right : left;
        }
        return MavenDefaultVersionNegotiator.compareVersions(left, right) >= 0 ? left : right;
    }

    private static Node child(Node parent, String name) {
        return MavenPomResolver.toChildren(parent)
                .filter(node -> Objects.equals(node.getNodeName(), name))
                .findFirst()
                .orElse(null);
    }

    private static String text(Node parent, String name) {
        Node node = child(parent, name);
        String value = node == null ? null : node.getTextContent().trim();
        return value == null || value.isEmpty() ? null : value;
    }

    private static void append(Document document, Element parent, String name, String value) {
        if (value != null) {
            Element element = document.createElement(name);
            element.setTextContent(value);
            parent.appendChild(element);
        }
    }
}

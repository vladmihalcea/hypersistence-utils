package build.jenesis;

import module java.base;
import module java.xml;

public record License(String id, String category, String name, String url) implements Serializable {

    public String label() {
        return id != null ? id : name != null ? name : url;
    }

    public License identified(Map<String, String> aliases) {
        if (id != null) {
            return this;
        }
        String identified = name == null ? null : aliases.get(name.toLowerCase(Locale.ROOT).trim());
        if (identified == null && url != null) {
            identified = aliases.get(url.toLowerCase(Locale.ROOT).trim()
                    .replaceFirst("^https?://(www\\.)?", "")
                    .replaceFirst("\\.(txt|html?|php|md)$", "")
                    .replaceFirst("/+$", ""));
        }
        return identified == null ? this : new License(identified, category, name, url);
    }

    public static List<License> ofArchive(Path root) throws IOException {
        Path manifestFile = root.resolve(JarFile.MANIFEST_NAME);
        if (!Files.isRegularFile(manifestFile)) {
            return List.of();
        }
        Manifest manifest;
        try (InputStream inputStream = Files.newInputStream(manifestFile)) {
            manifest = new Manifest(inputStream);
        }
        String location = manifest.getMainAttributes().getValue("Sbom-Location");
        Path file = location == null ? null : root.resolve(location.replaceFirst("^/+", "")).normalize();
        byte[] sbom = file != null && file.startsWith(root) && Files.isRegularFile(file) ? Files.readAllBytes(file) : null;
        List<License> licenses = new ArrayList<>();
        if (sbom != null) {
            try {
                if (location.endsWith(".xml")) {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    factory.setNamespaceAware(true);
                    factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
                    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
                    Element element = factory.newDocumentBuilder()
                            .parse(new ByteArrayInputStream(sbom))
                            .getDocumentElement();
                    for (String name : List.of("metadata", "component", "licenses")) {
                        Element child = null;
                        for (Node node = element == null ? null : element.getFirstChild(); node != null; node = node.getNextSibling()) {
                            if (node instanceof Element candidate && candidate.getLocalName().equals(name)) {
                                child = candidate;
                                break;
                            }
                        }
                        element = child;
                    }
                    for (Node node = element == null ? null : element.getFirstChild(); node != null; node = node.getNextSibling()) {
                        if (node instanceof Element entry) {
                            String[] values = new String[3];
                            if (entry.getLocalName().equals("expression")) {
                                values[1] = entry.getTextContent().trim();
                            }
                            for (Node field = entry.getFirstChild(); field != null; field = field.getNextSibling()) {
                                if (field instanceof Element value) {
                                    switch (value.getLocalName()) {
                                        case "id" -> values[0] = value.getTextContent().trim();
                                        case "name" -> values[1] = value.getTextContent().trim();
                                        case "url" -> values[2] = value.getTextContent().trim();
                                        default -> {
                                        }
                                    }
                                }
                            }
                            if (values[0] != null || values[1] != null || values[2] != null) {
                                licenses.add(new License(values[0], null, values[1], values[2]));
                            }
                        }
                    }
                } else if (Json.parse(new String(sbom, StandardCharsets.UTF_8)) instanceof Map<?, ?> document
                        && document.get("metadata") instanceof Map<?, ?> metadata
                        && metadata.get("component") instanceof Map<?, ?> component
                        && component.get("licenses") instanceof List<?> entries) {
                    for (Object entry : entries) {
                        if (entry instanceof Map<?, ?> choice && choice.get("expression") instanceof String expression) {
                            licenses.add(new License(null, null, expression, null));
                        } else if (entry instanceof Map<?, ?> choice && choice.get("license") instanceof Map<?, ?> license) {
                            licenses.add(new License(
                                    license.get("id") instanceof String id ? id : null,
                                    null,
                                    license.get("name") instanceof String name ? name : null,
                                    license.get("url") instanceof String url ? url : null));
                        }
                    }
                }
            } catch (RuntimeException | IOException | ParserConfigurationException | SAXException _) {
                licenses.clear();
            }
        }
        String bundle = manifest.getMainAttributes().getValue("Bundle-License");
        if (licenses.isEmpty() && bundle != null) {
            for (String clause : bundle.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")) {
                String[] parts = clause.split(";");
                String value = parts[0].trim().replaceAll("^\"|\"$", "");
                String link = null;
                for (int index = 1; index < parts.length; index++) {
                    String[] attribute = parts[index].split("=", 2);
                    if (attribute.length == 2 && attribute[0].trim().equals("link")) {
                        link = attribute[1].trim().replaceAll("^\"|\"$", "");
                    }
                }
                if (!value.isEmpty() && !value.equals("<<EXTERNAL>>")) {
                    licenses.add(value.contains("://")
                            ? new License(null, null, null, value)
                            : new License(null, null, value, link));
                }
            }
        }
        return licenses;
    }
}

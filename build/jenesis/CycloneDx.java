package build.jenesis;

import module java.base;
import module java.xml;

public class CycloneDx {

    public enum Format {

        JSON("cdx.json"), XML("cdx.xml");

        private final String extension;

        Format(String extension) {
            this.extension = extension;
        }

        public String extension() {
            return extension;
        }
    }

    private static final String SPEC_VERSION = "1.6",
            NAMESPACE = "http://cyclonedx.org/schema/bom/" + SPEC_VERSION;
    private static final Set<String> DEFAULT_IDENTIFIERS = Set.of(
            "Apache-2.0", "MIT", "MIT-0",
            "BSD-2-Clause", "BSD-3-Clause", "ISC", "BSL-1.0", "Zlib", "PSF-2.0",
            "Unlicense", "CC0-1.0", "WTFPL",
            "EPL-1.0", "EPL-2.0", "MPL-1.1", "MPL-2.0", "CDDL-1.0", "CDDL-1.1",
            "LGPL-2.1-only", "LGPL-2.1-or-later", "LGPL-3.0-only", "LGPL-3.0-or-later",
            "GPL-2.0-with-classpath-exception",
            "GPL-2.0-only", "GPL-2.0-or-later", "GPL-3.0-only", "GPL-3.0-or-later",
            "AGPL-3.0-only", "AGPL-3.0-or-later");

    private final Set<String> identifiers;

    public CycloneDx() {
        this(DEFAULT_IDENTIFIERS);
    }

    private CycloneDx(Set<String> identifiers) {
        this.identifiers = identifiers;
    }

    public CycloneDx identifiers(Set<String> identifiers) {
        return new CycloneDx(identifiers);
    }

    public record Component(String type,
                            String bomRef,
                            String group,
                            String name,
                            String version,
                            String purl,
                            List<String> swhids,
                            String sha256,
                            List<License> licenses,
                            String description,
                            List<Author> authors,
                            List<ExternalReference> externalReferences,
                            List<Property> properties,
                            Organization supplier,
                            String copyright,
                            Organization manufacturer,
                            String publisher,
                            String scope) {

        public Component(String bomRef, String group, String name, String version, String purl, String sha256, List<License> licenses) {
            this("library",
                    bomRef,
                    group,
                    name,
                    version,
                    purl,
                    List.of(),
                    sha256,
                    licenses,
                    null,
                    List.of(),
                    List.of(),
                    List.of(),
                    null,
                    null,
                    null,
                    null,
                    null);
        }

        public Component scope(String scope) {
            return new Component(type,
                    bomRef,
                    group,
                    name,
                    version,
                    purl,
                    swhids,
                    sha256,
                    licenses,
                    description,
                    authors,
                    externalReferences,
                    properties,
                    supplier,
                    copyright,
                    manufacturer,
                    publisher,
                    scope);
        }

        public Component externalReferences(List<ExternalReference> externalReferences) {
            return new Component(type,
                    bomRef,
                    group,
                    name,
                    version,
                    purl,
                    swhids,
                    sha256,
                    licenses,
                    description,
                    authors,
                    externalReferences,
                    properties,
                    supplier,
                    copyright,
                    manufacturer,
                    publisher,
                    scope);
        }

        private SequencedMap<String, Organization> organizations() {
            SequencedMap<String, Organization> organizations = new LinkedHashMap<>();
            if (supplier != null) {
                organizations.put("supplier", supplier);
            }
            if (manufacturer != null) {
                organizations.put("manufacturer", manufacturer);
            }
            return organizations;
        }
    }

    public record Organization(String name, String url) {
    }

    public record Author(String name, String email) {
    }

    public record ExternalReference(String type, String url) {
    }

    public record Property(String name, String value) {
    }

    public record Dependency(String ref, List<String> dependsOn) {
    }

    public String emit(Format format, Component metadata, List<Component> components, List<Dependency> dependencies) {
        List<Component> sortedComponents = new ArrayList<>(components);
        sortedComponents.sort(Comparator.comparing(component -> component.purl() == null
                ? component.group() + ":" + component.name() + ":" + component.version()
                : component.purl()));
        List<Dependency> sortedDependencies = new ArrayList<>();
        for (Dependency dependency : dependencies) {
            List<String> dependsOn = new ArrayList<>(dependency.dependsOn());
            dependsOn.sort(Comparator.naturalOrder());
            sortedDependencies.add(new Dependency(dependency.ref(), dependsOn));
        }
        sortedDependencies.sort(Comparator.comparing(Dependency::ref));
        return switch (format) {
            case XML -> {
                Document document = buildXml(metadata, sortedComponents, sortedDependencies);
                String serialLess = transformXml(document);
                document.getDocumentElement().setAttribute("serialNumber",
                        "urn:uuid:" + UUID.nameUUIDFromBytes(serialLess.getBytes(StandardCharsets.UTF_8)));
                yield transformXml(document);
            }
            case JSON -> {
                String serialLess = emitJson(metadata, sortedComponents, sortedDependencies);
                String serialNumber = "urn:uuid:" + UUID.nameUUIDFromBytes(serialLess.getBytes(StandardCharsets.UTF_8));
                int insert = serialLess.indexOf("  \"version\": 1,\n");
                yield serialLess.substring(0, insert)
                        + "  \"serialNumber\": \"" + Json.escaped(serialNumber) + "\",\n"
                        + serialLess.substring(insert);
            }
        };
    }

    private String emitJson(Component metadata, List<Component> components, List<Dependency> dependencies) {
        StringBuilder builder = new StringBuilder();
        builder.append("{\n");
        builder.append("  \"bomFormat\": \"CycloneDX\",\n");
        builder.append("  \"specVersion\": \"").append(SPEC_VERSION).append("\",\n");
        builder.append("  \"version\": 1,\n");
        builder.append("  \"metadata\": {\n");
        builder.append("    \"tools\": {\n      \"components\": [\n        { \"type\": \"application\", \"name\": \"Jenesis\" }\n      ]\n    }");
        if (metadata != null) {
            builder.append(",\n    \"component\": ");
            appendJsonComponent(builder, metadata, 4);
        }
        builder.append("\n  }");
        if (!components.isEmpty()) {
            builder.append(",\n  \"components\": [\n");
            for (int index = 0; index < components.size(); index++) {
                builder.append("    ");
                appendJsonComponent(builder, components.get(index), 4);
                builder.append(index + 1 < components.size() ? ",\n" : "\n");
            }
            builder.append("  ]");
        }
        if (!dependencies.isEmpty()) {
            builder.append(",\n  \"dependencies\": [\n");
            for (int index = 0; index < dependencies.size(); index++) {
                Dependency dependency = dependencies.get(index);
                builder.append("    { \"ref\": \"").append(Json.escaped(dependency.ref())).append("\"");
                if (!dependency.dependsOn().isEmpty()) {
                    builder.append(", \"dependsOn\": [");
                    for (int on = 0; on < dependency.dependsOn().size(); on++) {
                        builder.append(on > 0 ? ", " : "")
                                .append("\"").append(Json.escaped(dependency.dependsOn().get(on))).append("\"");
                    }
                    builder.append("]");
                }
                builder.append(" }").append(index + 1 < dependencies.size() ? ",\n" : "\n");
            }
            builder.append("  ]");
        }
        builder.append("\n}\n");
        return builder.toString();
    }

    private void appendJsonComponent(StringBuilder builder, Component component, int indent) {
        String pad = " ".repeat(indent);
        builder.append("{\n");
        builder.append(pad).append("  \"type\": \"").append(Json.escaped(component.type())).append("\",\n");
        if (component.bomRef() != null) {
            builder.append(pad).append("  \"bom-ref\": \"").append(Json.escaped(component.bomRef())).append("\",\n");
        }
        for (Map.Entry<String, Organization> entry : component.organizations().entrySet()) {
            Organization organization = entry.getValue();
            builder.append(pad).append("  \"").append(entry.getKey()).append("\": {");
            if (organization.name() != null) {
                builder.append(" \"name\": \"").append(Json.escaped(organization.name())).append("\"");
            }
            if (organization.url() != null) {
                builder.append(organization.name() != null ? "," : "")
                        .append(" \"url\": [\"").append(Json.escaped(organization.url())).append("\"]");
            }
            builder.append(" },\n");
        }
        if (component.publisher() != null) {
            builder.append(pad).append("  \"publisher\": \"").append(Json.escaped(component.publisher())).append("\",\n");
        }
        if (component.group() != null) {
            builder.append(pad).append("  \"group\": \"").append(Json.escaped(component.group())).append("\",\n");
        }
        builder.append(pad).append("  \"name\": \"").append(Json.escaped(component.name())).append("\"");
        if (component.version() != null) {
            builder.append(",\n").append(pad).append("  \"version\": \"").append(Json.escaped(component.version())).append("\"");
        }
        if (component.description() != null) {
            builder.append(",\n").append(pad).append("  \"description\": \"").append(Json.escaped(component.description())).append("\"");
        }
        if (component.scope() != null) {
            builder.append(",\n").append(pad).append("  \"scope\": \"").append(Json.escaped(component.scope())).append("\"");
        }
        if (component.authors() != null && !component.authors().isEmpty()) {
            builder.append(",\n").append(pad).append("  \"authors\": [\n");
            for (int index = 0; index < component.authors().size(); index++) {
                Author author = component.authors().get(index);
                builder.append(pad).append("    {");
                if (author.name() != null) {
                    builder.append(" \"name\": \"").append(Json.escaped(author.name())).append("\"");
                }
                if (author.email() != null) {
                    builder.append(author.name() != null ? "," : "")
                            .append(" \"email\": \"").append(Json.escaped(author.email())).append("\"");
                }
                builder.append(" }").append(index + 1 < component.authors().size() ? ",\n" : "\n");
            }
            builder.append(pad).append("  ]");
        }
        if (component.sha256() != null) {
            builder.append(",\n").append(pad).append("  \"hashes\": [\n");
            builder.append(pad).append("    { \"alg\": \"SHA-256\", \"content\": \"")
                    .append(Json.escaped(component.sha256())).append("\" }\n");
            builder.append(pad).append("  ]");
        }
        if (component.licenses() != null && !component.licenses().isEmpty()) {
            builder.append(",\n").append(pad).append("  \"licenses\": [\n");
            for (int index = 0; index < component.licenses().size(); index++) {
                License license = component.licenses().get(index);
                builder.append(pad).append("    { \"license\": { ");
                if (license.id() != null && identifiers.contains(license.id())) {
                    builder.append("\"id\": \"").append(Json.escaped(license.id())).append("\"");
                } else {
                    String name = license.id() != null ? license.id() : license.name();
                    builder.append("\"name\": \"").append(Json.escaped(name == null ? "" : name)).append("\"");
                    if (license.url() != null) {
                        builder.append(", \"url\": \"").append(Json.escaped(license.url())).append("\"");
                    }
                }
                builder.append(" } }").append(index + 1 < component.licenses().size() ? ",\n" : "\n");
            }
            builder.append(pad).append("  ]");
        }
        if (component.copyright() != null) {
            builder.append(",\n").append(pad).append("  \"copyright\": \"").append(Json.escaped(component.copyright())).append("\"");
        }
        if (component.purl() != null) {
            builder.append(",\n").append(pad).append("  \"purl\": \"").append(Json.escaped(component.purl())).append("\"");
        }
        if (component.swhids() != null && !component.swhids().isEmpty()) {
            builder.append(",\n").append(pad).append("  \"swhid\": [")
                    .append(component.swhids().stream()
                            .map(swhid -> "\"" + Json.escaped(swhid) + "\"")
                            .collect(Collectors.joining(", ")))
                    .append("]");
        }
        if (component.externalReferences() != null && !component.externalReferences().isEmpty()) {
            builder.append(",\n").append(pad).append("  \"externalReferences\": [\n");
            for (int index = 0; index < component.externalReferences().size(); index++) {
                ExternalReference reference = component.externalReferences().get(index);
                builder.append(pad).append("    { \"type\": \"").append(Json.escaped(reference.type()))
                        .append("\", \"url\": \"").append(Json.escaped(reference.url())).append("\" }")
                        .append(index + 1 < component.externalReferences().size() ? ",\n" : "\n");
            }
            builder.append(pad).append("  ]");
        }
        if (component.properties() != null && !component.properties().isEmpty()) {
            builder.append(",\n").append(pad).append("  \"properties\": [\n");
            for (int index = 0; index < component.properties().size(); index++) {
                Property property = component.properties().get(index);
                builder.append(pad).append("    { \"name\": \"").append(Json.escaped(property.name()))
                        .append("\", \"value\": \"").append(Json.escaped(property.value())).append("\" }")
                        .append(index + 1 < component.properties().size() ? ",\n" : "\n");
            }
            builder.append(pad).append("  ]");
        }
        builder.append("\n").append(pad).append("}");
    }

    private Document buildXml(Component metadata, List<Component> components, List<Dependency> dependencies) {
        Document document;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            document = factory.newDocumentBuilder().newDocument();
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
        Element bom = (Element) document.appendChild(document.createElementNS(NAMESPACE, "bom"));
        bom.setAttribute("version", "1");
        Element meta = (Element) bom.appendChild(document.createElementNS(NAMESPACE, "metadata"));
        Element tools = (Element) meta.appendChild(document.createElementNS(NAMESPACE, "tools"));
        Element toolComponents = (Element) tools.appendChild(document.createElementNS(NAMESPACE, "components"));
        Element tool = (Element) toolComponents.appendChild(document.createElementNS(NAMESPACE, "component"));
        tool.setAttribute("type", "application");
        appendXmlText(document, tool, "name", "Jenesis");
        if (metadata != null) {
            appendXmlComponent(document, meta, metadata);
        }
        if (!components.isEmpty()) {
            Element wrapper = (Element) bom.appendChild(document.createElementNS(NAMESPACE, "components"));
            for (Component component : components) {
                appendXmlComponent(document, wrapper, component);
            }
        }
        if (!dependencies.isEmpty()) {
            Element wrapper = (Element) bom.appendChild(document.createElementNS(NAMESPACE, "dependencies"));
            for (Dependency dependency : dependencies) {
                Element node = (Element) wrapper.appendChild(document.createElementNS(NAMESPACE, "dependency"));
                node.setAttribute("ref", dependency.ref());
                for (String on : dependency.dependsOn()) {
                    Element child = (Element) node.appendChild(document.createElementNS(NAMESPACE, "dependency"));
                    child.setAttribute("ref", on);
                }
            }
        }
        return document;
    }

    private String transformXml(Document document) {
        Transformer transformer;
        try {
            transformer = TransformerFactory.newInstance().newTransformer();
        } catch (TransformerConfigurationException e) {
            throw new IllegalStateException(e);
        }
        transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        StringWriter writer = new StringWriter();
        try {
            transformer.transform(new DOMSource(document), new StreamResult(writer));
        } catch (TransformerException e) {
            throw new IllegalStateException(e);
        }
        return writer.toString().replace("\r\n", "\n");
    }

    private void appendXmlComponent(Document document, Node parent, Component component) {
        Element node = (Element) parent.appendChild(document.createElementNS(NAMESPACE, "component"));
        node.setAttribute("type", component.type());
        if (component.bomRef() != null) {
            node.setAttribute("bom-ref", component.bomRef());
        }
        for (Map.Entry<String, Organization> entry : component.organizations().entrySet()) {
            Element organization = (Element) node.appendChild(document.createElementNS(NAMESPACE, entry.getKey()));
            if (entry.getValue().name() != null) {
                appendXmlText(document, organization, "name", entry.getValue().name());
            }
            if (entry.getValue().url() != null) {
                appendXmlText(document, organization, "url", entry.getValue().url());
            }
        }
        if (component.authors() != null && !component.authors().isEmpty()) {
            Element authors = (Element) node.appendChild(document.createElementNS(NAMESPACE, "authors"));
            for (Author author : component.authors()) {
                Element entry = (Element) authors.appendChild(document.createElementNS(NAMESPACE, "author"));
                if (author.name() != null) {
                    appendXmlText(document, entry, "name", author.name());
                }
                if (author.email() != null) {
                    appendXmlText(document, entry, "email", author.email());
                }
            }
        }
        if (component.publisher() != null) {
            appendXmlText(document, node, "publisher", component.publisher());
        }
        if (component.group() != null) {
            appendXmlText(document, node, "group", component.group());
        }
        appendXmlText(document, node, "name", component.name());
        if (component.version() != null) {
            appendXmlText(document, node, "version", component.version());
        }
        if (component.description() != null) {
            appendXmlText(document, node, "description", component.description());
        }
        if (component.scope() != null) {
            appendXmlText(document, node, "scope", component.scope());
        }
        if (component.sha256() != null) {
            Element hashes = (Element) node.appendChild(document.createElementNS(NAMESPACE, "hashes"));
            Element hash = (Element) hashes.appendChild(document.createElementNS(NAMESPACE, "hash"));
            hash.setAttribute("alg", "SHA-256");
            hash.setTextContent(component.sha256());
        }
        if (component.licenses() != null && !component.licenses().isEmpty()) {
            Element licenses = (Element) node.appendChild(document.createElementNS(NAMESPACE, "licenses"));
            for (License license : component.licenses()) {
                Element wrapper = (Element) licenses.appendChild(document.createElementNS(NAMESPACE, "license"));
                if (license.id() != null && identifiers.contains(license.id())) {
                    appendXmlText(document, wrapper, "id", license.id());
                } else {
                    String name = license.id() != null ? license.id() : license.name();
                    appendXmlText(document, wrapper, "name", name == null ? "" : name);
                    if (license.url() != null) {
                        appendXmlText(document, wrapper, "url", license.url());
                    }
                }
            }
        }
        if (component.copyright() != null) {
            appendXmlText(document, node, "copyright", component.copyright());
        }
        if (component.purl() != null) {
            appendXmlText(document, node, "purl", component.purl());
        }
        if (component.swhids() != null) {
            for (String swhid : component.swhids()) {
                appendXmlText(document, node, "swhid", swhid);
            }
        }
        if (component.externalReferences() != null && !component.externalReferences().isEmpty()) {
            Element references = (Element) node.appendChild(document.createElementNS(NAMESPACE, "externalReferences"));
            for (ExternalReference reference : component.externalReferences()) {
                Element entry = (Element) references.appendChild(document.createElementNS(NAMESPACE, "reference"));
                entry.setAttribute("type", reference.type());
                appendXmlText(document, entry, "url", reference.url());
            }
        }
        if (component.properties() != null && !component.properties().isEmpty()) {
            Element properties = (Element) node.appendChild(document.createElementNS(NAMESPACE, "properties"));
            for (Property property : component.properties()) {
                Element entry = (Element) properties.appendChild(document.createElementNS(NAMESPACE, "property"));
                entry.setAttribute("name", property.name());
                entry.setTextContent(property.value());
            }
        }
    }

    private static void appendXmlText(Document document, Node parent, String name, String text) {
        parent.appendChild(document.createElementNS(NAMESPACE, name)).setTextContent(text);
    }
}

package build.jenesis.maven;

import module java.base;
import build.jenesis.SequencedProperties;

public class MavenUriParser implements Function<String, String>, Serializable {

    @SuppressWarnings("unchecked")
    public static <F extends Function<String, String> & Serializable> F ofUris(MavenUriParser parser,
                                                                               String location,
                                                                               Iterable<Path> folders)
            throws IOException {
        SequencedProperties properties = SequencedProperties.ofFolders(folders, location);
        return (F) (Function<String, String> & Serializable) property -> {
            String value = properties.getProperty(property);
            if (value == null) {
                throw new IllegalArgumentException("Could not translate " + property);
            }
            return parser.apply(value);
        };
    }

    @Override
    public String apply(String value) {
        URI uri = URI.create(value);
        String[] elements = uri.getPath().split("/");
        String fileName = elements[elements.length - 1];
        String artifactId = elements[elements.length - 3];
        String version = elements[elements.length - 2];
        int dot = fileName.lastIndexOf('.');
        String type = fileName.substring(dot + 1);
        String stem = fileName.substring(0, dot);
        String prefix = artifactId + "-" + version;
        String classifier = stem.length() > prefix.length() ? stem.substring(prefix.length() + 1) : "";
        return String.join(".", Arrays.asList(elements).subList(2, elements.length - 3))
                + "/" + artifactId
                + (Objects.equals(type, "jar") && classifier.isEmpty() ? "" : "/" + type)
                + (classifier.isEmpty() ? "" : "/" + classifier)
                + "/" + version;
    }
}

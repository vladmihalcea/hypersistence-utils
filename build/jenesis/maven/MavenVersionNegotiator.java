package build.jenesis.maven;

import module java.base;

@FunctionalInterface
public interface MavenVersionNegotiator extends Serializable {

    String resolve(Executor executor,
                   MavenRepository repository,
                   String groupId,
                   String artifactId,
                   String type,
                   String classifier,
                   String version) throws IOException;

    default String resolve(Executor executor,
                           MavenRepository repository,
                           String groupId,
                           String artifactId,
                           String type,
                           String classifier,
                           String current,
                           SequencedSet<String> versions) throws IOException {
        return current;
    }

    default void discovered(String groupId,
                            String artifactId,
                            String type,
                            String classifier,
                            String version,
                            boolean managed) {
    }
}

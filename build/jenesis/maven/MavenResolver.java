package build.jenesis.maven;

import module java.base;
import build.jenesis.License;
import build.jenesis.Repository;
import build.jenesis.Resolver;

public interface MavenResolver extends Resolver {

    SequencedMap<Path, MavenLocalPom> local(Executor executor, Repository repository, Path root) throws IOException;

    Closure dependencies(Executor executor,
                         MavenRepository repository,
                         List<RootPom> rootPoms,
                         Map<MavenDependencyKey, MavenDependencyValue> managedCoordinates,
                         MavenDependencyScope scope,
                         String prefix) throws IOException;

    record Closure(SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                   SequencedMap<String, MavenDependencyKey> roots,
                   List<Resolver.Edge> edges,
                   SequencedMap<String, List<License>> licenses) {
    }

    static MavenResolver of(Resolver resolver) {
        if (resolver instanceof MavenResolver mavenResolver) {
            return mavenResolver;
        }
        throw new IllegalArgumentException("Resolver "
                + (resolver == null ? "null" : resolver.getClass().getName())
                + " is not a MavenResolver");
    }

    record RootPom(InputStream pom,
                   String checksum,
                   String identifier,
                   boolean versionPinned,
                   List<MavenDependencyName> exclusions) {

        public RootPom(InputStream pom) {
            this(pom, null, null, false, null);
        }
    }
}

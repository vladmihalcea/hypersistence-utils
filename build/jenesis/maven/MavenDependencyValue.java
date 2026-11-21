package build.jenesis.maven;

import module java.base;

public record MavenDependencyValue(String version,
                                   MavenDependencyScope scope,
                                   Path systemPath,
                                   List<MavenDependencyName> exclusions,
                                   Boolean optional,
                                   String checksum) {

    public MavenDependencyValue(String version,
                                MavenDependencyScope scope,
                                Path systemPath,
                                List<MavenDependencyName> exclusions,
                                Boolean optional) {
        this(version, scope, systemPath, exclusions, optional, null);
    }

    public MavenDependencyValue version(String version) {
        return new MavenDependencyValue(version, scope, systemPath, exclusions, optional, checksum);
    }

    public MavenDependencyValue exclusions(List<MavenDependencyName> exclusions) {
        return new MavenDependencyValue(version, scope, systemPath, exclusions, optional, checksum);
    }
}

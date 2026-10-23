package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Environment;
import build.jenesis.Project;
import build.jenesis.maven.MavenRepositoryRelease;
import build.jenesis.module.JenesisModuleRepositoryRelease;

public class ReleaseModule implements BuildExecutorModule {

    public static final String JENESIS = "jenesis", MAVEN = "maven";

    private final JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease;
    private final MavenRepositoryRelease mavenRepositoryRelease;

    public ReleaseModule() {
        this(null, null);
    }

    public static ReleaseModule ofEnvironment(Environment environment) {
        URI repository = JenesisModuleRepositoryRelease.configured(environment),
                maven = MavenRepositoryRelease.configured(environment);
        return new ReleaseModule(repository == null ? null : JenesisModuleRepositoryRelease.ofEnvironment(environment, repository),
                                 maven == null ? null : MavenRepositoryRelease.ofEnvironment(environment, maven));
    }

    private ReleaseModule(JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease,
                          MavenRepositoryRelease mavenRepositoryRelease) {
        this.jenesisModuleRepositoryRelease = jenesisModuleRepositoryRelease;
        this.mavenRepositoryRelease = mavenRepositoryRelease;
    }

    public ReleaseModule jenesisModuleRepositoryRelease(JenesisModuleRepositoryRelease jenesisModuleRepositoryRelease) {
        return new ReleaseModule(jenesisModuleRepositoryRelease, mavenRepositoryRelease);
    }

    public ReleaseModule mavenRepositoryRelease(MavenRepositoryRelease mavenRepositoryRelease) {
        return new ReleaseModule(jenesisModuleRepositoryRelease, mavenRepositoryRelease);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        if (jenesisModuleRepositoryRelease != null) {
            String modular = BuildExecutorModule.PREVIOUS + Project.STAGE + "/modular";
            if (!inherited.containsKey(modular)) {
                throw new IllegalStateException("A Jenesis module repository is configured to release to, but this"
                        + " layout stages no modular tree: build with the modular or modular_to_maven layout, or"
                        + " unset jenesis.release.uri");
            }
            buildExecutor.addStep(JENESIS, jenesisModuleRepositoryRelease, modular);
        }
        if (mavenRepositoryRelease != null) {
            String maven = BuildExecutorModule.PREVIOUS + Project.STAGE + "/maven";
            if (!inherited.containsKey(maven)) {
                throw new IllegalStateException("A Maven repository is configured to release to, but this layout"
                        + " stages no Maven tree: build with the maven or modular_to_maven layout, or unset"
                        + " jenesis.deploy.uri");
            }
            buildExecutor.addStep(MAVEN, mavenRepositoryRelease, maven);
        }
    }
}

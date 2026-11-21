package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Launcher;
import build.jenesis.step.Sbom;

public class LauncherModule implements BuildExecutorModule {

    public static final String BUNDLE = "bundle";
    private static final String REQUIRED = "required", DEPENDENCIES = "dependencies", SBOM = "sbom";

    private final Dependencies dependencies;
    private final Pinning pinning;
    private final String group;
    private final PathPlacement pathPlacement;
    private final Sbom sbom;
    private final SequencedSet<String> sbomInputs;
    private final Launcher launcher;

    public LauncherModule(Map<String, Repository> repositories,
                          Map<String, Resolver> resolvers) {
        this(new Dependencies(repositories, resolvers),
             null,
             "launcher",
             PathPlacement.INFERRED,
             null,
             null,
             new Launcher("launcher", PathPlacement.INFERRED));
    }

    public static LauncherModule ofEnvironment(Environment environment,
                                               Map<String, Repository> repositories,
                                               Map<String, Resolver> resolvers) {
        return new LauncherModule(Dependencies.ofEnvironment(environment, repositories, resolvers),
                null,
                "launcher",
                PathPlacement.INFERRED,
                null,
                null,
                Launcher.ofEnvironment(environment, "launcher", PathPlacement.INFERRED));
    }

    private LauncherModule(Dependencies dependencies,
                           Pinning pinning,
                           String group,
                           PathPlacement pathPlacement,
                           Sbom sbom,
                           SequencedSet<String> sbomInputs,
                           Launcher launcher) {
        this.dependencies = dependencies;
        this.pinning = pinning;
        this.group = group;
        this.pathPlacement = pathPlacement;
        this.sbom = sbom;
        this.sbomInputs = sbomInputs;
        this.launcher = launcher;
    }

    public LauncherModule pinning(Pinning pinning) {
        return new LauncherModule(dependencies, pinning, group, pathPlacement, sbom, sbomInputs, launcher);
    }

    public LauncherModule group(String group) {
        return new LauncherModule(dependencies, pinning, group, pathPlacement, sbom, sbomInputs, launcher);
    }

    public LauncherModule pathPlacement(PathPlacement pathPlacement) {
        return new LauncherModule(dependencies, pinning, group, pathPlacement, sbom, sbomInputs, launcher);
    }

    public LauncherModule sbom(Sbom sbom) {
        return new LauncherModule(dependencies, pinning, group, pathPlacement, sbom, sbomInputs, launcher);
    }

    public LauncherModule sbomInputs(SequencedSet<String> sbomInputs) {
        return new LauncherModule(dependencies, pinning, group, pathPlacement, sbom, sbomInputs, launcher);
    }

    public LauncherModule launcher(Launcher launcher) {
        return new LauncherModule(dependencies, pinning, group, pathPlacement, sbom, sbomInputs, launcher);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REQUIRED, new Requires(group), inherited.sequencedKeySet());
        SequencedSet<String> resolveInputs = new LinkedHashSet<>();
        resolveInputs.add(REQUIRED);
        resolveInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addModule(DEPENDENCIES,
                dependencies.pinning(pinning).group(group),
                resolveInputs);
        SequencedSet<String> bundleInputs = new LinkedHashSet<>();
        if (sbom != null) {
            SequencedSet<String> described = new LinkedHashSet<>();
            if (sbomInputs == null) {
                described.addAll(inherited.sequencedKeySet());
            } else {
                sbomInputs.forEach(input -> described.add(PREVIOUS + input));
            }
            described.add(DEPENDENCIES);
            buildExecutor.addStep(SBOM, sbom, described);
            bundleInputs.add(SBOM);
        }
        bundleInputs.add(DEPENDENCIES);
        bundleInputs.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(BUNDLE, launcher.tool(group).pathPlacement(pathPlacement), bundleInputs);
    }

    private record Requires(String group) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return false;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties requires = new SequencedProperties();
            requires.setProperty(group + "/runtime/maven/build.jenesis/build.jenesis.launcher/RELEASE", "");
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

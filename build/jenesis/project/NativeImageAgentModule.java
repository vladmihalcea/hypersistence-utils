package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.step.NativeImage;

public class NativeImageAgentModule implements BuildExecutorModule {

    public static final String REPORT = "report";

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(REPORT, new Report(), inherited.sequencedKeySet());
    }

    private record Report() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path captured = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path candidate = argument.folder().resolve(NativeImageAgent.NATIVE_IMAGE);
                if (Files.isDirectory(candidate)) {
                    captured = candidate;
                    break;
                }
            }
            if (captured != null) {
                Path source = captured;
                Path metadata = Files.createDirectories(context.next().resolve(NativeImage.METADATA));
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Path destination = metadata.resolve(source.relativize(file).toString());
                        Files.createDirectories(destination.getParent());
                        BuildStep.linkOrCopy(destination, file);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

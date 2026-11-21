package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;

public class ProjectFiles implements BuildStep {

    public static final String PROJECT = "project/";

    private final String folder;

    public ProjectFiles() {
        this("");
    }

    private ProjectFiles(String folder) {
        this.folder = folder;
    }

    public ProjectFiles folder(String folder) {
        return new ProjectFiles(folder);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Path target = context.next().resolve(folder);
        for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
            if (argument.getValue().removed()) {
                continue;
            }
            Path source = argument.getValue().folder().resolve(PROJECT);
            if (!Files.isDirectory(source)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(source)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    String relative = source.relativize(file).toString().replace(File.separatorChar, '/');
                    Path placed = target.resolve(relative);
                    if (Files.exists(placed)) {
                        throw new IllegalStateException(argument.getKey() + " places " + relative + " in the project,"
                                + " where another plugin placed it already - give each file a path of its own");
                    }
                    Files.createDirectories(placed.getParent());
                    BuildStep.linkOrCopy(placed, file);
                }
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

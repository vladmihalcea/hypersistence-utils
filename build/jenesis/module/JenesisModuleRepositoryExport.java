package build.jenesis.module;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;

public class JenesisModuleRepositoryExport implements BuildStep {

    private final Path target;

    public JenesisModuleRepositoryExport() {
        this(Path.of(System.getProperty("user.home")).resolve(".jenesis"));
    }

    public static JenesisModuleRepositoryExport ofEnvironment(Environment environment) {
        String override = environment.getProperty("module.local", System.getenv("JENESIS_REPOSITORY_LOCAL"));
        return override == null
                ? new JenesisModuleRepositoryExport()
                : new JenesisModuleRepositoryExport(Path.of(override));
    }

    public JenesisModuleRepositoryExport(Path target) {
        this.target = target;
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return true;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Set<Path> cleaned = new HashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path folder = argument.folder();
            if (!Files.isDirectory(folder)) {
                continue;
            }
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Path relative = folder.relativize(file);
                    link(file, target.resolve(relative.toString()), cleaned);
                    if (relative.getNameCount() == 3) {
                        link(file,
                                target.resolve(relative.getName(0).toString())
                                        .resolve(relative.getName(2).toString()),
                                cleaned);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void link(Path file, Path destination, Set<Path> cleaned) throws IOException {
        Path parent = destination.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
            if (cleaned.add(parent)) {
                try (DirectoryStream<Path> existing = Files.newDirectoryStream(parent)) {
                    for (Path child : existing) {
                        if (Files.isRegularFile(child)) {
                            Files.delete(child);
                        }
                    }
                }
            }
        }
        Files.deleteIfExists(destination);
        BuildStep.linkOrCopy(destination, file);
    }
}

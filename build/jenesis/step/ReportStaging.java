package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class ReportStaging implements BuildStep {

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String prefix : Inventory.prefixes(inventory)) {
                String module = prefix.startsWith("module-") ? prefix.substring("module-".length()) : prefix;
                String marker = prefix + ".report.";
                for (String key : inventory.stringPropertyNames()) {
                    if (!key.startsWith(marker)) {
                        continue;
                    }
                    String kind = key.substring(marker.length());
                    Path source = argument.folder().resolve(inventory.getProperty(key)).normalize();
                    Path target = context.next().resolve(kind).resolve(module);
                    if (Files.isDirectory(source)) {
                        copyTree(source, target);
                    } else if (Files.isRegularFile(source)) {
                        Files.createDirectories(target);
                        Path destination = target.resolve(source.getFileName().toString());
                        if (!Files.exists(destination)) {
                            BuildStep.linkOrCopy(destination, source);
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void copyTree(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path destination = target.resolve(source.relativize(file).toString());
                Files.createDirectories(destination.getParent());
                if (!Files.exists(destination)) {
                    BuildStep.linkOrCopy(destination, file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

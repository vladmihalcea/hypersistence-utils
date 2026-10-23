package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Inventory;

public record NativeImageMetadata() implements BuildStep {

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String testedPrefix = BuildExecutorModule.PREVIOUS + MultiProjectModule.TESTED + "/";
        String artifact = null;
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            if (entry.getValue().removed()) {
                continue;
            }
            if (entry.getKey().startsWith(testedPrefix)) {
                continue;
            }
            Path inventoryFile = entry.getValue().folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String key : inventory.stringPropertyNames()) {
                if (key.endsWith(".artifact")) {
                    artifact = inventory.getProperty(key);
                    break;
                }
            }
            if (artifact != null) {
                break;
            }
        }
        if (artifact == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        String suffix = ".nativeimage";
        Path target = null;
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            if (entry.getValue().removed()) {
                continue;
            }
            if (!entry.getKey().startsWith(testedPrefix)) {
                continue;
            }
            Path inventoryFile = entry.getValue().folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String key : inventory.stringPropertyNames()) {
                if (!key.endsWith(suffix)) {
                    continue;
                }
                if (!artifact.equals(inventory.getProperty(key.substring(0, key.length() - suffix.length()) + ".test"))) {
                    continue;
                }
                Path captured = entry.getValue().folder().resolve(inventory.getProperty(key)).normalize();
                if (!Files.isDirectory(captured)) {
                    continue;
                }
                if (target == null) {
                    target = Files.createDirectories(context.next().resolve(NativeImageAgent.NATIVE_IMAGE));
                }
                Path source = captured, destination = target;
                Files.walkFileTree(source, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Path to = destination.resolve(source.relativize(file).toString());
                        Files.createDirectories(to.getParent());
                        if (!Files.exists(to)) {
                            BuildStep.linkOrCopy(to, file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

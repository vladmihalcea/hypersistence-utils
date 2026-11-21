package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;

public class ImageStaging implements BuildStep {

    private final String key;
    private final boolean noFolder;

    public ImageStaging(String key) {
        this(key, false);
    }

    private ImageStaging(String key, boolean noFolder) {
        this.key = key;
        this.noFolder = noFolder;
    }

    public ImageStaging noFolder(boolean noFolder) {
        return new ImageStaging(key, noFolder);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String suffix = "." + key;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(Inventory.INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String entry : inventory.stringPropertyNames()) {
                if (!entry.endsWith(suffix)) {
                    continue;
                }
                Path image = argument.folder().resolve(inventory.getProperty(entry)).normalize();
                if (!Files.isDirectory(image)) {
                    continue;
                }
                String prefix = entry.substring(0, entry.length() - suffix.length());
                Path target = noFolder ? context.next() : context.next().resolve(prefix);
                Files.createDirectories(target);
                Files.walkFileTree(image, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                        Files.createDirectories(target.resolve(image.relativize(directory).toString()));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Path destination = target.resolve(image.relativize(file).toString());
                        if (!Files.exists(destination)) {
                            BuildStep.linkOrCopy(destination, file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

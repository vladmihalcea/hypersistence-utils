package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Versions;

public record ClassTransformModule(SequencedMap<String, BuildExecutorModule> transforms) implements BuildExecutorModule {

    public static final String PLUGIN = "plugin", CLASSES = "classes";

    public ClassTransformModule {
        if (transforms.isEmpty()) {
            throw new IllegalArgumentException("A class transformation needs at least one transform - name one, or"
                    + " leave the transformer of the toolchain unset");
        }
        transforms = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(transforms));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        String previous = inherited.firstEntry().getKey();
        List<String> inputs = inherited.sequencedKeySet().stream().skip(1).toList();
        for (Map.Entry<String, BuildExecutorModule> entry : transforms.entrySet()) {
            BuildExecutorModule plugin = entry.getValue();
            buildExecutor.addModule(entry.getKey(), (nested, handed) -> {
                nested.addModule(PLUGIN, plugin, handed.sequencedKeySet());
                nested.addStep(CLASSES, new Overlay(), handed.firstEntry().getKey(), PLUGIN);
            }, Stream.concat(Stream.of(previous), inputs.stream()));
            previous = entry.getKey() + "/" + CLASSES;
        }
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(transforms.lastEntry().getKey() + "/" + CLASSES) ? Optional.of("") : Optional.empty();
    }

    private record Overlay() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path target = context.next();
            Files.createDirectory(target.resolve(BuildStep.CLASSES));
            Map<Path, String> writers = new HashMap<>();
            boolean handed = true;
            for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
                BuildStepArgument argument = entry.getValue();
                if (argument.removed()) {
                    continue;
                }
                boolean transformed = !handed;
                handed = false;
                Path folder = argument.folder();
                Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Files.createDirectories(target.resolve(folder.relativize(dir)));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        Path relative = folder.relativize(file), destination = target.resolve(relative);
                        if (relative.startsWith(BuildStep.CLASSES) || relative.equals(Path.of(Versions.MANIFEST))) {
                            if (transformed) {
                                String previous = writers.putIfAbsent(relative, entry.getKey());
                                if (previous != null) {
                                    throw new IllegalStateException(relative.toString().replace(File.separatorChar, '/')
                                            + " is written by both " + previous + " and " + entry.getKey()
                                            + " - a transform writes each file once, so let one of its steps write it");
                                }
                                Files.deleteIfExists(destination);
                            }
                            BuildStep.linkOrCopy(destination, file);
                        } else if (!Files.exists(destination)) {
                            BuildStep.linkOrCopy(destination, file);
                        } else if (relative.getFileName().toString().endsWith(".properties")) {
                            SequencedProperties merged = SequencedProperties.ofFiles(destination);
                            SequencedProperties.ofFiles(file).forEachProperty(merged::putIfAbsent);
                            Files.delete(destination);
                            merged.store(destination);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }
}

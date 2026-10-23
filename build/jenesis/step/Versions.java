package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.SequencedProperties;
import java.util.jar.Attributes;

public class Versions implements BuildStep {

    public static final String MANIFEST = "manifest.mf";

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(DEPENDENCIES),
                Path.of(CLASSES),
                Path.of(MANIFEST)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path requires = argument.folder().resolve(DEPENDENCIES);
            if (!Files.exists(requires)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(requires);
            for (String property : properties.stringPropertyNames()) {
                int firstSlash = property.indexOf('/');
                if (firstSlash < 0) {
                    continue;
                }
                String rest = property.substring(firstSlash + 1);
                int secondSlash = rest.indexOf('/');
                if (secondSlash < 0 || rest.indexOf('/', secondSlash + 1) >= 0) {
                    continue;
                }
                versions.putIfAbsent(rest.substring(0, secondSlash), rest.substring(secondSlash + 1));
            }
        }
        Path target = Files.createDirectory(context.next().resolve(CLASSES));
        List<Path> manifests = new ArrayList<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path manifest = argument.folder().resolve(MANIFEST);
            if (Files.exists(manifest)) {
                manifests.add(manifest);
            }
        }
        if (!manifests.isEmpty()) {
            Manifest merged = new Manifest();
            for (Path manifest : manifests) {
                Manifest current;
                try (InputStream in = Files.newInputStream(manifest)) {
                    current = new Manifest(in);
                }
                mergeAttributes(merged.getMainAttributes(), current.getMainAttributes(), manifest);
                for (Map.Entry<String, Attributes> entry : current.getEntries().entrySet()) {
                    Attributes attributes = merged.getEntries()
                            .computeIfAbsent(entry.getKey(), _ -> new Attributes());
                    mergeAttributes(attributes, entry.getValue(), manifest);
                }
            }
            try (OutputStream out = Files.newOutputStream(context.next().resolve(MANIFEST))) {
                merged.write(out);
            }
        }
        boolean dependenciesChanged = arguments.values().stream().anyMatch(arg -> {
            Checksum status = arg.files().get(Path.of(DEPENDENCIES));
            return status != null && status.status() != ChecksumStatus.RETAINED;
        });
        Map<Path, String> writers = new HashMap<>();
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            BuildStepArgument argument = entry.getValue();
            if (argument.removed()) {
                continue;
            }
            Path source = argument.folder().resolve(CLASSES);
            if (!Files.exists(source)) {
                continue;
            }
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    Files.createDirectories(target.resolve(source.relativize(dir)));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path destination = target.resolve(source.relativize(file));
                    String writer = writers.putIfAbsent(destination, entry.getKey());
                    if (writer != null) {
                        throw new IllegalStateException(CLASSES + source.relativize(file).toString()
                                .replace(File.separatorChar, '/') + " is written by both " + writer + " and "
                                + entry.getKey() + " - a step beside the compiler adds classes and never replaces"
                                + " one, so give what it writes a name no other step uses");
                    }
                    if (file.getFileName().toString().equals("module-info.class")) {
                        if (!dependenciesChanged && context.previous() != null) {
                            Path argumentRelative = argument.folder().relativize(file);
                            Checksum status = argument.files().get(argumentRelative);
                            if (status != null && status.status() == ChecksumStatus.RETAINED) {
                                Path priorOutput = context.previous().resolve(CLASSES).resolve(source.relativize(file));
                                if (Files.exists(priorOutput)) {
                                    BuildStep.linkOrCopy(destination, priorOutput);
                                    return FileVisitResult.CONTINUE;
                                }
                            }
                        }
                        ClassFile classFile = ClassFile.of();
                        ClassModel model = classFile.parse(Files.readAllBytes(file));
                        Files.write(destination, classFile.transformClass(model, (classBuilder, element) -> {
                            if (element instanceof ModuleAttribute moduleAttribute) {
                                classBuilder.with(ModuleAttribute.of(moduleAttribute.moduleName(), builder -> {
                                    builder.moduleFlags(moduleAttribute.moduleFlagsMask());
                                    moduleAttribute.moduleVersion().ifPresent(
                                            version -> builder.moduleVersion(version.stringValue()));
                                    for (ModuleRequireInfo require : moduleAttribute.requires()) {
                                        String name = require.requires().name().stringValue();
                                        String version = versions.get(name);
                                        if (version != null) {
                                            builder.requires(ModuleDesc.of(name), require.requiresFlagsMask(), version);
                                        } else {
                                            builder.requires(require);
                                        }
                                    }
                                    moduleAttribute.exports().forEach(builder::exports);
                                    moduleAttribute.opens().forEach(builder::opens);
                                    moduleAttribute.uses().forEach(builder::uses);
                                    moduleAttribute.provides().forEach(builder::provides);
                                }));
                            } else {
                                classBuilder.with(element);
                            }
                        }));
                    } else {
                        BuildStep.linkOrCopy(destination, file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static void mergeAttributes(Attributes target, Attributes source, Path file) {
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            Object key = entry.getKey(), value = entry.getValue(), existing = target.get(key);
            if (existing == null) {
                target.put(key, value);
            } else if (!existing.equals(value)) {
                throw new IllegalStateException("Conflicting manifest attribute '"
                        + key + "' in " + file + ": '" + existing + "' vs '" + value + "'");
            }
        }
    }
}

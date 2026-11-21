package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildStep extends Serializable {

    String SOURCES = "sources/",
            RESOURCES = "resources/",
            CLASSES = "classes/",
            ARTIFACTS = "artifacts/",
            DOCUMENTATION = "documentation/",
            REPORTS = "reports/";

    String IDENTITY = "identity.properties",
            REQUIRES = "requires.properties",
            VERSIONS = "versions.properties",
            ALIASES = "aliases.properties",
            BOMS = "boms.properties",
            SIGNATURES = "signatures.properties",
            MODULE = "module.properties",
            METADATA = "metadata.properties",
            EXCLUSIONS = "exclusions.properties",
            MANAGED = "managed.properties",
            OPTIONALS = "optionals.properties",
            OVERRIDES = "overrides.properties",
            ATTACHMENTS = "attachments.properties",
            NATIVES = "natives.properties",
            LAYERS = "layers.properties",
            DEPENDENCIES = "dependencies.properties",
            RELEASE = "release";

    default BuildExecutorModule asModule(String name) {
        return new BuildExecutorModule() {
            @Override
            public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
                buildExecutor.addStep(name, BuildStep.this, inherited.sequencedKeySet());
            }

            @Override
            public Optional<String> resolve(String path) {
                return path.equals(name) ? Optional.of("") : Optional.empty();
            }
        };
    }

    default boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        return arguments.values().stream().anyMatch(BuildStepArgument::hasChanged);
    }

    default boolean shouldCacheRemotely() {
        return true;
    }

    CompletionStage<BuildStepResult> apply(Executor executor,
                                           BuildStepContext context,
                                           SequencedMap<String, BuildStepArgument> arguments) throws IOException;

    static void linkOrCopy(Path link, Path existing) throws IOException {
        try {
            Files.createLink(link, existing);
        } catch (UnsupportedOperationException | FileSystemException _) {
            Files.copy(existing, link);
        }
    }

    static Path resolveContained(Path base, String relative) throws IOException {
        Path resolved = base.resolve(relative).normalize();
        if (!resolved.startsWith(base.normalize())) {
            throw new IOException("Resolved path escapes " + base + ": " + relative);
        }
        return resolved;
    }

    static boolean underMetaInfVersions(Path path) {
        return path.getNameCount() >= 2
                && path.getName(0).toString().equals("META-INF")
                && path.getName(1).toString().equals("versions");
    }

    static boolean underBuildJenesis(Path path) {
        return path.getNameCount() >= 2
                && path.getName(0).toString().equals("META-INF")
                && path.getName(1).toString().equals("build.jenesis");
    }

    static Path locate(SequencedSet<Path> folders, String fileName) {
        for (Path folder : folders) {
            Path candidate = folder.resolve(fileName);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    static List<String> selectByExtension(List<Path> folders, String extension) throws IOException {
        List<String> selected = new ArrayList<>();
        for (Path folder : folders) {
            Files.walkFileTree(folder, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (file.getFileName().toString().endsWith(extension)) {
                        selected.add(file.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        selected.sort(null);
        return selected;
    }

    static Path selectByName(List<Path> folders, String name) throws IOException {
        for (Path folder : folders) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, name + ".*")) {
                for (Path candidate : stream) {
                    if (Files.isRegularFile(candidate)) {
                        return candidate;
                    }
                }
            }
        }
        return null;
    }

    static SequencedMap<String, String> listing(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, String> listing = new TreeMap<>();
        for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
            Path folder = argument.getValue().folder();
            if (argument.getValue().removed() || !Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.walk(folder)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    listing.put(argument.getKey() + "/" + folder.relativize(file).toString().replace(File.separatorChar, '/'),
                            Files.size(file) + " " + Files.getLastModifiedTime(file).toMillis());
                }
            }
        }
        return listing;
    }

    static OffsetDateTime timestamp() {
        return timestamp(Environment.NONE);
    }

    static OffsetDateTime timestamp(Environment environment) {
        String value = environment.getProperty("archive.timestamp", "1980-02-01T00:00:00Z");
        if (value.isBlank()) {
            return null;
        }
        OffsetDateTime timestamp;
        try {
            timestamp = ZonedDateTime.parse(value, DateTimeFormatter.ISO_ZONED_DATE_TIME)
                    .withZoneSameInstant(ZoneOffset.UTC)
                    .toOffsetDateTime();
        } catch (DateTimeParseException _) {
            throw new IllegalArgumentException("jenesis.archive.timestamp is not an ISO-8601 date-time with an offset,"
                    + " such as 2026-01-01T00:00:00Z: " + value);
        }
        if (timestamp.isBefore(OffsetDateTime.parse("1980-01-01T00:00:02Z"))
                || timestamp.isAfter(OffsetDateTime.parse("2099-12-31T23:59:59Z"))) {
            throw new IllegalArgumentException("jenesis.archive.timestamp must lie between 1980-01-01T00:00:02Z and"
                    + " 2099-12-31T23:59:59Z, the range an archive entry records without a time zone: " + value);
        }
        return timestamp;
    }
}

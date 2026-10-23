package build.jenesis;

import module java.base;

public record BuildExecutorFileCache(Path root,
                                     String algorithm,
                                     boolean links,
                                     int steps,
                                     int versions,
                                     long size,
                                     Duration ttl,
                                     boolean touch,
                                     boolean lru,
                                     boolean compressed,
                                     boolean read,
                                     boolean write) implements BuildExecutorCache {

    public BuildExecutorFileCache(Path root) {
        Path file = root.resolve("cache.properties");
        SequencedProperties properties = new SequencedProperties();
        if (Files.isRegularFile(file)) {
            try {
                properties = SequencedProperties.ofFiles(file);
            } catch (IOException _) {
            }
        }
        String steps = properties.value("steps"),
                versions = properties.value("versions"),
                size = properties.value("size"),
                ttl = properties.value("ttl");
        this(root,
                properties.value("digest", "SHA-256"),
                true,
                steps == null ? 250 : Integer.parseInt(steps),
                versions == null ? 10 : Integer.parseInt(versions),
                size == null ? 0 : Long.parseLong(size),
                ttl == null ? null : Duration.parse(ttl),
                properties.flag("touch", true),
                properties.flag("lru", true),
                properties.flag("compressed"),
                properties.flag("read", true),
                properties.flag("write", true));
    }

    @Override
    public Optional<BuildStepResult> fetch(Executor executor,
                                           String identity,
                                           byte[] step,
                                           SequencedMap<String, Map<Path, byte[]>> inputs,
                                           boolean remote,
                                           Path target) throws IOException {
        if (!read) {
            return Optional.empty();
        }
        Path folder = root.resolve(HexFormat.of().formatHex(step));
        Path entry = folder.resolve(HexFormat.of().formatHex(fold(inputs)));
        try {
            if (Files.isDirectory(entry)) {
                materialize(entry, target, links);
            } else if (Files.isRegularFile(entry)) {
                unzip(entry, target);
            } else {
                return Optional.empty();
            }
        } catch (IOException _) {
            clean(target);
            return Optional.empty();
        }
        if (touch && write) {
            touch(entry);
            touch(folder);
        }
        return Optional.of(new BuildStepResult(true));
    }

    @Override
    public void store(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      boolean remote,
                      Path output,
                      String digest,
                      Map<Path, byte[]> checksums) throws IOException {
        if (write) {
            persist(step, inputs, output);
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                executor.execute(() -> {
                    try {
                        evictByAge(ttl);
                    } catch (IOException _) {
                    }
                });
            }
        }
    }

    @Override
    public boolean stores() {
        return write;
    }

    @Override
    public void touch(Executor executor,
                      String identity,
                      byte[] step,
                      SequencedMap<String, Map<Path, byte[]>> inputs,
                      boolean remote) {
        if (!touch || !write) {
            return;
        }
        Path folder = root.resolve(HexFormat.of().formatHex(step));
        Path entry = folder.resolve(HexFormat.of().formatHex(fold(inputs)));
        if (Files.exists(entry)) {
            touch(entry);
            touch(folder);
        }
    }

    private void persist(byte[] step, SequencedMap<String, Map<Path, byte[]>> inputs, Path output) {
        Path folder = root.resolve(HexFormat.of().formatHex(step));
        Path entry = folder.resolve(HexFormat.of().formatHex(fold(inputs)));
        if (Files.exists(entry)) {
            return;
        }
        Path temporary = null;
        try {
            Files.createDirectories(folder);
            try {
                Files.createFile(root.resolve(BuildExecutor.SKIP_MARKER));
            } catch (FileAlreadyExistsException _) {
            }
            temporary = compressed
                    ? Files.createTempFile(folder, "tmp", null)
                    : Files.createTempDirectory(folder, "tmp");
            if (compressed) {
                zip(output, temporary);
            } else {
                materialize(output, temporary, false);
            }
            try {
                Files.move(temporary, entry, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(temporary, entry);
            }
            temporary = null;
            evict(folder, versions);
            evict(root, steps);
            evictBySize(size);
        } catch (IOException | RuntimeException _) {
        } finally {
            if (temporary != null) {
                try {
                    delete(temporary);
                } catch (IOException _) {
                }
            }
        }
    }

    private byte[] fold(SequencedMap<String, Map<Path, byte[]>> inputs) {
        MessageDigest message;
        try {
            message = MessageDigest.getInstance(algorithm);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        inputs.forEach((argument, files) -> {
            message.update(argument.getBytes(StandardCharsets.UTF_8));
            message.update((byte) 0);
            List<Map.Entry<Path, byte[]>> ordered = new ArrayList<>(files.entrySet());
            ordered.sort(Comparator.comparing(entry -> entry.getKey().toString().replace('\\', '/')));
            for (Map.Entry<Path, byte[]> entry : ordered) {
                message.update(entry.getKey().toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                message.update((byte) 0);
                message.update(entry.getValue());
            }
        });
        return message.digest();
    }

    private void evict(Path parent, int limit) throws IOException {
        if (limit <= 0) {
            return;
        }
        List<Path> entries = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(parent)) {
            for (Path path : stream) {
                if (isHex(path.getFileName().toString())) {
                    entries.add(path);
                }
            }
        }
        int excess = entries.size() - limit;
        if (excess <= 0) {
            return;
        }
        entries.sort(Comparator.comparing(BuildExecutorFileCache::lastModified));
        for (int index = 0; index < excess; index++) {
            try {
                delete(lru ? entries.get(index) : entries.get(entries.size() - 1 - index));
            } catch (IOException _) {
            }
        }
    }

    private void evictBySize(long limit) throws IOException {
        if (limit <= 0) {
            return;
        }
        Map<Path, Long> sizes = new LinkedHashMap<>();
        long total = 0;
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(root)) {
            for (Path folder : folders) {
                if (!isHex(folder.getFileName().toString()) || !Files.isDirectory(folder)) {
                    continue;
                }
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
                    for (Path entry : entries) {
                        if (!isHex(entry.getFileName().toString())) {
                            continue;
                        }
                        long bytes = sizeOf(entry);
                        sizes.put(entry, bytes);
                        total += bytes;
                    }
                }
            }
        }
        if (total <= limit) {
            return;
        }
        List<Path> entries = new ArrayList<>(sizes.keySet());
        entries.sort(Comparator.comparing(BuildExecutorFileCache::lastModified));
        for (int index = 0; index < entries.size() && total > limit; index++) {
            Path entry = lru ? entries.get(index) : entries.get(entries.size() - 1 - index);
            try {
                delete(entry);
                total -= sizes.get(entry);
                Path folder = entry.getParent();
                try (DirectoryStream<Path> remaining = Files.newDirectoryStream(folder)) {
                    if (!remaining.iterator().hasNext()) {
                        Files.deleteIfExists(folder);
                    }
                }
            } catch (IOException _) {
            }
        }
    }

    private void evictByAge(Duration ttl) throws IOException {
        Instant threshold = Instant.now().minus(ttl);
        List<Path> expired = new ArrayList<>();
        try (DirectoryStream<Path> folders = Files.newDirectoryStream(root)) {
            for (Path folder : folders) {
                if (!isHex(folder.getFileName().toString()) || !Files.isDirectory(folder)) {
                    continue;
                }
                try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder)) {
                    for (Path entry : entries) {
                        if (isHex(entry.getFileName().toString())
                                && lastModified(entry).toInstant().isBefore(threshold)) {
                            expired.add(entry);
                        }
                    }
                }
            }
        }
        for (Path entry : expired) {
            try {
                delete(entry);
                Path folder = entry.getParent();
                try (DirectoryStream<Path> remaining = Files.newDirectoryStream(folder)) {
                    if (!remaining.iterator().hasNext()) {
                        Files.deleteIfExists(folder);
                    }
                }
            } catch (IOException _) {
            }
        }
    }

    private static long sizeOf(Path path) {
        if (Files.isRegularFile(path)) {
            try {
                return Files.size(path);
            } catch (IOException _) {
                return 0;
            }
        }
        long[] total = {0};
        try {
            Files.walkFileTree(path, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    total[0] += attributes.size();
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException _) {
        }
        return total[0];
    }

    private static void touch(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException _) {
        }
    }

    private static FileTime lastModified(Path path) {
        try {
            return Files.getLastModifiedTime(path);
        } catch (IOException _) {
            return FileTime.fromMillis(0);
        }
    }

    private static boolean isHex(String name) {
        if (name.isEmpty()) {
            return false;
        }
        for (int index = 0; index < name.length(); index++) {
            if (Character.digit(name.charAt(index), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static void materialize(Path source, Path target, boolean links) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                Files.createDirectories(target.resolve(source.relativize(directory)));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Path destination = target.resolve(source.relativize(file));
                if (links) {
                    try {
                        Files.createLink(destination, file);
                        return FileVisitResult.CONTINUE;
                    } catch (UnsupportedOperationException | FileSystemException _) {
                    }
                }
                Files.copy(file, destination, StandardCopyOption.REPLACE_EXISTING);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void zip(Path source, Path target) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(target))) {
            Files.walkFileTree(source, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    zip.putNextEntry(new ZipEntry(source.relativize(file).toString().replace('\\', '/')));
                    Files.copy(file, zip);
                    zip.closeEntry();
                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private static void unzip(Path source, Path target) throws IOException {
        Path base = target.normalize();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(source))) {
            for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path destination = BuildStep.resolveContained(base, entry.getName());
                Files.createDirectories(destination.getParent());
                Files.copy(zip, destination, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private static void clean(Path target) {
        try (DirectoryStream<Path> children = Files.newDirectoryStream(target)) {
            for (Path child : children) {
                delete(child);
            }
        } catch (IOException _) {
        }
    }

    private static void delete(Path path) throws IOException {
        if (Files.notExists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                Files.deleteIfExists(directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }
}

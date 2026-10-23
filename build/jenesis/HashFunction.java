package build.jenesis;

import module java.base;

@FunctionalInterface
public interface HashFunction {

    byte[] hash(Path file) throws IOException;

    static Map<Path, byte[]> read(Path file) throws IOException {
        Map<Path, byte[]> checksums = new LinkedHashMap<>();
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String name : properties.stringPropertyNames()) {
            checksums.put(Path.of(name), HexFormat.of().parseHex(properties.getProperty(name)));
        }
        return checksums;
    }

    static Map<Path, byte[]> read(Path folder, HashFunction hash, Executor executor) throws IOException {
        Map<Path, byte[]> checksums = new LinkedHashMap<>();
        if (!Files.exists(folder)) {
            return checksums;
        }
        List<Path> files = files(folder);
        Map<Path, byte[]> hashes = hashAll(files, hash, executor);
        for (Path file : files) {
            checksums.put(folder.relativize(file), hashes.get(file));
        }
        return checksums;
    }

    private static List<Path> files(Path folder) throws IOException {
        List<Path> files = new ArrayList<>();
        Queue<Path> queue = new ArrayDeque<>(List.of(folder));
        do {
            Path current = queue.remove();
            if (Files.isDirectory(current)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(current)) {
                    stream.forEach(queue::add);
                }
            } else if (Files.isSymbolicLink(current) && Files.notExists(current)) {
                throw new NoSuchFileException(current.toString(),
                        Files.readSymbolicLink(current).toString(),
                        "a symbolic link to a file that does not exist; link one that does, or copy the file in its place");
            } else {
                files.add(current);
            }
        } while (!queue.isEmpty());
        return files;
    }

    private static Map<Path, byte[]> hashAll(List<Path> files, HashFunction hash, Executor executor) throws IOException {
        if (files.size() < 2) {
            Map<Path, byte[]> hashes = new HashMap<>();
            for (Path file : files) {
                hashes.put(file, hash.hash(file));
            }
            return hashes;
        }
        Map<Path, byte[]> hashes = new ConcurrentHashMap<>();
        List<CompletableFuture<?>> futures = new ArrayList<>(files.size());
        for (Path file : files) {
            CompletableFuture<?> future = new CompletableFuture<>();
            executor.execute(() -> {
                try {
                    hashes.put(file, hash.hash(file));
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
            });
            futures.add(future);
        }
        try {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException e) {
            if (e.getCause() instanceof IOException exception) {
                throw exception;
            }
            throw e;
        }
        return hashes;
    }

    static void write(Path file, Map<Path, byte[]> checksums) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        checksums.entrySet().stream()
                .map(entry -> Map.entry(
                        entry.getKey().toString().replace(File.separatorChar, '/'),
                        HexFormat.of().formatHex(entry.getValue())))
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> properties.setProperty(entry.getKey(), entry.getValue()));
        properties.storeAtomically(file);
    }

    static boolean areConsistent(Path folder, Map<Path, byte[]> checksums, HashFunction hash, Executor executor)
            throws IOException {
        Map<Path, byte[]> remaining = new HashMap<>(checksums);
        List<Path> files = files(folder);
        for (Path file : files) {
            if (!remaining.containsKey(folder.relativize(file))) {
                return false;
            }
        }
        Map<Path, byte[]> hashes = hashAll(files, hash, executor);
        for (Path file : files) {
            if (!Arrays.equals(remaining.remove(folder.relativize(file)), hashes.get(file))) {
                return false;
            }
        }
        return remaining.isEmpty();
    }
}

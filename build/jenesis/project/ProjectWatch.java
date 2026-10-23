package build.jenesis.project;

import module java.base;

public final class ProjectWatch {

    private final Path root;
    private final Set<Path> excluded;
    private final long debounceMillis;
    private final Consumer<String> out;

    public ProjectWatch(Path root, Set<Path> excluded, long debounceMillis, Consumer<String> out) {
        this.root = root;
        this.excluded = excluded;
        this.debounceMillis = debounceMillis;
        this.out = out;
    }

    public void watch(Runnable build) throws IOException {
        try (WatchService service = root.getFileSystem().newWatchService()) {
            Map<WatchKey, Path> keys = new HashMap<>();
            register(service, root, keys);
            build.run();
            out.accept("Watching " + root + " for changes (press Ctrl+C to stop).");
            while (!Thread.interrupted()) {
                WatchKey key = service.take();
                boolean rebuild = false;
                while (key != null) {
                    Path directory = keys.get(key);
                    for (WatchEvent<?> event : key.pollEvents()) {
                        if (event.kind() == StandardWatchEventKinds.OVERFLOW) {
                            rebuild = true;
                            continue;
                        }
                        Path base = directory != null ? directory : (Path) key.watchable();
                        Path changed = base.resolve((Path) event.context());
                        if (isExcluded(changed)) {
                            continue;
                        }
                        rebuild = true;
                        if (event.kind() == StandardWatchEventKinds.ENTRY_CREATE && Files.isDirectory(changed)) {
                            register(service, changed, keys);
                        } else if (event.kind() == StandardWatchEventKinds.ENTRY_DELETE) {
                            keys.entrySet().removeIf(entry -> {
                                if (entry.getValue().equals(changed)) {
                                    entry.getKey().cancel();
                                    return true;
                                }
                                return false;
                            });
                        }
                    }
                    if (!key.reset()) {
                        keys.remove(key);
                    }
                    key = service.poll(debounceMillis, TimeUnit.MILLISECONDS);
                }
                if (rebuild) {
                    out.accept("Change detected, rebuilding.");
                    build.run();
                }
            }
        } catch (InterruptedException _) {
        }
    }

    private void register(WatchService service, Path start, Map<WatchKey, Path> keys) throws IOException {
        Files.walkFileTree(start, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (isExcluded(directory)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                keys.put(directory.register(service,
                        StandardWatchEventKinds.ENTRY_CREATE,
                        StandardWatchEventKinds.ENTRY_MODIFY,
                        StandardWatchEventKinds.ENTRY_DELETE), directory);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private boolean isExcluded(Path path) {
        Path absolute = path.toAbsolutePath().normalize();
        for (Path exclude : excluded) {
            if (absolute.startsWith(exclude)) {
                return true;
            }
        }
        if (absolute.startsWith(root) && !absolute.equals(root)) {
            for (Path element : root.relativize(absolute)) {
                if (element.toString().startsWith(".")) {
                    return true;
                }
            }
        }
        return false;
    }
}

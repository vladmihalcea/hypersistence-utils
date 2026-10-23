package build.jenesis;

import module java.base;

@FunctionalInterface
public interface Resolver extends Serializable {

    record Resolved(Path file, String checksum, boolean internal) implements Serializable {
    }

    record Bom(Path file,
               boolean internal,
               boolean verifiable,
               String version,
               SequencedMap<String, String> entries) implements Serializable {
    }

    record Resolution(SequencedMap<String, Resolved> artifacts,
                      List<Edge> edges,
                      SequencedMap<String, Vertex> vertices) {
    }

    record Edge(String parent, String coordinate, String version, String scope, boolean followed) {
    }

    record Vertex(String resolvedVersion, String module, boolean automatic, boolean internal, List<License> licenses) {
    }

    Resolution dependencies(Executor executor,
                            String prefix,
                            Map<String, Repository> repositories,
                            SequencedMap<String, SequencedSet<String>> coordinates,
                            SequencedMap<String, String> versions,
                            DependencyScope scope) throws IOException;

    default Resolution dependencies(Executor executor,
                                    String prefix,
                                    Map<String, Repository> repositories,
                                    SequencedMap<String, SequencedSet<String>> coordinates,
                                    SequencedMap<String, String> versions,
                                    SequencedMap<String, SequencedSet<String>> managedExclusions,
                                    DependencyScope scope) throws IOException {
        return dependencies(executor, prefix, repositories, coordinates, versions, scope);
    }

    default SequencedSet<String> managedPrefixes() {
        return Collections.emptyNavigableSet();
    }

    default Bom bom(Executor executor,
                    String prefix,
                    Map<String, Repository> repositories,
                    String coordinate,
                    String version,
                    String checksum,
                    boolean latest) throws IOException {
        Repository repository = repositories.getOrDefault(base(prefix), Repository.empty());
        Resolved resolved = materialize(executor,
                repository,
                version.isEmpty() ? coordinate + ":properties" : coordinate + "/" + version + ":properties",
                checksum);
        return new Bom(resolved.file(),
                resolved.internal(),
                true,
                version,
                bomEntries(SequencedProperties.ofFiles(resolved.file())));
    }

    static SequencedMap<String, String> bomEntries(SequencedProperties properties) {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key).trim();
            if (value.endsWith(")")) {
                throw new IllegalArgumentException("Malformed BOM entry '"
                        + key
                        + "': platform guards are not supported in BOM files,"
                        + " guard the @jenesis.bom declaration instead");
            }
            int firstSlash = key.indexOf('/');
            int secondSlash = firstSlash < 0 ? -1 : key.indexOf('/', firstSlash + 1);
            String expanded;
            if (firstSlash < 0) {
                expanded = "module/" + key;
            } else if (secondSlash < 0) {
                if (firstSlash < 1 || firstSlash == key.length() - 1) {
                    throw new IllegalArgumentException("Malformed BOM entry '"
                            + key
                            + "': expected <module>, <groupId>/<artifactId>,"
                            + " or <repository>/<coordinate>");
                }
                expanded = "maven/" + key;
            } else {
                if (firstSlash < 1 || secondSlash == firstSlash + 1 || secondSlash == key.length() - 1) {
                    throw new IllegalArgumentException("Malformed BOM entry '"
                            + key
                            + "': expected <module>, <groupId>/<artifactId>,"
                            + " or <repository>/<coordinate>");
                }
                expanded = key;
            }
            entries.put(expanded, value);
        }
        return entries;
    }

    static String base(String prefix) {
        int at = prefix.indexOf('@');
        return at < 0 ? prefix : prefix.substring(0, at);
    }

    static Resolved materialize(Executor executor,
                                Repository repository,
                                String coordinate,
                                String checksum) throws IOException {
        RepositoryItem item = repository.fetch(executor, coordinate)
                .orElseThrow(() -> new IllegalStateException("Unresolved: " + coordinate));
        Path file = item.file().orElse(null);
        if (file == null) {
            throw new IllegalStateException("Repository did not materialize a file for " + coordinate);
        }
        if (checksum != null && !checksum.isEmpty()) {
            validate(file, checksum, coordinate);
        }
        return new Resolved(file, checksum == null ? "" : checksum, item.internal());
    }

    static void validate(Path file, String checksum, String coordinate) throws IOException {
        int slash = checksum.indexOf('/');
        if (slash < 0) {
            throw new IllegalStateException("Malformed checksum '" + checksum
                    + "' for " + coordinate + ", expected <algorithm>/<hex>");
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance(checksum.substring(0, slash));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        try (InputStream inputStream = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        byte[] actual = digest.digest();
        if (!Arrays.equals(actual, HexFormat.of().parseHex(checksum.substring(slash + 1)))) {
            throw new IllegalStateException("Mismatched digest for " + coordinate + ": " + file + " is "
                    + checksum.substring(0, slash) + "/" + HexFormat.of().formatHex(actual) + " where the pin names "
                    + checksum + " - pin what the version holds, or delete that copy if the version was"
                    + " republished on purpose");
        }
    }

    static SequencedMap<String, Resolved> materializeAll(Executor executor,
                                                        Map<String, Repository> repositories,
                                                        String prefix,
                                                        SequencedMap<String, String> resolved) throws IOException {
        Repository repository = repositories.getOrDefault(base(prefix), Repository.empty());
        Map<String, Resolved> results = new ConcurrentHashMap<>();
        List<CompletableFuture<?>> futures = new ArrayList<>();
        for (Map.Entry<String, String> entry : resolved.entrySet()) {
            String coordinate = entry.getKey();
            String fetch = coordinate.startsWith(prefix + "/")
                    ? coordinate.substring(prefix.length() + 1)
                    : coordinate;
            String checksum = entry.getValue();
            CompletableFuture<?> future = new CompletableFuture<>();
            executor.execute(() -> {
                try {
                    results.put(coordinate, materialize(executor, repository, fetch, checksum.isEmpty() ? null : checksum));
                    future.complete(null);
                } catch (Throwable t) {
                    future.completeExceptionally(new RuntimeException("Failed to fetch " + coordinate, t));
                }
            });
            futures.add(future);
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        SequencedMap<String, Resolved> materialized = new LinkedHashMap<>();
        for (String coordinate : resolved.sequencedKeySet()) {
            materialized.put(coordinate, results.get(coordinate));
        }
        return materialized;
    }

    static Resolver identity() {
        return (executor, prefix, repositories, coordinates, _, _) -> {
            SequencedMap<String, String> resolved = new LinkedHashMap<>();
            coordinates.sequencedKeySet().forEach(coordinate -> resolved.put(prefix + "/" + coordinate, ""));
            SequencedMap<String, Resolved> artifacts = materializeAll(executor, repositories, prefix, resolved);
            List<Edge> edges = new ArrayList<>();
            SequencedMap<String, Vertex> vertices = new LinkedHashMap<>();
            artifacts.sequencedKeySet().forEach(coordinate -> {
                edges.add(new Edge(null, coordinate, null, null, true));
                vertices.put(coordinate, new Vertex(null, null, false, artifacts.get(coordinate).internal(), List.of()));
            });
            return new Resolution(artifacts, edges, vertices);
        };
    }
}

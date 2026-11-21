package build.jenesis.maven;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;

@FunctionalInterface
public interface MavenRepository extends Repository {

    @Override
    default Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension)
            throws IOException {
        MavenDependencyKey.Versioned parsed = MavenDependencyKey.parse(coordinate);
        return fetch(executor,
                parsed.key().groupId(),
                parsed.key().artifactId(),
                parsed.version(),
                extension == null ? parsed.key().type() : parsed.key().type() == null ? "jar" : parsed.key().type(),
                parsed.key().classifier(),
                extension);
    }

    @Override
    default MavenRepository cached(Path folder) {
        return folder == null ? this : caching(Repository.super.cached(folder), folder);
    }

    @Override
    default MavenRepository materialized(Path folder) {
        return folder == null ? this : caching(Repository.super.materialized(folder), folder);
    }

    private MavenRepository caching(Repository cached, Path folder) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                String coordinate = new MavenDependencyKey(groupId, artifactId, type, classifier)
                        .coordinate(null, version);
                if ("asc".equals(checksum) || "sigstore.json".equals(checksum)) {
                    return cached.fetch(executor, coordinate, checksum);
                }
                if (checksum != null) {
                    return MavenRepository.this.fetch(executor, groupId, artifactId, version, type, classifier, checksum);
                }
                return cached.fetch(executor, coordinate);
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                Path target = folder.resolve(BuildExecutorModule.encode(groupId
                        + "/" + artifactId
                        + "/maven-metadata.xml" + (checksum == null ? "" : "." + checksum)));
                Optional<RepositoryItem> candidate;
                try {
                    candidate = MavenRepository.this.fetchMetadata(executor, groupId, artifactId, checksum);
                } catch (IOException e) {
                    if (!Files.exists(target)) {
                        throw e;
                    }
                    return Optional.of(RepositoryItem.ofFile(target));
                }
                if (candidate.isEmpty()) {
                    return Files.exists(target) ? Optional.of(RepositoryItem.ofFile(target)) : candidate;
                }
                Files.createDirectories(folder);
                Path temporary = Files.createTempFile(folder, "maven-metadata", ".xml");
                try (InputStream inputStream = candidate.orElseThrow().toInputStream()) {
                    Files.copy(inputStream, temporary, StandardCopyOption.REPLACE_EXISTING);
                } catch (Throwable t) {
                    Files.deleteIfExists(temporary);
                    throw t;
                }
                try {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException _) {
                    Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
                }
                return Optional.of(RepositoryItem.ofFile(target));
            }
        };
    }

    @Override
    default MavenRepository prepend(Repository repository) {
        MavenRepository mavenRepository = of(repository);
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                Optional<RepositoryItem> candidate = mavenRepository.fetch(executor,
                        groupId,
                        artifactId,
                        version,
                        type,
                        classifier,
                        checksum);
                return candidate.isPresent()
                        ? candidate
                        : MavenRepository.this.fetch(executor, groupId, artifactId, version, type, classifier, checksum);
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                Optional<RepositoryItem> candidate = mavenRepository.fetchMetadata(executor,
                        groupId,
                        artifactId,
                        checksum);
                return candidate.isPresent()
                        ? candidate
                        : MavenRepository.this.fetchMetadata(executor, groupId, artifactId, checksum);
            }
        };
    }

    @Override
    default MavenRepository observed(Repository.Observer observer) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                Optional<RepositoryItem> candidate = MavenRepository.this.fetch(executor,
                        groupId,
                        artifactId,
                        version,
                        type,
                        classifier,
                        checksum);
                if (candidate.isPresent()) {
                    observer.fetched(new MavenDependencyKey(groupId, artifactId, type, classifier)
                                    .coordinate(null, version),
                            checksum,
                            candidate.get());
                }
                return candidate;
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                return MavenRepository.this.fetchMetadata(executor, groupId, artifactId, checksum);
            }
        };
    }

    default MavenRepository overlay(MavenRepository repository) {
        MavenRepository prepended = prepend(repository);
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                return prepended.fetch(executor, groupId, artifactId, version, type, classifier, checksum);
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                if (checksum != null) {
                    return prepended.fetchMetadata(executor, groupId, artifactId, checksum);
                }
                RepositoryItem overlaid = repository.fetchMetadata(executor, groupId, artifactId, null).orElse(null);
                RepositoryItem underlying = MavenRepository.this.fetchMetadata(executor, groupId, artifactId, null)
                        .orElse(null);
                if (overlaid == null || underlying == null) {
                    return Optional.ofNullable(overlaid == null ? underlying : overlaid);
                }
                return Optional.of(MavenMetadata.of(overlaid).merge(MavenMetadata.of(underlying)).toItem());
            }
        };
    }

    default MavenRepository versions(Predicate<String> admitted) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                return admitted.test(version)
                        ? MavenRepository.this.fetch(executor, groupId, artifactId, version, type, classifier, checksum)
                        : Optional.empty();
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                Optional<RepositoryItem> metadata = MavenRepository.this.fetchMetadata(executor,
                        groupId,
                        artifactId,
                        checksum);
                if (metadata.isEmpty() || checksum != null) {
                    return metadata;
                }
                MavenMetadata filtered = MavenMetadata.of(metadata.get()).filter(admitted);
                return filtered.versions().isEmpty() ? Optional.empty() : Optional.of(filtered.toItem());
            }
        };
    }

    @Override
    default MavenRepository spilled(Path folder) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                Optional<RepositoryItem> candidate = MavenRepository.this.fetch(executor,
                        groupId,
                        artifactId,
                        version,
                        type,
                        classifier,
                        checksum);
                RepositoryItem item = candidate.orElse(null);
                if (item == null || checksum != null || "pom".equals(type) || item.file().isPresent()) {
                    return candidate;
                }
                return Optional.of(item.spill(folder.resolve(artifactId
                        + "-" + version
                        + (classifier == null ? "" : "-" + classifier)
                        + "." + (type == null ? "jar" : type))));
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                return MavenRepository.this.fetchMetadata(executor, groupId, artifactId, checksum);
            }
        };
    }

    default MavenRepository filter(Predicate<String> predicate) {
        return new MavenRepository() {
            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String groupId,
                                                  String artifactId,
                                                  String version,
                                                  String type,
                                                  String classifier,
                                                  String checksum) throws IOException {
                return predicate.test(groupId)
                        ? MavenRepository.this.fetch(executor, groupId, artifactId, version, type, classifier, checksum)
                        : Optional.empty();
            }

            @Override
            public Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                          String groupId,
                                                          String artifactId,
                                                          String checksum) throws IOException {
                return predicate.test(groupId)
                        ? MavenRepository.this.fetchMetadata(executor, groupId, artifactId, checksum)
                        : Optional.empty();
            }
        };
    }

    Optional<RepositoryItem> fetch(Executor executor,
                                   String groupId,
                                   String artifactId,
                                   String version,
                                   String type,
                                   String classifier,
                                   String checksum) throws IOException;


    default Optional<RepositoryItem> fetchMetadata(Executor executor,
                                                   String groupId,
                                                   String artifactId,
                                                   String checksum) throws IOException {
        return Optional.empty();
    }

    static MavenRepository of(Repository repository) {
        return repository instanceof MavenRepository mavenRepository ? mavenRepository : (executor,
                                                                                          groupId,
                                                                                          artifactId,
                                                                                          version,
                                                                                          type,
                                                                                          classifier,
                                                                                          checksum) -> {
            if (checksum != null) {
                return Optional.empty();
            }
            Optional<RepositoryItem> candidate = repository.fetch(executor,
                    new MavenDependencyKey(groupId, artifactId, type, classifier).coordinate(null, version));
            if (candidate.isEmpty() && classifier == null && (type == null || "jar".equals(type))) {
                candidate = repository.fetch(executor, groupId + "/" + artifactId + "/jar/" + version);
            }
            return candidate;
        };
    }
}

package build.jenesis.module;

import module java.base;
import build.jenesis.Discovery;
import build.jenesis.Environment;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;

@FunctionalInterface
public interface JenesisRepository extends Repository {

    enum Scope {
        MODULE,
        ARTIFACT
    }

    @Override
    default Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension)
            throws IOException {
        int colon = coordinate.lastIndexOf(':');
        String type = colon < 0 ? "jar" : coordinate.substring(colon + 1);
        String identifier = colon < 0 ? coordinate : coordinate.substring(0, colon);
        int slash = identifier.indexOf('/');
        String module = slash < 0 ? identifier : identifier.substring(0, slash);
        String version = slash < 0 ? null : identifier.substring(slash + 1);
        int dash = module.indexOf('-');
        String classifier = dash < 0 ? null : module.substring(dash + 1);
        if (dash >= 0) {
            module = module.substring(0, dash);
        }
        Optional<RepositoryItem> item = fetch(executor,
                module,
                classifier,
                version,
                extension == null ? type : type + "." + extension);
        if (item.isEmpty() && extension == null && type.equals("jmod")) {
            return fetch(executor, module, classifier, version, "jar");
        }
        return item;
    }

    @Override
    default JenesisRepository cached(Path folder) {
        if (folder == null) {
            return this;
        }
        Repository cached = Repository.super.cached(folder);
        JenesisRepository origin = this;
        return new JenesisRepository() {

            @Override
            public Optional<RepositoryItem> fetch(Executor executor,
                                                  String module,
                                                  String classifier,
                                                  String version,
                                                  String type) throws IOException {
                return cached.fetch(executor, coordinate(module, classifier, version, type));
            }

            @Override
            public Optional<RepositoryItem> fetch(Executor executor, String coordinate, String extension)
                    throws IOException {
                return cached.fetch(executor, coordinate, extension);
            }
        };
    }

    @Override
    default JenesisRepository materialized(Path folder) {
        if (folder == null) {
            return this;
        }
        Repository materialized = Repository.super.materialized(folder);
        return (executor, module, classifier, version, type) -> materialized.fetch(
                executor,
                coordinate(module, classifier, version, type));
    }

    @Override
    default JenesisRepository prepend(Repository repository) {
        JenesisRepository jenesisRepository = of(repository);
        return (executor, module, classifier, version, type) -> {
            Optional<RepositoryItem> candidate = jenesisRepository.fetch(executor, module, classifier, version, type);
            return candidate.isPresent()
                    ? candidate
                    : fetch(executor, module, classifier, version, type);
        };
    }

    @Override
    default JenesisRepository observed(Repository.Observer observer) {
        return (executor, module, classifier, version, type) -> {
            Optional<RepositoryItem> candidate = fetch(executor, module, classifier, version, type);
            if (candidate.isPresent()) {
                observer.fetched(coordinate(module, classifier, version, type), null, candidate.get());
            }
            return candidate;
        };
    }

    default JenesisRepository filter(Predicate<String> predicate) {
        return (executor, module, classifier, version, type) -> predicate.test(module)
                ? fetch(executor, module, classifier, version, type)
                : Optional.empty();
    }

    Optional<RepositoryItem> fetch(Executor executor,
                                   String module,
                                   String classifier,
                                   String version,
                                   String type) throws IOException;

    static JenesisRepository ofEnvironment(Environment environment, Scope scope) {
        return ofEnvironment(environment,
                scope,
                environment.flag("repository.discovery") ? Discovery.ofEnvironment(environment) : null);
    }

    static JenesisRepository ofEnvironment(Environment environment, Scope scope, Discovery discovery) {
        String source = environment.value("module.source", "service");
        JenesisRepository repository = switch (source) {
            case "service" -> JenesisModuleRepository.ofEnvironment(environment, scope);
            case "git" -> JenesisRawGitRepository.ofEnvironment(environment, scope);
            default -> throw new IllegalArgumentException("Unknown jenesis.module.source '"
                    + source
                    + "', expected 'service' for repo.jenesis.build or 'git' for the published index");
        };
        return discovery == null
                ? repository
                : repository.prepend(DiscoveryModuleRepository.ofEnvironment(environment, scope, discovery));
    }

    static JenesisRepository of(Repository repository) {
        return repository instanceof JenesisRepository jenesisRepository ? jenesisRepository : (executor,
                                                                                                module,
                                                                                                classifier,
                                                                                                version,
                                                                                                type) -> repository.fetch(executor, coordinate(module, classifier, version, type));
    }

    private static String coordinate(String module, String classifier, String version, String type) {
        return (classifier == null ? module : module + "-" + classifier)
                + (version == null ? "" : "/" + version)
                + (type == null || type.equals("jar") ? "" : ":" + type);
    }
}

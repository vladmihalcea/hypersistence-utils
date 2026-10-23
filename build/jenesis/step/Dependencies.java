package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.DependencyScope;
import build.jenesis.DiscoverySources;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.Palette;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Repository;
import build.jenesis.RepositoryItem;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;

public class Dependencies implements BuildExecutorModule {

    public static final String SPDX = "spdx.properties",
            GRAPH = "graph.properties",
            LICENSES = "licenses.properties",
            ARCHIVES = "archives.properties",
            ALIASED = "aliased.properties",
            INTERNAL = "internal.properties",
            RESOLUTION = "resolution.properties",
            MODULAR = "modular.properties",
            UNPINNED = "unpinned.properties";
    public static final String RESOLVED = "resolved/", MODULAR_PATH = "modular/";
    public static final String RESOLVE = "resolve", PINNED = "pinned", SIGNATURES = "signatures";
    public static final Set<String> PROCESSOR_PATH = Set.of("javac", "plugin");

    private final Map<String, Repository> repositories;
    private final Map<String, Resolver> resolvers;
    private final Signatures signatures;
    private final Pinning pinning;
    private final String group;
    private final PathPlacement pathPlacement;
    private final OffsetDateTime timestamp;
    private final Consumer<String> printing;
    private final Palette palette;

    public Dependencies(Map<String, Repository> repositories, Map<String, Resolver> resolvers) {
        this(repositories,
                resolvers,
                new Signatures(repositories),
                null,
                null,
                PathPlacement.INFERRED,
                BuildStep.timestamp(),
                null,
                Palette.NONE);
    }

    public static Dependencies ofEnvironment(Environment environment,
                                             Map<String, Repository> repositories,
                                             Map<String, Resolver> resolvers) {
        return new Dependencies(repositories,
                resolvers,
                Signatures.ofEnvironment(environment, repositories),
                Pinning.ofEnvironment(environment),
                null,
                PathPlacement.INFERRED,
                BuildStep.timestamp(environment),
                environment.flag("print.aliases") ? environment.out() : null,
                Palette.ofEnvironment(environment));
    }

    private Dependencies(Map<String, Repository> repositories,
                         Map<String, Resolver> resolvers,
                         Signatures signatures,
                         Pinning pinning,
                         String group,
                         PathPlacement pathPlacement,
                         OffsetDateTime timestamp,
                         Consumer<String> printing,
                         Palette palette) {
        this.repositories = repositories;
        this.resolvers = new LinkedHashMap<>(resolvers);
        this.signatures = signatures;
        this.pinning = pinning;
        this.group = group;
        this.pathPlacement = pathPlacement;
        this.timestamp = timestamp;
        this.printing = printing;
        this.palette = palette;
    }

    public Dependencies repositories(Map<String, Repository> repositories) {
        return new Dependencies(repositories,
                resolvers,
                signatures.repositories(repositories),
                pinning,
                group,
                pathPlacement,
                timestamp,
                printing,
                palette);
    }

    public Dependencies resolvers(Map<String, Resolver> resolvers) {
        return new Dependencies(repositories, resolvers, signatures, pinning, group, pathPlacement, timestamp, printing, palette);
    }

    public Dependencies signatures(Signatures signatures) {
        return new Dependencies(repositories, resolvers, signatures, pinning, group, pathPlacement, timestamp, printing, palette);
    }

    public Dependencies pinning(Pinning pinning) {
        return new Dependencies(repositories, resolvers, signatures, pinning, group, pathPlacement, timestamp, printing, palette);
    }

    public Dependencies group(String group) {
        return new Dependencies(repositories, resolvers, signatures, pinning, group, pathPlacement, timestamp, printing, palette);
    }

    public Dependencies pathPlacement(PathPlacement pathPlacement) {
        return new Dependencies(repositories, resolvers, signatures, pinning, group, pathPlacement, timestamp, printing, palette);
    }

    public Dependencies timestamp(OffsetDateTime timestamp) {
        return new Dependencies(repositories, resolvers, signatures, pinning, group, pathPlacement, timestamp, printing, palette);
    }

    public Dependencies printing(Consumer<String> printing, Palette palette) {
        return new Dependencies(repositories, resolvers, signatures, pinning, group, pathPlacement, timestamp, printing, palette);
    }

    public static SequencedMap<String, String> bomEntries(SequencedProperties properties, String group) {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        Resolver.bomEntries(properties).forEach((key, value) -> entries.put(group + "/" + key, value));
        return entries;
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(RESOLVE,
                new Resolve(repositories,
                        resolvers,
                        pinning == Pinning.STRICT ? null : pinning,
                        group,
                        pathPlacement,
                        printing,
                        palette,
                        timestamp),
                inherited.sequencedKeySet());
        buildExecutor.addStep(PINNED, new Pinned(pinning == Pinning.STRICT), RESOLVE);
        SequencedSet<String> verified = new LinkedHashSet<>();
        verified.add(RESOLVE);
        verified.addAll(inherited.sequencedKeySet());
        buildExecutor.addStep(SIGNATURES, signatures, verified);
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(RESOLVE) ? Optional.of("") : Optional.empty();
    }

    private static class Resolve implements BuildStep {

        private final transient Map<String, Repository> repositories;
        private final Map<String, Resolver> resolvers;
        private final Pinning pinning;
        private final String group;
        private final PathPlacement pathPlacement;
        private final OffsetDateTime timestamp;
        private final transient Consumer<String> printing;
        private final transient Palette palette;

        private Resolve(Map<String, Repository> repositories,
                        Map<String, Resolver> resolvers,
                        Pinning pinning,
                        String group,
                        PathPlacement pathPlacement,
                        Consumer<String> printing,
                        Palette palette,
                        OffsetDateTime timestamp) {
            this.repositories = repositories;
            this.resolvers = new LinkedHashMap<>(resolvers);
            this.pinning = pinning;
            this.group = group;
            this.pathPlacement = pathPlacement;
            this.timestamp = timestamp;
            this.printing = printing;
            this.palette = palette;
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            for (BuildStepArgument argument : arguments.values()) {
                Path requires = argument.removed() ? null : argument.folder().resolve(REQUIRES);
                if (requires != null && Files.isRegularFile(requires)) {
                    SequencedProperties required = SequencedProperties.ofFiles(requires);
                    for (String key : required.stringPropertyNames()) {
                        if (key.endsWith("-SNAPSHOT") && required.getProperty(key).isEmpty()) {
                            return true;
                        }
                    }
                }
            }
            return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                    Path.of(REQUIRES),
                    Path.of(VERSIONS),
                    Path.of(ALIASES),
                    Path.of(BOMS),
                    Path.of(EXCLUSIONS),
                    Path.of(MANAGED),
                    Path.of(OVERRIDES),
                    Path.of(SPDX),
                    Path.of(DEPENDENCIES),
                    Path.of(MODULAR),
                    Path.of(Assign.VARIANTS),
                    Path.of(Abi.FOLDER),
                    Path.of(RESOLVED)));
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            boolean pinned = pinning != Pinning.IGNORE;
            Map<String, String> aliases = aliases(arguments.values().stream()
                    .filter(argument -> !argument.removed())
                    .map(BuildStepArgument::folder)
                    .toList());
            Map<String, String> categories = new LinkedHashMap<>(DEFAULT_CATEGORIES);
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path file = argument.folder().resolve(SPDX);
                if (Files.isRegularFile(file)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(file);
                    for (String key : properties.stringPropertyNames()) {
                        String value = properties.getProperty(key).trim();
                        if (key.startsWith("category/")) {
                            String identifier = key.substring("category/".length()).trim();
                            if (value.isEmpty()) {
                                categories.remove(identifier);
                            } else {
                                categories.put(identifier, value);
                            }
                        } else if (!key.startsWith("alias/")) {
                            throw new IllegalArgumentException("Expected key to be prefixed: " + key);
                        }
                    }
                }
            }
            SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>>> requires = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> versions = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> moduleAliases = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> moduleOverrides = new LinkedHashMap<>();
            SequencedMap<String, String> bomTokens = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedSet<String>>>>> exclusions = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedSet<String>>>> managedExclusions = new LinkedHashMap<>();
            SequencedMap<String, String> variants = new TreeMap<>();
            Set<String> handed = new HashSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path variantsFile = argument.folder().resolve(Assign.VARIANTS);
                if (Files.exists(variantsFile)) {
                    SequencedProperties.ofFiles(variantsFile).forEachProperty(variants::putIfAbsent);
                }
                Path handedFile = argument.folder().resolve(DEPENDENCIES);
                if (Files.exists(handedFile)) {
                    handed.addAll(SequencedProperties.ofFiles(handedFile).stringPropertyNames());
                }
                Path requiresFile = argument.folder().resolve(REQUIRES);
                if (Files.exists(requiresFile)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(requiresFile);
                    for (String key : properties.stringPropertyNames()) {
                        String[] parts = split(key);
                        if (parts == null) {
                            continue;
                        }
                        requires.computeIfAbsent(parts[0], _ -> new LinkedHashMap<>())
                                .computeIfAbsent(parts[1], _ -> new LinkedHashMap<>())
                                .computeIfAbsent(parts[2], _ -> new LinkedHashMap<>())
                                .merge(parts[3], properties.getProperty(key), (left, right) -> left.isEmpty() ? right : left);
                    }
                }
                Path versionsFile = argument.folder().resolve(VERSIONS);
                if (Files.exists(versionsFile)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(versionsFile);
                    for (String key : properties.stringPropertyNames()) {
                        int first = key.indexOf('/');
                        int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                        if (first < 1 || second <= first || second == key.length() - 1) {
                            throw new IllegalArgumentException("Malformed version pin '"
                                    + key
                                    + "' in "
                                    + versionsFile
                                    + ": expected <group>/<repository>/<coordinate>");
                        }
                        versions.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                                .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                                .putIfAbsent(key.substring(second + 1), properties.getProperty(key));
                    }
                }
                Path aliasesFile = argument.folder().resolve(ALIASES);
                if (Files.exists(aliasesFile)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(aliasesFile);
                    for (String key : properties.stringPropertyNames()) {
                        int first = key.indexOf('/');
                        int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                        if (first < 1 || second <= first || second == key.length() - 1) {
                            throw new IllegalArgumentException("Malformed module alias '"
                                    + key
                                    + "' in "
                                    + aliasesFile
                                    + ": expected <group>/<repository>/<module-name>");
                        }
                        moduleAliases.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                                .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                                .putIfAbsent(key.substring(second + 1), properties.getProperty(key));
                    }
                }
                Path overridesFile = argument.folder().resolve(OVERRIDES);
                if (Files.exists(overridesFile)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(overridesFile);
                    for (String key : properties.stringPropertyNames()) {
                        int first = key.indexOf('/');
                        int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                        if (first < 1 || second <= first || second == key.length() - 1) {
                            throw new IllegalArgumentException("Malformed module override '"
                                    + key
                                    + "' in "
                                    + overridesFile
                                    + ": expected <group>/<repository>/<module-name>");
                        }
                        moduleOverrides.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                                .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                                .putIfAbsent(key.substring(second + 1), properties.getProperty(key));
                    }
                }
                Path bomsFile = argument.folder().resolve(BOMS);
                if (Files.exists(bomsFile)) {
                    boolean resolution = Files.exists(argument.folder().resolve(DEPENDENCIES));
                    SequencedProperties properties = SequencedProperties.ofFiles(bomsFile);
                    for (String key : properties.stringPropertyNames()) {
                        if (resolution && !key.startsWith("entry/")) {
                            continue;
                        }
                        String reference = key.startsWith("bom/")
                                ? key.substring(4)
                                : key.startsWith("entry/") ? key.substring(6) : null;
                        int first = reference == null ? -1 : reference.indexOf('/');
                        int second = first < 1 ? -1 : reference.indexOf('/', first + 1);
                        if (first < 1 || second <= first || second == reference.length() - 1) {
                            throw new IllegalArgumentException("Malformed BOM reference '"
                                    + key
                                    + "' in "
                                    + bomsFile
                                    + ": expected bom/<group>/<repository>/<coordinate>"
                                    + " or entry/<group>/<repository>/<coordinate>");
                        }
                        bomTokens.putIfAbsent(key, properties.getProperty(key));
                    }
                }
                Path exclusionsFile = argument.folder().resolve(EXCLUSIONS);
                if (Files.exists(exclusionsFile)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(exclusionsFile);
                    for (String key : properties.stringPropertyNames()) {
                        String[] parts = split(key);
                        if (parts == null) {
                            continue;
                        }
                        SequencedSet<String> excludes = new LinkedHashSet<>();
                        String value = properties.getProperty(key);
                        if (!value.isEmpty()) {
                            excludes.addAll(Arrays.asList(value.split(",")));
                        }
                        exclusions.computeIfAbsent(parts[0], _ -> new LinkedHashMap<>())
                                .computeIfAbsent(parts[1], _ -> new LinkedHashMap<>())
                                .computeIfAbsent(parts[2], _ -> new LinkedHashMap<>())
                                .put(parts[3], excludes);
                    }
                }
                Path managedFile = argument.folder().resolve(MANAGED);
                if (Files.exists(managedFile)) {
                    SequencedProperties properties = SequencedProperties.ofFiles(managedFile);
                    for (String key : properties.stringPropertyNames()) {
                        int first = key.indexOf('/');
                        int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                        if (first < 1 || second <= first || second == key.length() - 1) {
                            throw new IllegalArgumentException("Malformed managed exclusion '"
                                    + key
                                    + "' in "
                                    + managedFile
                                    + ": expected <group>/<repository>/<coordinate>");
                        }
                        managedExclusions.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                                .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                                .computeIfAbsent(key.substring(second + 1), _ -> new LinkedHashSet<>())
                                .addAll(properties.entries(key));
                    }
                }
            }
            if (group != null) {
                requires.keySet().retainAll(Set.of(group));
                moduleAliases.keySet().retainAll(Set.of(group));
                moduleOverrides.keySet().retainAll(Set.of(group));
                bomTokens.keySet().removeIf(token -> {
                    int first = token.indexOf('/'), second = token.indexOf('/', first + 1);
                    return second < 0 || !group.equals(token.substring(first + 1, second));
                });
                exclusions.keySet().retainAll(Set.of(group));
                managedExclusions.keySet().retainAll(Set.of(group));
                versions.keySet().retainAll(Set.of(group));
            }
            Path libs = Files.createDirectories(context.next().resolve(RESOLVED));
            SequencedMap<String, Path> previousArtifacts = new LinkedHashMap<>();
            SequencedSet<String> previousInternal = new LinkedHashSet<>(), previousSnapshots = new LinkedHashSet<>();
            if (context.previous() != null) {
                Path previousInternalFile = context.previous().resolve(INTERNAL);
                if (Files.exists(previousInternalFile)) {
                    for (String dependency : SequencedProperties.ofFiles(previousInternalFile).stringPropertyNames()) {
                        previousInternal.add(dependency.substring(dependency.indexOf('/') + 1));
                    }
                }
                Path previousIndex = context.previous().resolve(DEPENDENCIES);
                if (Files.exists(previousIndex)) {
                    SequencedProperties.ofFiles(previousIndex).forEachProperty((key, value) -> {
                        String[] parts = split(key);
                        if (parts == null) {
                            return;
                        }
                        int space = value.indexOf(' ');
                        Path file = context.previous()
                                .resolve(space < 0 ? value : value.substring(0, space))
                                .normalize();
                        if (previousInternal.contains(parts[3])) {
                            return;
                        }
                        if (parts[3].endsWith("-SNAPSHOT")) {
                            previousSnapshots.add(parts[3]);
                        } else if (Files.exists(file)) {
                            previousArtifacts.putIfAbsent(parts[3], file);
                        }
                    });
                }
            }
            Map<String, Repository> wrapped = new LinkedHashMap<>();
            ConcurrentNavigableMap<String, RepositoryItem> fetched = new ConcurrentSkipListMap<>();
            repositories.forEach((name, repository) -> {
                Repository effective = repository;
                if (!previousArtifacts.isEmpty()) {
                    effective = effective.prepend((_, coordinate, extension) -> extension == null
                            ? Optional.ofNullable(previousArtifacts.get(coordinate)).map(RepositoryItem::ofFile)
                            : Optional.empty());
                }
                wrapped.put(name, effective.materialized(libs).observed((coordinate, extension, item) -> {
                    if (item.internal()) {
                        fetched.putIfAbsent(name + "/" + coordinate + (extension == null ? "" : "#" + extension), item);
                    }
                }));
            });
            Path previousResolution = context.previous() == null ? null : context.previous().resolve(RESOLUTION);
            if (previousResolution != null && Files.exists(previousResolution)) {
                SequencedProperties stored = SequencedProperties.ofFiles(previousResolution);
                SequencedMap<String, RepositoryItem> current = new LinkedHashMap<>();
                boolean reusable = true;
                for (String key : stored.stringPropertyNames()) {
                    if (!key.startsWith("fetched/")) {
                        continue;
                    }
                    String location = key.substring("fetched/".length());
                    int slash = location.indexOf('/'), hash = location.lastIndexOf('#');
                    Repository repository = wrapped.get(location.substring(0, slash));
                    RepositoryItem item = repository == null ? null : repository.fetch(executor,
                            location.substring(slash + 1, hash < 0 ? location.length() : hash),
                            hash < 0 ? null : location.substring(hash + 1)).orElse(null);
                    if (item == null || !item.internal() || !fingerprint(item).equals(stored.getProperty(key))) {
                        reusable = false;
                        break;
                    }
                    current.put(location, item);
                }
                if (reusable && previousSnapshots.isEmpty() && key(stored,
                        requires,
                        new TreeMap<>(categories),
                        new TreeMap<>(aliases),
                        versions,
                        moduleAliases,
                        moduleOverrides,
                        bomTokens,
                        exclusions,
                        managedExclusions,
                        variants).equals(stored.getProperty("inputs"))) {
                    try (Stream<Path> files = Files.walk(context.previous())) {
                        for (Path file : files.toList()) {
                            Path target = context.next().resolve(context.previous().relativize(file));
                            if (Files.isDirectory(file)) {
                                Files.createDirectories(target);
                            } else {
                                BuildStep.linkOrCopy(target, file);
                            }
                        }
                    }
                    SequencedProperties index = SequencedProperties.ofFiles(context.next().resolve(DEPENDENCIES));
                    Set<Path> refreshed = new HashSet<>();
                    SequencedMap<String, Path> refreshedInternals = new LinkedHashMap<>();
                    for (String key : index.stringPropertyNames()) {
                        String[] parts = split(key);
                        String location = parts == null
                                ? null
                                : stored.getProperty("placed/" + parts[2] + "/" + parts[3]);
                        if (location == null) {
                            continue;
                        }
                        String value = index.getProperty(key);
                        int space = value.indexOf(' ');
                        String relative = space < 0 ? value : value.substring(0, space);
                        Path target = context.next().resolve(relative).normalize();
                        refreshedInternals.put(parts[2] + "/" + parts[3], target);
                        if (refreshed.add(target)) {
                            Files.deleteIfExists(target);
                            BuildStep.linkOrCopy(target, current.get(location).file().orElseThrow(() ->
                                    new IllegalStateException("Repository did not materialize a file for "
                                            + location)));
                        }
                        index.setProperty(key, relative);
                    }
                    index.store(context.next().resolve(DEPENDENCIES));
                    try (Stream<Path> files = Files.list(libs)) {
                        for (Path file : files.toList()) {
                            if (file.getFileName().toString().endsWith("." + Abi.ABI)) {
                                Files.delete(file);
                            }
                        }
                    }
                    companions(arguments, refreshedInternals);
                    return CompletableFuture.completedStage(new BuildStepResult(true));
                }
                fetched.clear();
            }
            SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> managed = new LinkedHashMap<>();
            SequencedProperties unpinned = new SequencedProperties();
            if (!bomTokens.isEmpty()) {
                SequencedMap<String, String> merged = new LinkedHashMap<>();
                SequencedMap<String, String> covering = new LinkedHashMap<>();
                SequencedProperties resolvedBoms = new SequencedProperties();
                for (Map.Entry<String, String> token : bomTokens.entrySet()) {
                    if (token.getKey().startsWith("entry/")) {
                        String key = token.getKey().substring(6);
                        int groupSlash = key.indexOf('/');
                        int repositorySlash = key.indexOf('/', groupSlash + 1);
                        String repository = key.substring(groupSlash + 1, repositorySlash);
                        if (wrapped.get(Resolver.base(repository)) == null) {
                            throw new IllegalArgumentException("Unknown repository '"
                                    + repository
                                    + "' for bill of materials entry '"
                                    + key.substring(repositorySlash + 1)
                                    + "': a coordinate carrying a type or a classifier names its"
                                    + " repository first, as maven/<groupId>/<artifactId>/<type>/<classifier>,"
                                    + " because a bare <groupId>/<artifactId> reads the first segment as one");
                        }
                        String aliased = moduleAliases
                                .getOrDefault(key.substring(0, groupSlash), Collections.emptyNavigableMap())
                                .getOrDefault(repository, Collections.emptyNavigableMap())
                                .get(key.substring(repositorySlash + 1));
                        if (aliased != null) {
                            throw new IllegalArgumentException("Bill of materials entry '"
                                    + key.substring(repositorySlash + 1)
                                    + "' names a module this project aliases to "
                                    + aliased
                                    + ": an entry under a module name is looked up in the module"
                                    + " repository by every module importing the file, which is the"
                                    + " lookup the alias exists to replace. Pin the coordinate the"
                                    + " alias names instead, or drop the alias");
                        }
                        merged.put(key, token.getValue());
                        covering.put(key, token.getValue());
                        continue;
                    }
                    String reference = token.getKey().substring(4);
                    int first = reference.indexOf('/');
                    int second = reference.indexOf('/', first + 1);
                    String group = reference.substring(0, first);
                    String repo = reference.substring(first + 1, second);
                    String coordinate = reference.substring(second + 1);
                    String value = token.getValue();
                    int space = value.indexOf(' ');
                    String version = space < 0 ? value : value.substring(0, space);
                    String checksum = space < 0 ? "" : value.substring(space + 1).trim();
                    if (wrapped.get(Resolver.base(repo)) == null) {
                        throw new IllegalArgumentException("Unknown repository for BOM: " + reference);
                    }
                    boolean verify = pinning != Pinning.VERSIONS && pinning != Pinning.IGNORE;
                    Resolver.Bom bom;
                    try {
                        bom = resolvers.getOrDefault(Resolver.base(repo), Resolver.identity()).bom(executor,
                                repo,
                                wrapped,
                                coordinate,
                                version,
                                verify ? checksum : null,
                                pinning == Pinning.IGNORE);
                    } catch (RuntimeException e) {
                        throw new IllegalStateException("Failed to fetch BOM " + reference, e);
                    }
                    if (bom.verifiable() && checksum.isEmpty() && !bom.internal()) {
                        unpinned.setProperty("bom/" + reference, "");
                    }
                    if (!version.isEmpty() && !bom.version().isEmpty()) {
                        if (bom.verifiable()) {
                            resolvedBoms.setProperty("bom/" + reference + "/" + bom.version(),
                                    context.next().toAbsolutePath().relativize(bom.file().toAbsolutePath())
                                            .toString().replace(File.separatorChar, '/'));
                        } else {
                            resolvedBoms.setProperty("version/" + reference, bom.version());
                        }
                    }
                    for (Map.Entry<String, String> entry : bom.entries().entrySet()) {
                        String key = group + "/" + entry.getKey();
                        merged.put(key, entry.getValue());
                        if (bom.verifiable()) {
                            covering.put(key, entry.getValue());
                        } else {
                            covering.remove(key);
                        }
                    }
                }
                for (Map.Entry<String, String> entry : covering.entrySet()) {
                    resolvedBoms.setProperty("entry/" + entry.getKey(), entry.getValue());
                }
                for (Map.Entry<String, String> entry : merged.entrySet()) {
                    String key = entry.getKey();
                    int first = key.indexOf('/');
                    int second = key.indexOf('/', first + 1);
                    managed.computeIfAbsent(key.substring(0, first), _ -> new LinkedHashMap<>())
                            .computeIfAbsent(key.substring(first + 1, second), _ -> new LinkedHashMap<>())
                            .putIfAbsent(key.substring(second + 1), entry.getValue());
                }
                if (!resolvedBoms.isEmpty()) {
                    resolvedBoms.store(context.next().resolve(BOMS));
                }
            }
            SequencedProperties resolved = new SequencedProperties();
            SequencedMap<String, Resolver.Resolved> materialized = new LinkedHashMap<>();
            SequencedMap<String, Alias> aliasTargets = new LinkedHashMap<>();
            for (SequencedMap<String, SequencedMap<String, String>> byRepository : moduleAliases.values()) {
                for (SequencedMap<String, String> byAlias : byRepository.values()) {
                    byAlias.forEach((alias, token) -> merge(aliasTargets, alias, token, LOCAL));
                }
            }
            SequencedMap<String, Overridden> overrideTargets = new LinkedHashMap<>();
            for (SequencedMap<String, SequencedMap<String, String>> byRepository : moduleOverrides.values()) {
                for (SequencedMap<String, String> byModule : byRepository.values()) {
                    byModule.forEach((module, carriers) -> merge(overrideTargets,
                            module,
                            PathPlacement.overrides(module + "=" + carriers, "a local @jenesis.override declaration")
                                    .get(module),
                            "a local @jenesis.override declaration"));
                }
            }
            if (!overrideTargets.isEmpty()
                    && resolvers.values().stream().allMatch(resolver -> resolver.managedPrefixes().isEmpty())) {
                throw new IllegalArgumentException("Cannot override "
                        + overrideTargets.sequencedKeySet()
                        + ": a module override needs a layout where module names resolve to Maven coordinates"
                        + " - drop the declaration or build with jenesis.project.layout=modular_to_maven");
            }
            SequencedSet<String> aliasTokens = new LinkedHashSet<>();
            for (Alias alias : aliasTargets.values()) {
                aliasTokens.add(alias.token());
            }
            SequencedMap<String, String> modules = new LinkedHashMap<>();
            SequencedMap<String, Boolean> explicit = new LinkedHashMap<>();
            SequencedProperties graph = new SequencedProperties();
            SequencedProperties licenses = new SequencedProperties();
            int edge = 0;
            SequencedProperties layers = new SequencedProperties();
            SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>>>
                    pending = new LinkedHashMap<>(requires);
            SequencedMap<String, String> discovered = new LinkedHashMap<>();
            while (!pending.isEmpty()) {
            SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>>>
                    round = pending;
            pending = new LinkedHashMap<>();
            SequencedSet<String> before = new LinkedHashSet<>(materialized.sequencedKeySet());
            for (Map.Entry<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>>> groupEntry : round.entrySet()) {
                String group = groupEntry.getKey();
                for (String scope : groupEntry.getValue().sequencedKeySet()) {
                    DependencyScope intent = scope.equals("compile") ? DependencyScope.COMPILE : DependencyScope.RUNTIME;
                    for (Map.Entry<String, SequencedMap<String, String>> repoEntry : groupEntry.getValue().get(scope).entrySet()) {
                        String repo = repoEntry.getKey();
                        Resolver resolver = resolvers.get(Resolver.base(repo));
                        if (resolver == null) {
                            throw new IllegalArgumentException("Cannot resolve "
                                    + repoEntry.getValue().sequencedKeySet().stream().map(coordinate -> repo + "/" + coordinate).toList()
                                    + ": this layout resolves no " + Resolver.base(repo) + " coordinate, only those of "
                                    + resolvers.keySet().stream().sorted().toList()
                                    + (Resolver.base(repo).equals("maven")
                                            ? " - a @jenesis.alias or a Maven coordinate names one, which the modular_to_maven"
                                                    + " and maven layouts resolve, so build with"
                                                    + " -Djenesis.project.layout=modular_to_maven or require the module by a"
                                                    + " name the module repository serves"
                                            : " - name a coordinate of one of those"));
                        }
                        SequencedMap<String, SequencedSet<String>> repoExclusions = exclusions
                                .getOrDefault(group, Collections.emptyNavigableMap())
                                .getOrDefault(scope, Collections.emptyNavigableMap())
                                .getOrDefault(repo, Collections.emptyNavigableMap());
                        SequencedMap<String, SequencedSet<String>> coordinates = new LinkedHashMap<>();
                        SequencedMap<String, SequencedSet<String>> deferred = new LinkedHashMap<>();
                        for (String coordinate : repoEntry.getValue().sequencedKeySet()) {
                            if (overrideTargets.containsKey(coordinate)) {
                                continue;
                            }
                            SequencedSet<String> excludes = repoExclusions.getOrDefault(coordinate, Collections.emptyNavigableSet());
                            if (aliasTokens.contains(coordinate)) {
                                deferred.put(coordinate, excludes);
                            } else {
                                coordinates.put(coordinate, excludes);
                            }
                        }
                        SequencedMap<String, SequencedMap<String, String>> groupVersions = versions
                                .getOrDefault(group, Collections.emptyNavigableMap());
                        SequencedMap<String, SequencedMap<String, String>> groupManaged = managed
                                .getOrDefault(group, Collections.emptyNavigableMap());
                        List<SequencedMap<String, String>> scoped = new ArrayList<>();
                        List<SequencedMap<String, String>> scopedManaged = new ArrayList<>();
                        scoped.add(groupVersions.getOrDefault(repo, Collections.emptyNavigableMap()));
                        scopedManaged.add(groupManaged.getOrDefault(repo, Collections.emptyNavigableMap()));
                        for (String managedPrefix : resolver.managedPrefixes()) {
                            scoped.add(groupVersions.getOrDefault(managedPrefix, Collections.emptyNavigableMap()));
                            scopedManaged.add(groupManaged.getOrDefault(managedPrefix, Collections.emptyNavigableMap()));
                        }
                        SequencedMap<String, String> bom = new LinkedHashMap<>();
                        if (!pinned) {
                            for (SequencedMap<String, String> entries : scopedManaged) {
                                entries.forEach((coordinate, value) -> {
                                    int space = value.indexOf(' ');
                                    bom.putIfAbsent(coordinate, space < 0 ? value : value.substring(0, space));
                                });
                            }
                        }
                        for (SequencedMap<String, String> pins : scoped) {
                            if (pinning == Pinning.VERSIONS) {
                                pins.forEach((coordinate, value) -> {
                                    int space = value.indexOf(' ');
                                    bom.putIfAbsent(coordinate, space < 0 ? value : value.substring(0, space));
                                });
                            } else if (pinned) {
                                pins.forEach(bom::putIfAbsent);
                            } else {
                                pins.forEach((coordinate, value) -> {
                                    int space = value.indexOf(' ');
                                    String qualified = space < 0 ? value : value.substring(0, space);
                                    if (coordinates.containsKey(coordinate)) {
                                        bom.putIfAbsent(coordinate, qualified);
                                    } else if (qualified.startsWith(":")) {
                                        int divider = qualified.indexOf(':', 1);
                                        bom.putIfAbsent(coordinate, divider < 0
                                                ? qualified
                                                : qualified.substring(0, divider));
                                    }
                                });
                            }
                        }
                        if (pinned) {
                            for (SequencedMap<String, String> entries : scopedManaged) {
                                if (pinning == Pinning.VERSIONS) {
                                    entries.forEach((coordinate, value) -> {
                                        int space = value.indexOf(' ');
                                        bom.putIfAbsent(coordinate, space < 0 ? value : value.substring(0, space));
                                    });
                                } else {
                                    entries.forEach(bom::putIfAbsent);
                                }
                            }
                        }
                        SequencedMap<String, SequencedMap<String, SequencedSet<String>>> groupExclusions = managedExclusions
                                .getOrDefault(group, Collections.emptyNavigableMap());
                        SequencedMap<String, SequencedSet<String>> managedExcludes = new LinkedHashMap<>(
                                groupExclusions.getOrDefault(repo, Collections.emptyNavigableMap()));
                        for (String managedPrefix : resolver.managedPrefixes()) {
                            groupExclusions.getOrDefault(managedPrefix, Collections.emptyNavigableMap())
                                    .forEach(managedExcludes::putIfAbsent);
                        }
                        Resolver.Resolution resolution = resolver.dependencies(executor,
                                repo,
                                wrapped,
                                coordinates,
                                bom,
                                managedExcludes,
                                intent);
                        if (!deferred.isEmpty()) {
                            SequencedMap<String, SequencedSet<String>> absent = new LinkedHashMap<>();
                            for (Map.Entry<String, SequencedSet<String>> entry : deferred.entrySet()) {
                                Resolver.Vertex node = resolution.vertices().get(repo + "/" + entry.getKey());
                                if (node == null) {
                                    absent.put(entry.getKey() + "/LATEST", entry.getValue());
                                } else if (!entry.getValue().isEmpty() && node.resolvedVersion() != null) {
                                    absent.put(entry.getKey() + "/" + node.resolvedVersion(), entry.getValue());
                                }
                            }
                            if (!absent.isEmpty()) {
                                coordinates.putAll(absent);
                                resolution = resolver.dependencies(executor, repo, wrapped, coordinates, bom, managedExcludes, intent);
                            }
                        }
                        if (!overrideTargets.isEmpty() && !coordinates.isEmpty()) {
                            SequencedSet<String> overridden = new LinkedHashSet<>();
                            resolution.vertices().forEach((coordinate, node) -> {
                                if (node.module() != null && overrideTargets.containsKey(node.module())) {
                                    String artifact = coordinate.substring(coordinate.indexOf('/') + 1);
                                    int slash = artifact.indexOf('/');
                                    int second = slash < 0 ? -1 : artifact.indexOf('/', slash + 1);
                                    String name = second < 0 ? artifact : artifact.substring(0, second);
                                    overridden.add(name);
                                    graph.setProperty("overridden/" + group + "/" + scope + "/"
                                            + coordinate.substring(0, coordinate.indexOf('/') + 1) + name, node.module());
                                }
                            });
                            if (!overridden.isEmpty()) {
                                coordinates.replaceAll((_, excludes) -> {
                                    SequencedSet<String> merged = new LinkedHashSet<>(excludes);
                                    merged.addAll(overridden);
                                    return merged;
                                });
                                resolution = resolver.dependencies(executor, repo, wrapped, coordinates, bom, managedExcludes, intent);
                            }
                        }
                        for (Map.Entry<String, Resolver.Resolved> entry : resolution.artifacts().entrySet()) {
                            String coordinate = entry.getKey().substring(entry.getKey().indexOf('/') + 1);
                            String declared = repoEntry.getValue().get(coordinate);
                            String value = declared != null && !declared.isEmpty() ? declared : entry.getValue().checksum();
                            String transitiveKey = group + "/" + scope + "/" + entry.getKey();
                            resolved.setProperty(transitiveKey, value);
                            materialized.putIfAbsent(transitiveKey, entry.getValue());
                        }
                        for (Resolver.Edge dependency : resolution.edges()) {
                            graph.setProperty("edge/" + edge++, String.join("\t",
                                    group,
                                    scope,
                                    repo,
                                    Boolean.toString(dependency.followed()),
                                    text(dependency.scope()),
                                    text(dependency.version()),
                                    text(dependency.parent()),
                                    dependency.coordinate()));
                        }
                        for (Map.Entry<String, Resolver.Vertex> entry : resolution.vertices().entrySet()) {
                            Resolver.Vertex node = entry.getValue();
                            graph.setProperty("vertex/" + group + "/" + scope + "/" + entry.getKey(), String.join("\t",
                                    text(node.resolvedVersion()),
                                    text(node.module()),
                                    Boolean.toString(node.automatic()),
                                    Boolean.toString(node.internal())));
                            String versioned = node.resolvedVersion() == null
                                    ? entry.getKey()
                                    : entry.getKey() + "/" + node.resolvedVersion();
                            explicit.putIfAbsent(versioned, node.module() != null && !node.automatic());
                            if (node.module() != null) {
                                modules.putIfAbsent(node.module(), versioned);
                            }
                            if (!node.licenses().isEmpty()) {
                                String licenseKey = node.resolvedVersion() == null
                                        ? entry.getKey()
                                        : entry.getKey() + "/" + node.resolvedVersion();
                                for (int i = 0; i < node.licenses().size(); i++) {
                                    License license = node.licenses().get(i);
                                    String id = license.identified(aliases).id();
                                    String category = license.category();
                                    if (category == null && id != null) {
                                        category = categories.get(id);
                                    }
                                    if (id != null) {
                                        licenses.setProperty(licenseKey + "#" + i + "#id", id);
                                    }
                                    if (category != null) {
                                        licenses.setProperty(licenseKey + "#" + i + "#category", category);
                                    }
                                    if (license.name() != null) {
                                        licenses.setProperty(licenseKey + "#" + i + "#name", license.name());
                                    }
                                    if (license.url() != null) {
                                        licenses.setProperty(licenseKey + "#" + i + "#url", license.url());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            for (String key : materialized.sequencedKeySet()) {
                if (before.contains(key)) {
                    continue;
                }
                for (Map.Entry<String, SequencedSet<String>> layer
                        : PathPlacement.layers(materialized.get(key).file()).entrySet()) {
                    String coordinate = key.substring(key.indexOf('/', key.indexOf('/') + 1) + 1);
                    String previous = discovered.put(layer.getKey(), coordinate);
                    if (previous != null) {
                        if (previous.equals(coordinate)) {
                            continue;
                        }
                        throw new IllegalStateException(previous + " and " + coordinate
                                + " both declare a layer called " + layer.getKey()
                                + " - a layer is named on its own, so rename one of them or keep one out");
                    }
                    graph.setProperty("layer/" + layer.getKey(), coordinate);
                    ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(materialized.get(key).file());
                    Iterator<String> tokens = layer.getValue().iterator();
                    layers.setProperty(layer.getKey(),
                            (descriptor == null ? key : descriptor.name()) + " " + tokens.next());
                    while (tokens.hasNext()) {
                        String token = tokens.next();
                        int slash = token.indexOf('/');
                        if (slash < 1) {
                            throw new IllegalArgumentException("Malformed " + PathPlacement.LAYERS
                                    + " coordinate '" + token + "' in " + key
                                    + ": expected <repository>/<coordinate>");
                        }
                        pending.computeIfAbsent("layer:" + layer.getKey(), _ -> new LinkedHashMap<>())
                                .computeIfAbsent("runtime", _ -> new LinkedHashMap<>())
                                .computeIfAbsent(token.substring(0, slash), _ -> new LinkedHashMap<>())
                                .putIfAbsent(token.substring(slash + 1), "");
                    }
                }
            }
            }
            if (!layers.isEmpty()) {
                layers.store(context.next().resolve(BuildStep.LAYERS));
            }
            UnaryOperator<String> unversioned = coordinate -> coordinate.chars().filter(character -> character == '/').count() == 3
                    ? coordinate.substring(0, coordinate.lastIndexOf('/'))
                    : coordinate;
            SequencedSet<String> replaced = new LinkedHashSet<>();
            for (String key : materialized.sequencedKeySet()) {
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                String main = variants.get(key.substring(second + 1));
                if (main != null) {
                    replaced.add(key.substring(0, first + 1) + unversioned.apply(main));
                }
            }
            materialized.keySet().removeIf(key -> {
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                boolean main = replaced.contains(key.substring(0, first + 1) + unversioned.apply(key.substring(second + 1)));
                if (main) {
                    resolved.remove(key);
                }
                return main;
            });
            SequencedMap<String, Path> placed = new LinkedHashMap<>();
            SequencedMap<String, SequencedSet<String>> grouped = new LinkedHashMap<>();
            SequencedMap<String, String> checksums = new LinkedHashMap<>();
            SequencedMap<String, Boolean> internals = new LinkedHashMap<>();
            SequencedMap<String, Path> sources = new LinkedHashMap<>();
            for (Map.Entry<String, Resolver.Resolved> entry : materialized.entrySet()) {
                String key = entry.getKey();
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                if (first < 0 || second < 0) {
                    continue;
                }
                String dependency = key.substring(second + 1);
                grouped.computeIfAbsent(dependency, _ -> new LinkedHashSet<>()).add(key.substring(0, first));
                Resolver.Resolved artifact = entry.getValue();
                String value = resolved.getProperty(key);
                Path file = placed.get(dependency);
                if (file == null) {
                    if (artifact.internal()) {
                        file = libs.resolve(PathPlacement.fileName(
                                dependency.substring(dependency.indexOf('/') + 1)));
                        if (!Files.exists(file)) {
                            BuildStep.linkOrCopy(file, artifact.file());
                        }
                    } else {
                        file = artifact.file();
                    }
                    placed.put(dependency, file);
                    internals.put(dependency, artifact.internal());
                    if (artifact.internal()) {
                        sources.put(dependency, artifact.file());
                    }
                }
                checksums.merge(dependency, value, (left, right) -> {
                    if (right.isEmpty()) {
                        return left;
                    }
                    if (!left.isEmpty() && !left.equals(right)) {
                        throw new IllegalStateException("Conflicting checksums pinned for " + dependency + ": " + left + " and " + right);
                    }
                    return left.isEmpty() ? right : left;
                });
            }
            SequencedMap<String, String> aliased = rename(placed, grouped, aliasTargets, modules, explicit, pathPlacement, libs, printing, palette);
            for (Map.Entry<String, Overridden> entry : overrideTargets.entrySet()) {
                for (String carrier : entry.getValue().carriers()) {
                    if (!modules.containsKey(carrier)) {
                        throw new IllegalArgumentException("Module override "
                                + entry.getKey()
                                + " declared by "
                                + entry.getValue().origin()
                                + " names "
                                + carrier
                                + " which no resolved dependency carries"
                                + " - require the carrier or drop the override");
                    }
                }
            }
            for (Map.Entry<String, SequencedMap<String, SequencedMap<String, String>>> byGroup : moduleOverrides.entrySet()) {
                SequencedSet<String> scopes = requires
                        .getOrDefault(byGroup.getKey(), Collections.emptyNavigableMap())
                        .sequencedKeySet();
                for (SequencedMap<String, String> byModule : byGroup.getValue().values()) {
                    for (String module : byModule.sequencedKeySet()) {
                        Path file = libs.resolve(BuildExecutorModule.encode(module) + ".jar");
                        if (!Files.exists(file)) {
                            try (JarOutputStream environment = new JarOutputStream(Files.newOutputStream(file))) {
                                JarEntry entry = new JarEntry("module-info.class");
                                if (timestamp != null) {
                                    entry.setTimeLocal(timestamp.toLocalDateTime());
                                }
                                environment.putNextEntry(entry);
                                environment.write(carrying(module, overrideTargets.get(module).carriers()));
                                environment.closeEntry();
                            }
                        }
                        placed.put("module/" + module, file);
                        for (String scope : scopes) {
                            String key = byGroup.getKey() + "/" + scope + "/module/" + module;
                            materialized.put(key, new Resolver.Resolved(file, "", false));
                            resolved.setProperty(key, "");
                        }
                    }
                }
            }
            SequencedProperties index = new SequencedProperties();
            for (Map.Entry<String, Resolver.Resolved> entry : materialized.entrySet()) {
                String key = entry.getKey();
                int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                if (first < 0 || second < 0) {
                    continue;
                }
                String value = resolved.getProperty(key);
                String relative = context.next()
                        .relativize(placed.get(key.substring(second + 1)))
                        .toString()
                        .replace(File.separatorChar, '/');
                index.setProperty(key, value.isEmpty() || internals.getOrDefault(key.substring(second + 1), false)
                        ? relative
                        : relative + " " + value);
            }
            if (!aliased.isEmpty()) {
                SequencedProperties properties = new SequencedProperties();
                aliased.forEach(properties::setProperty);
                properties.store(context.next().resolve(ALIASED));
            }
            SequencedMap<String, Path> placedInternals = new LinkedHashMap<>();
            internals.forEach((dependency, internal) -> {
                Path jar = placed.get(dependency);
                if (internal && jar != null && jar.startsWith(libs)) {
                    placedInternals.put(dependency, jar);
                }
            });
            companions(arguments, placedInternals);
            SequencedProperties produced = new SequencedProperties();
            internals.forEach((dependency, internal) -> {
                if (internal) {
                    produced.setProperty(dependency, "");
                }
            });
            if (!produced.isEmpty()) {
                produced.store(context.next().resolve(INTERNAL));
            }
            Set<Path> pinnedFiles = new HashSet<>();
            for (Map.Entry<String, String> entry : checksums.entrySet()) {
                if (!entry.getValue().isEmpty()) {
                    pinnedFiles.add(placed.get(entry.getKey()));
                }
            }
            for (Map.Entry<String, String> entry : checksums.entrySet()) {
                if (entry.getValue().isEmpty()
                        && !internals.get(entry.getKey())
                        && !pinnedFiles.contains(placed.get(entry.getKey()))) {
                    unpinned.setProperty(entry.getKey(), managing(managed, entry.getKey()));
                }
            }
            if (!unpinned.isEmpty()) {
                unpinned.store(context.next().resolve(UNPINNED));
            }
            Repository locator = repositories.get(DiscoverySources.NAME);
            if (locator != null) {
                SequencedProperties archives = new SequencedProperties();
                for (Map.Entry<String, Resolver.Resolved> entry : materialized.entrySet()) {
                    String key = entry.getKey();
                    int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
                    if (first < 0 || second < 0 || entry.getValue().internal()) {
                        continue;
                    }
                    String coordinate = key.substring(second + 1);
                    if (archives.getProperty(coordinate) == null) {
                        RepositoryItem item = locator.fetch(executor, coordinate).orElse(null);
                        if (item != null) {
                            try (InputStream inputStream = item.toInputStream()) {
                                archives.setProperty(coordinate,
                                        new String(inputStream.readAllBytes(), StandardCharsets.UTF_8));
                            }
                        }
                    }
                }
                if (!archives.isEmpty()) {
                    archives.store(context.next().resolve(ARCHIVES));
                }
            }
            SequencedProperties resolution = new SequencedProperties();
            for (Map.Entry<String, Path> entry : sources.entrySet()) {
                fetched.entrySet().stream()
                        .filter(candidate -> candidate.getValue().file().filter(entry.getValue()::equals).isPresent())
                        .findFirst()
                        .ifPresent(candidate -> resolution.setProperty("placed/" + entry.getKey(), candidate.getKey()));
            }
            if (resolution.size() == sources.size()) {
                for (Map.Entry<String, RepositoryItem> entry : fetched.entrySet()) {
                    resolution.setProperty("fetched/" + entry.getKey(), fingerprint(entry.getValue()));
                }
                resolution.setProperty("inputs",
                        key(resolution,
                                requires,
                                new TreeMap<>(categories),
                                new TreeMap<>(aliases),
                                versions,
                                moduleAliases,
                                moduleOverrides,
                                bomTokens,
                                exclusions,
                                managedExclusions,
                                variants));
                resolution.store(context.next().resolve(RESOLUTION));
            }
            Set<String> listed = new HashSet<>(), dropped = new HashSet<>();
            for (String key : index.stringPropertyNames()) {
                String value = index.getProperty(key);
                int space = value.indexOf(' ');
                (handed.contains(key) ? dropped : listed).add(space < 0 ? value : value.substring(0, space));
            }
            handed.forEach(index::remove);
            dropped.removeAll(listed);
            for (String relative : dropped) {
                Files.deleteIfExists(context.next().resolve(relative));
            }
            index.store(context.next().resolve(DEPENDENCIES));
            graph.store(context.next().resolve(GRAPH));
            licenses.store(context.next().resolve(LICENSES));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static String key(SequencedProperties resolution,
                                  SequencedMap<String, SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>>> requires,
                                  Object... inputs) {
            Set<String> produced = new HashSet<>();
            for (String key : resolution.stringPropertyNames()) {
                if (key.startsWith("placed/") || key.startsWith("fetched/")) {
                    String location = key.substring(key.indexOf('/') + 1);
                    produced.add(location.indexOf('#') < 0 ? location : location.substring(0, location.indexOf('#')));
                }
            }
            SequencedMap<String, String> declared = new LinkedHashMap<>();
            requires.forEach((group, scopes) -> scopes.forEach((scope, repositories) -> repositories.forEach(
                    (repository, coordinates) -> coordinates.forEach((coordinate, value) -> declared.put(
                            group + "/" + scope + "/" + repository + "/" + coordinate,
                            !value.isEmpty() && Stream.of(repository, Resolver.base(repository))
                                    .map(prefix -> prefix + "/" + coordinate)
                                    .anyMatch(location -> produced.stream().anyMatch(candidate ->
                                            candidate.equals(location)
                                                    || candidate.startsWith(location + "/")
                                                    || candidate.startsWith(location + ":")))
                                    ? "internal"
                                    : value)))));
            return digest(List.of(declared, List.of(inputs)));
        }

        private static void companions(SequencedMap<String, BuildStepArgument> arguments,
                                       SequencedMap<String, Path> internals) throws IOException {
            for (Map.Entry<String, Path> internal : internals.entrySet()) {
                Path jar = internal.getValue();
                for (BuildStepArgument argument : arguments.values()) {
                    Path abi = argument.removed()
                            ? null
                            : argument.folder().resolve(Abi.FOLDER
                                    + BuildExecutorModule.encode(internal.getKey())
                                    + ".properties");
                    if (abi != null && Files.isRegularFile(abi)) {
                        Files.copy(abi,
                                jar.resolveSibling(Abi.companion(jar.getFileName().toString())),
                                StandardCopyOption.REPLACE_EXISTING);
                        break;
                    }
                }
            }
        }

        private static String fingerprint(RepositoryItem item) throws IOException {
            Path file = item.file().orElse(null);
            SequencedMap<String, Object> description = new LinkedHashMap<>();
            if (file == null) {
                try (InputStream inputStream = item.toInputStream()) {
                    description.put("content", new String(inputStream.readAllBytes(), StandardCharsets.ISO_8859_1));
                }
                return digest(description);
            }
            description.put("name", file.getFileName().toString());
            SequencedMap<String, String> entries = new TreeMap<>();
            SequencedSet<String> packages = new TreeSet<>();
            try (FileSystem archive = Files.isDirectory(file) ? null : FileSystems.newFileSystem(file);
                 Stream<Path> files = Files.walk(archive == null ? file : archive.getPath("/"))) {
                Path root = archive == null ? file : archive.getPath("/");
                for (Path candidate : files.filter(Files::isRegularFile).toList()) {
                    String name = root.relativize(candidate).toString().replace(File.separatorChar, '/');
                    if (name.equals(JarFile.MANIFEST_NAME)
                            || name.equals("module-info.class")
                            || name.startsWith("META-INF/versions/") && name.endsWith("/module-info.class")) {
                        entries.put(name, new String(Files.readAllBytes(candidate), StandardCharsets.ISO_8859_1));
                    } else if (!name.startsWith("META-INF/")) {
                        packages.add(name.lastIndexOf('/') < 0 ? "" : name.substring(0, name.lastIndexOf('/')));
                    }
                }
                description.put("licenses", License.ofArchive(root).stream().map(License::toString).toList());
            } catch (ZipException | ProviderNotFoundException _) {
                entries.put("", new String(Files.readAllBytes(file), StandardCharsets.ISO_8859_1));
            }
            description.put("entries", entries);
            description.put("packages", packages);
            Path declaration = PathPlacement.declaration(file);
            if (Files.isRegularFile(declaration)) {
                description.put("declaration", Files.readString(declaration));
            }
            return digest(description);
        }

        private static String digest(Object value) {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            update(digest, value);
            return HexFormat.of().formatHex(digest.digest());
        }

        private static void update(MessageDigest digest, Object value) {
            switch (value) {
                case String text -> {
                    byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                    digest.update(ByteBuffer.allocate(5).put((byte) 's').putInt(bytes.length).array());
                    digest.update(bytes);
                }
                case Map<?, ?> map -> {
                    digest.update(ByteBuffer.allocate(5).put((byte) 'm').putInt(map.size()).array());
                    map.forEach((key, entry) -> {
                        update(digest, key);
                        update(digest, entry);
                    });
                }
                case Collection<?> collection -> {
                    digest.update(ByteBuffer.allocate(5).put((byte) 'c').putInt(collection.size()).array());
                    collection.forEach(element -> update(digest, element));
                }
                default -> throw new IllegalArgumentException("Cannot digest " + value.getClass().getName()
                        + " as part of a resolution key: expected a string, a map or a collection");
            }
        }
    }

    private record Pinned(boolean strict) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return arguments.values().stream().anyMatch(argument -> argument.hasChanged(Path.of(UNPINNED)));
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            for (BuildStepArgument argument : strict ? arguments.values() : List.<BuildStepArgument>of()) {
                Path file = argument.removed() ? null : argument.folder().resolve(UNPINNED);
                if (file == null || !Files.exists(file)) {
                    continue;
                }
                SequencedProperties unpinned = SequencedProperties.ofFiles(file);
                Optional<String> bom = unpinned.stringPropertyNames().stream()
                        .filter(key -> key.startsWith("bom/"))
                        .findFirst();
                if (bom.isPresent()) {
                    throw new IllegalStateException("No checksum pinned for BOM "
                            + bom.get().substring("bom/".length())
                            + " (strict pinning is enabled)");
                }
                Optional<String> dependency = unpinned.stringPropertyNames().stream().findFirst();
                if (dependency.isPresent()) {
                    throw new IllegalStateException("No checksum pinned for "
                            + dependency.get()
                            + " (strict pinning is enabled)"
                            + unpinned.getProperty(dependency.get()));
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static final String LOCAL = "a local @jenesis.alias declaration";

    private record Alias(String token, String origin) {
    }

    private record Overridden(SequencedSet<String> carriers, String origin) {
    }

    private static void merge(SequencedMap<String, Overridden> declared,
                              String module,
                              SequencedSet<String> carriers,
                              String origin) {
        Overridden previous = declared.putIfAbsent(module, new Overridden(carriers, origin));
        if (previous != null && !previous.carriers().equals(carriers)) {
            throw new IllegalArgumentException("Module override "
                    + module
                    + " is declared for "
                    + previous.carriers()
                    + " by "
                    + previous.origin()
                    + " and for "
                    + carriers
                    + " by "
                    + origin);
        }
    }

    private static byte[] carrying(String module, SequencedSet<String> carriers) {
        return ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of(module), builder -> {
            builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
            carriers.forEach(carrier -> builder.requires(
                    ModuleDesc.of(carrier), ClassFile.ACC_TRANSITIVE, null));
        }));
    }

    private static void merge(SequencedMap<String, Alias> declared,
                              String alias,
                              String token,
                              String origin) {
        Alias previous = declared.putIfAbsent(alias, new Alias(token, origin));
        if (previous != null && !previous.token().equals(token)) {
            throw new IllegalArgumentException("Module alias "
                    + alias
                    + " is declared for "
                    + previous.token()
                    + " by "
                    + previous.origin()
                    + " and for "
                    + token
                    + " by "
                    + origin);
        }
    }

    private static SequencedMap<String, String> rename(SequencedMap<String, Path> placed,
                                                       SequencedMap<String, SequencedSet<String>> grouped,
                                                       SequencedMap<String, Alias> declared,
                                                       SequencedMap<String, String> modules,
                                                       SequencedMap<String, Boolean> explicit,
                                                       PathPlacement pathPlacement,
                                                       Path libs,
                                                       Consumer<String> printing,
                                                       Palette palette) throws IOException {
        SequencedMap<String, String> coordinates = new LinkedHashMap<>();
        for (String dependency : placed.sequencedKeySet()) {
            int first = dependency.indexOf('/'), last = dependency.lastIndexOf('/');
            if (first > 0 && last > first) {
                coordinates.putIfAbsent(dependency.substring(first + 1, last), dependency);
            }
        }
        for (Map.Entry<String, Path> entry : placed.entrySet()) {
            if (!explicit.getOrDefault(entry.getKey(), true) || Files.isDirectory(entry.getValue())) {
                continue;
            }
            String origin = entry.getValue().getFileName().toString();
            for (Map.Entry<String, String> declaration : PathPlacement.aliases(entry.getValue()).entrySet()) {
                if (coordinates.containsKey(declaration.getValue())) {
                    merge(declared, declaration.getKey(), declaration.getValue(), origin);
                }
            }
        }
        SequencedMap<String, String> aliased = new LinkedHashMap<>(), owners = new LinkedHashMap<>();
        for (Map.Entry<String, Alias> entry : declared.entrySet()) {
            String alias = entry.getKey(), token = entry.getValue().token();
            String coordinate = coordinates.get(token);
            if (coordinate == null) {
                continue;
            }
            String module = modules.get(alias);
            ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(placed.get(coordinate));
            if (descriptor != null && descriptor.name().equals(alias)) {
                if (printing != null) {
                    printing.accept("%s%-11s%s %s already declares %s, so the alias declared by %s"
                                    .formatted(palette.warning(),
                                            "[ALIAS]",
                                            palette.reset(),
                                            coordinate,
                                            alias,
                                            entry.getValue().origin())
                            + " says nothing the jar does not");
                }
                continue;
            }
            if (module != null) {
                throw new IllegalArgumentException("Module alias "
                        + alias
                        + " collides with module "
                        + alias
                        + " resolved from "
                        + module
                        + " - require it directly");
            }
            if (descriptor != null) {
                String version = coordinate.substring(coordinate.lastIndexOf('/') + 1);
                throw new IllegalArgumentException("Target of module alias "
                        + alias
                        + ", "
                        + token
                        + " "
                        + version
                        + ", is already the "
                        + (descriptor.isAutomatic() ? "automatic" : "named")
                        + " module "
                        + descriptor.name()
                        + " - require "
                        + descriptor.name()
                        + " instead of aliasing "
                        + token
                        + ", or, if a version of "
                        + token
                        + " other than "
                        + version
                        + " is meant, pin that one, since the alias names no version of its own");
            }
            String previous = owners.putIfAbsent(coordinate, alias);
            if (previous != null) {
                throw new IllegalArgumentException(coordinate
                        + " is aliased as both "
                        + previous
                        + " and "
                        + alias
                        + " - a jar can carry only one module name");
            }
            aliased.put(alias, coordinate);
        }
        SequencedMap<String, String> names = new LinkedHashMap<>();
        aliased.forEach((alias, coordinate) -> names.put(coordinate, alias));
        SequencedMap<String, Path> ordered = new LinkedHashMap<>();
        names.keySet().forEach(dependency -> ordered.put(dependency, placed.get(dependency)));
        ordered.putAll(placed);
        SequencedMap<String, Claim> claims = new LinkedHashMap<>(), carriers = new LinkedHashMap<>();
        SequencedMap<Path, Path> renamed = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : ordered.entrySet()) {
            String dependency = entry.getKey();
            Path source = entry.getValue();
            if (Files.isDirectory(source)) {
                continue;
            }
            Path taken = renamed.get(source);
            if (taken != null) {
                placed.put(dependency, taken);
                continue;
            }
            String coordinate = dependency.substring(dependency.indexOf('/') + 1);
            String alias = names.get(dependency);
            String module;
            if (alias != null) {
                module = alias;
            } else {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(source);
                module = descriptor == null ? null : descriptor.name();
            }
            List<String> modular = pathPlacement == PathPlacement.CLASS_PATH
                    ? List.of()
                    : grouped.getOrDefault(dependency, Collections.emptyNavigableSet()).stream()
                            .filter(group -> !PROCESSOR_PATH.contains(group))
                            .toList();
            if (module == null && pathPlacement == PathPlacement.MODULE_PATH && !modular.isEmpty()) {
                throw new IllegalArgumentException(dependency
                        + " declares no module name, neither by a module-info.class nor by an Automatic-Module-Name,"
                        + " but every jar of group "
                        + String.join(" and ", modular)
                        + " loads as a module of its own layer - name it with a @jenesis.alias <module> "
                        + coordinate.substring(0, coordinate.lastIndexOf('/'))
                        + " line in the module-info.java that requires it, or drop it with @jenesis.exclude");
            }
            String name = module == null
                    ? PathPlacement.fileName(coordinate)
                    : PathPlacement.fileName(coordinate, module, alias == null);
            Claim named = claims.get(name);
            if (module != null && named != null && !named.file().equals(source)) {
                name = PathPlacement.fileName(coordinate);
            }
            if (module != null) {
                for (String group : modular) {
                    Claim carrier = carriers.putIfAbsent(group + "/" + module, new Claim(dependency, source));
                    if (carrier != null && !carrier.file().equals(source)) {
                        throw new IllegalArgumentException(carrier.dependency()
                                + " and "
                                + dependency
                                + " both carry module "
                                + module
                                + " in group "
                                + group
                                + " - a module path resolves whichever of the two comes first, so "
                                + (carrier.dependency().substring(0, carrier.dependency().lastIndexOf('/'))
                                        .equals(dependency.substring(0, dependency.lastIndexOf('/')))
                                        ? "settle on one version: a <dependencyManagement> entry in pom.xml"
                                                + " or a @jenesis.pin "
                                                + group
                                                + "/"
                                                + dependency.substring(0, dependency.lastIndexOf('/'))
                                                + " <version> line in module-info.java manages it"
                                        : "drop one: an <exclusions> entry in pom.xml or @jenesis.exclude in"
                                                + " module-info.java"));
                    }
                }
            }
            Claim previous = claims.putIfAbsent(name, new Claim(dependency, source));
            if (previous != null && !previous.file().equals(source)) {
                throw new IllegalArgumentException(previous.dependency()
                        + " and "
                        + dependency
                        + " are both materialized as "
                        + name
                        + " - two artifacts cannot share one file name");
            }
            Path target = libs.resolve(name);
            if (!source.equals(target)) {
                if (source.startsWith(libs)) {
                    Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.deleteIfExists(target);
                    BuildStep.linkOrCopy(target, source);
                }
                placed.put(dependency, target);
            }
            renamed.put(source, target);
            if (alias != null) {
                PathPlacement.aliased(target, alias, "Target " + dependency);
                Files.writeString(PathPlacement.declaration(target), alias);
            }
        }
        return aliased;
    }

    private record Claim(String dependency, Path file) {
    }

    private static String managing(
            SequencedMap<String, SequencedMap<String, SequencedMap<String, String>>> managed,
            String key) {
        int lastSlash = key.lastIndexOf('/');
        if (lastSlash < 1) {
            return "";
        }
        String coordinate = key.substring(0, lastSlash), version = key.substring(lastSlash + 1);
        for (SequencedMap<String, SequencedMap<String, String>> byRepository : managed.values()) {
            for (Map.Entry<String, SequencedMap<String, String>> repository : byRepository.entrySet()) {
                for (Map.Entry<String, String> entry : repository.getValue().entrySet()) {
                    if (!coordinate.equals(repository.getKey() + "/" + entry.getKey())) {
                        continue;
                    }
                    String value = entry.getValue();
                    int space = value.indexOf(' ');
                    if (!version.equals(space < 0 ? value : value.substring(0, space))) {
                        continue;
                    }
                    return ". A bill of materials manages it at this version and records no checksum,"
                            + " so no pin covers what it resolves to: record the checksum beside the"
                            + " version in the bill of materials, or stop managing the coordinate there";
                }
            }
        }
        return "";
    }

    private static Path index(Path folder) {
        Path modular = folder.resolve(MODULAR);
        if (Files.exists(modular)) {
            return modular;
        }
        Path dependencies = folder.resolve(BuildStep.DEPENDENCIES);
        return Files.exists(dependencies) ? dependencies : null;
    }

    public static Map<String, String> aliases(List<Path> folders) throws IOException {
        Map<String, String> aliases = new LinkedHashMap<>(DEFAULT_ALIASES);
        for (Path folder : folders) {
            Path file = folder.resolve(SPDX);
            if (Files.isRegularFile(file)) {
                SequencedProperties properties = SequencedProperties.ofFiles(file);
                for (String key : properties.stringPropertyNames()) {
                    if (key.startsWith("alias/")) {
                        String name = key.substring("alias/".length()).toLowerCase(Locale.ROOT).trim();
                        String value = properties.getProperty(key).trim();
                        if (value.isEmpty()) {
                            aliases.remove(name);
                        } else {
                            aliases.put(name, value);
                        }
                    }
                }
            }
        }
        return aliases;
    }

    public static List<Path> select(Path folder, String group, String scope) throws IOException {
        Path file = index(folder);
        if (file == null) {
            return List.of();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedSet<Path> selected = new LinkedHashSet<>();
        String prefix = group + "/" + scope + "/";
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(prefix)) {
                String value = properties.getProperty(key);
                int space = value.indexOf(' ');
                Path jar = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
                if (Files.exists(jar)) {
                    selected.add(jar);
                }
            }
        }
        return new ArrayList<>(selected);
    }

    public static List<Path> paths(SequencedMap<String, BuildStepArgument> arguments, String group, String scope)
            throws IOException {
        SequencedSet<Path> paths = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (!argument.removed()) {
                paths.addAll(select(argument.folder(), group, scope));
            }
        }
        return List.copyOf(paths);
    }

    public static Path runtime(Path folder, String key) throws IOException {
        Path file = index(folder);
        if (file == null) {
            return null;
        }
        int first = key.indexOf('/'), second = key.indexOf('/', first + 1);
        String prefix = key.substring(0, first) + "/runtime" + key.substring(second);
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String candidate : properties.stringPropertyNames()) {
            if (candidate.equals(prefix)
                    || candidate.startsWith(prefix + "/") && candidate.indexOf('/', prefix.length() + 1) < 0) {
                String value = properties.getProperty(candidate);
                int space = value.indexOf(' ');
                Path jar = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
                if (Files.exists(jar)) {
                    return jar;
                }
            }
        }
        return null;
    }

    public static List<Path> all(Path folder) throws IOException {
        Path file = index(folder);
        if (file == null) {
            return List.of();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedSet<Path> selected = new LinkedHashSet<>();
        for (String key : properties.stringPropertyNames()) {
            String value = properties.getProperty(key);
            int space = value.indexOf(' ');
            Path jar = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
            if (Files.exists(jar)) {
                selected.add(jar);
            }
        }
        return new ArrayList<>(selected);
    }

    public static SequencedMap<String, Path> internal(Path folder) throws IOException {
        Path file = index(folder), graphFile = folder.resolve(GRAPH);
        if (file == null || !Files.exists(graphFile)) {
            return Collections.emptyNavigableMap();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedMap<String, Path> selected = new LinkedHashMap<>();
        for (Map.Entry<String, Resolver.Resolution> entry : graph(List.of(graphFile), List.of()).entrySet()) {
            for (Map.Entry<String, Resolver.Vertex> vertex : entry.getValue().vertices().entrySet()) {
                if (!vertex.getValue().internal() || vertex.getValue().resolvedVersion() == null) {
                    continue;
                }
                String coordinate = vertex.getKey() + "/" + vertex.getValue().resolvedVersion();
                String value = properties.getProperty(entry.getKey() + "/" + coordinate);
                if (value == null) {
                    continue;
                }
                int space = value.indexOf(' ');
                Path jar = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
                if (Files.exists(jar)) {
                    selected.putIfAbsent(coordinate, jar);
                }
            }
        }
        return selected;
    }

    private static final Map<String, String> DEFAULT_ALIASES = Map.ofEntries(
            Map.entry("apache.org/licenses/license-2.0", "Apache-2.0"),
            Map.entry("opensource.org/licenses/apache-2.0", "Apache-2.0"),
            Map.entry("opensource.org/licenses/mit", "MIT"),
            Map.entry("opensource.org/licenses/mit-license", "MIT"),
            Map.entry("opensource.org/license/mit", "MIT"),
            Map.entry("opensource.org/licenses/bsd-2-clause", "BSD-2-Clause"),
            Map.entry("opensource.org/licenses/bsd-3-clause", "BSD-3-Clause"),
            Map.entry("opensource.org/licenses/bsd-license", "BSD-3-Clause"),
            Map.entry("eclipse.org/legal/epl-v10", "EPL-1.0"),
            Map.entry("eclipse.org/legal/epl-2.0", "EPL-2.0"),
            Map.entry("eclipse.org/legal/epl-v20", "EPL-2.0"),
            Map.entry("mozilla.org/mpl/2.0", "MPL-2.0"),
            Map.entry("gnu.org/licenses/lgpl-2.1", "LGPL-2.1-only"),
            Map.entry("gnu.org/licenses/lgpl-3.0", "LGPL-3.0-only"),
            Map.entry("gnu.org/licenses/gpl-2.0", "GPL-2.0-only"),
            Map.entry("gnu.org/licenses/gpl-3.0", "GPL-3.0-only"),
            Map.entry("apache license 2.0", "Apache-2.0"),
            Map.entry("apache license, version 2.0", "Apache-2.0"),
            Map.entry("apache license version 2.0", "Apache-2.0"),
            Map.entry("apache software license - version 2.0", "Apache-2.0"),
            Map.entry("the apache software license, version 2.0", "Apache-2.0"),
            Map.entry("the apache license, version 2.0", "Apache-2.0"),
            Map.entry("apache 2.0", "Apache-2.0"),
            Map.entry("apache-2.0", "Apache-2.0"),
            Map.entry("asl 2.0", "Apache-2.0"),
            Map.entry("apache license", "Apache-2.0"),
            Map.entry("mit license", "MIT"),
            Map.entry("the mit license", "MIT"),
            Map.entry("mit", "MIT"),
            Map.entry("mit-0", "MIT-0"),
            Map.entry("bsd license", "BSD-2-Clause"),
            Map.entry("the bsd license", "BSD-2-Clause"),
            Map.entry("bsd-2-clause", "BSD-2-Clause"),
            Map.entry("bsd 2-clause license", "BSD-2-Clause"),
            Map.entry("bsd-3-clause", "BSD-3-Clause"),
            Map.entry("bsd 3-clause license", "BSD-3-Clause"),
            Map.entry("the bsd 3-clause license", "BSD-3-Clause"),
            Map.entry("new bsd license", "BSD-3-Clause"),
            Map.entry("revised bsd license", "BSD-3-Clause"),
            Map.entry("eclipse distribution license - v 1.0", "BSD-3-Clause"),
            Map.entry("eclipse distribution license (new bsd license)", "BSD-3-Clause"),
            Map.entry("eclipse public license - v 1.0", "EPL-1.0"),
            Map.entry("eclipse public license 1.0", "EPL-1.0"),
            Map.entry("eclipse public license v1.0", "EPL-1.0"),
            Map.entry("epl-1.0", "EPL-1.0"),
            Map.entry("eclipse public license - v 2.0", "EPL-2.0"),
            Map.entry("eclipse public license 2.0", "EPL-2.0"),
            Map.entry("eclipse public license v2.0", "EPL-2.0"),
            Map.entry("eclipse public license", "EPL-2.0"),
            Map.entry("epl-2.0", "EPL-2.0"),
            Map.entry("gnu lesser general public license", "LGPL-2.1-or-later"),
            Map.entry("lesser general public license", "LGPL-2.1-or-later"),
            Map.entry("gnu lesser general public license, version 2.1", "LGPL-2.1-only"),
            Map.entry("lgpl-2.1", "LGPL-2.1-only"),
            Map.entry("lgpl 2.1", "LGPL-2.1-only"),
            Map.entry("lgpl-3.0", "LGPL-3.0-only"),
            Map.entry("lgpl", "LGPL-2.1-or-later"),
            Map.entry("gnu general public license, version 2", "GPL-2.0-only"),
            Map.entry("gnu general public license v2.0", "GPL-2.0-only"),
            Map.entry("gpl-2.0", "GPL-2.0-only"),
            Map.entry("gnu general public license, version 2 with the classpath exception", "GPL-2.0-with-classpath-exception"),
            Map.entry("gpl-2.0-with-classpath-exception", "GPL-2.0-with-classpath-exception"),
            Map.entry("gnu general public license, version 3", "GPL-3.0-only"),
            Map.entry("gpl-3.0", "GPL-3.0-only"),
            Map.entry("gnu affero general public license", "AGPL-3.0-or-later"),
            Map.entry("affero general public license", "AGPL-3.0-or-later"),
            Map.entry("agpl-3.0", "AGPL-3.0-only"),
            Map.entry("mozilla public license 2.0", "MPL-2.0"),
            Map.entry("mozilla public license, version 2.0", "MPL-2.0"),
            Map.entry("mpl 2.0", "MPL-2.0"),
            Map.entry("mpl-2.0", "MPL-2.0"),
            Map.entry("mozilla public license 1.1", "MPL-1.1"),
            Map.entry("common development and distribution license 1.0", "CDDL-1.0"),
            Map.entry("cddl 1.0", "CDDL-1.0"),
            Map.entry("cddl-1.0", "CDDL-1.0"),
            Map.entry("common development and distribution license 1.1", "CDDL-1.1"),
            Map.entry("cddl 1.1", "CDDL-1.1"),
            Map.entry("cddl-1.1", "CDDL-1.1"),
            Map.entry("isc license", "ISC"),
            Map.entry("isc", "ISC"),
            Map.entry("boost software license 1.0", "BSL-1.0"),
            Map.entry("boost software license", "BSL-1.0"),
            Map.entry("bsl-1.0", "BSL-1.0"),
            Map.entry("the unlicense", "Unlicense"),
            Map.entry("unlicense", "Unlicense"),
            Map.entry("cc0 1.0 universal", "CC0-1.0"),
            Map.entry("cc0", "CC0-1.0"),
            Map.entry("public domain", "CC0-1.0"),
            Map.entry("the zlib/libpng license", "Zlib"),
            Map.entry("zlib", "Zlib"),
            Map.entry("python software foundation license", "PSF-2.0"),
            Map.entry("wtfpl", "WTFPL"));
    private static final Map<String, String> DEFAULT_CATEGORIES = Map.ofEntries(
            Map.entry("Apache-2.0", "permissive"),
            Map.entry("MIT", "permissive"),
            Map.entry("MIT-0", "permissive"),
            Map.entry("BSD-2-Clause", "permissive"),
            Map.entry("BSD-3-Clause", "permissive"),
            Map.entry("ISC", "permissive"),
            Map.entry("BSL-1.0", "permissive"),
            Map.entry("Zlib", "permissive"),
            Map.entry("PSF-2.0", "permissive"),
            Map.entry("Unlicense", "public-domain"),
            Map.entry("CC0-1.0", "public-domain"),
            Map.entry("WTFPL", "public-domain"),
            Map.entry("EPL-1.0", "weak-copyleft"),
            Map.entry("EPL-2.0", "weak-copyleft"),
            Map.entry("MPL-1.1", "weak-copyleft"),
            Map.entry("MPL-2.0", "weak-copyleft"),
            Map.entry("CDDL-1.0", "weak-copyleft"),
            Map.entry("CDDL-1.1", "weak-copyleft"),
            Map.entry("LGPL-2.1-only", "weak-copyleft"),
            Map.entry("LGPL-2.1-or-later", "weak-copyleft"),
            Map.entry("LGPL-3.0-only", "weak-copyleft"),
            Map.entry("LGPL-3.0-or-later", "weak-copyleft"),
            Map.entry("GPL-2.0-with-classpath-exception", "weak-copyleft"),
            Map.entry("GPL-2.0-only", "strong-copyleft"),
            Map.entry("GPL-2.0-or-later", "strong-copyleft"),
            Map.entry("GPL-3.0-only", "strong-copyleft"),
            Map.entry("GPL-3.0-or-later", "strong-copyleft"),
            Map.entry("AGPL-3.0-only", "network-copyleft"),
            Map.entry("AGPL-3.0-or-later", "network-copyleft"));

    public static SequencedMap<String, String> layers(Iterable<Path> graphFiles) throws IOException {
        SequencedMap<String, String> layers = new LinkedHashMap<>();
        for (Path file : graphFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String key : properties.stringPropertyNames()) {
                if (key.startsWith("layer/")) {
                    layers.putIfAbsent(key.substring("layer/".length()), properties.getProperty(key));
                }
            }
        }
        return layers;
    }

    public static SequencedMap<String, Resolver.Resolution> graph(Iterable<Path> graphFiles,
                                                                  Iterable<Path> licenseFiles) throws IOException {
        SequencedMap<String, SequencedMap<Integer, Map<String, String>>> licenseEntries = new LinkedHashMap<>();
        for (Path file : licenseFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String key : properties.stringPropertyNames()) {
                int last = key.lastIndexOf('#');
                int prior = last < 1 ? -1 : key.lastIndexOf('#', last - 1);
                if (prior < 1) {
                    continue;
                }
                int index;
                try {
                    index = Integer.parseInt(key.substring(prior + 1, last));
                } catch (NumberFormatException _) {
                    continue;
                }
                licenseEntries.computeIfAbsent(key.substring(0, prior), _ -> new TreeMap<>())
                        .computeIfAbsent(index, _ -> new HashMap<>())
                        .put(key.substring(last + 1), properties.getProperty(key));
            }
        }
        SequencedMap<String, List<License>> licenses = new LinkedHashMap<>();
        licenseEntries.forEach((coordinate, byIndex) -> licenses.put(coordinate,
                byIndex.values().stream()
                        .map(entry -> new License(entry.get("id"), entry.get("category"), entry.get("name"), entry.get("url")))
                        .toList()));
        SequencedMap<String, SequencedSet<Resolver.Edge>> edges = new LinkedHashMap<>();
        SequencedMap<String, SequencedMap<String, Resolver.Vertex>> vertices = new LinkedHashMap<>();
        for (Path file : graphFiles) {
            if (!Files.isRegularFile(file)) {
                continue;
            }
            SequencedProperties properties = SequencedProperties.ofFiles(file);
            for (String key : properties.stringPropertyNames()) {
                String value = properties.getProperty(key);
                if (key.startsWith("edge/")) {
                    String[] parts = value.split("\t", -1);
                    if (parts.length != 8) {
                        continue;
                    }
                    edges.computeIfAbsent(parts[0] + "/" + parts[1], _ -> new LinkedHashSet<>())
                            .add(new Resolver.Edge(
                                    parts[6].isEmpty() ? null : parts[6],
                                    parts[7],
                                    parts[5].isEmpty() ? null : parts[5],
                                    parts[4].isEmpty() ? null : parts[4],
                                    Boolean.parseBoolean(parts[3])));
                } else if (key.startsWith("vertex/")) {
                    String rest = key.substring("vertex/".length());
                    int first = rest.indexOf('/');
                    int second = first < 0 ? -1 : rest.indexOf('/', first + 1);
                    if (second < 0) {
                        continue;
                    }
                    String groupScope = rest.substring(0, second);
                    String coordinate = rest.substring(second + 1);
                    String[] parts = value.split("\t", -1);
                    String resolvedVersion = parts[0].isEmpty() ? null : parts[0];
                    List<License> declared = resolvedVersion == null
                            ? List.of()
                            : licenses.getOrDefault(coordinate + "/" + resolvedVersion, List.of());
                    vertices.computeIfAbsent(groupScope, _ -> new LinkedHashMap<>())
                            .putIfAbsent(coordinate, new Resolver.Vertex(
                                    resolvedVersion,
                                    parts.length > 1 && !parts[1].isEmpty() ? parts[1] : null,
                                    parts.length > 2 && Boolean.parseBoolean(parts[2]),
                                    parts.length > 3 && Boolean.parseBoolean(parts[3]),
                                    declared));
                }
            }
        }
        SequencedMap<String, Resolver.Resolution> result = new LinkedHashMap<>();
        SequencedSet<String> groupScopes = new LinkedHashSet<>(edges.sequencedKeySet());
        groupScopes.addAll(vertices.sequencedKeySet());
        for (String groupScope : groupScopes) {
            result.put(groupScope, new Resolver.Resolution(
                    Collections.emptyNavigableMap(),
                    new ArrayList<>(edges.getOrDefault(groupScope, Collections.emptyNavigableSet())),
                    vertices.getOrDefault(groupScope, Collections.emptyNavigableMap())));
        }
        return result;
    }

    private static String[] split(String key) {
        int first = key.indexOf('/');
        if (first < 1) {
            return null;
        }
        int second = key.indexOf('/', first + 1);
        if (second < 0) {
            return null;
        }
        int third = key.indexOf('/', second + 1);
        if (third < 0) {
            return null;
        }
        return new String[] {
                key.substring(0, first),
                key.substring(first + 1, second),
                key.substring(second + 1, third),
                key.substring(third + 1)};
    }
}

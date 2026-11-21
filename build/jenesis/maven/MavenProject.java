package build.jenesis.maven;

import module java.base;
import build.jenesis.Pinning;
import module java.xml;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.PathPlacement;
import build.jenesis.Platform;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.MultiProjectDependencies;
import build.jenesis.project.MultiProjectModule;
import build.jenesis.project.ProjectModule;
import build.jenesis.project.ProjectPlugins;
import build.jenesis.step.Assign;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Group;
import build.jenesis.step.Inventory;
import build.jenesis.step.Javac;
import build.jenesis.step.Versions;
import java.util.jar.Attributes;

import static build.jenesis.BuildStep.IDENTITY;
import static build.jenesis.project.MultiProjectModule.ARTIFACTS;
import static build.jenesis.project.MultiProjectModule.ASSIGN;
import static build.jenesis.project.MultiProjectModule.COORDINATES;
import static build.jenesis.project.MultiProjectModule.DEPENDENCIES;
import static build.jenesis.project.MultiProjectModule.MANIFESTS;
import static build.jenesis.project.MultiProjectModule.MODULE;
import static build.jenesis.project.MultiProjectModule.PREPARE;
import static build.jenesis.project.MultiProjectModule.PRODUCE;
import static build.jenesis.project.MultiProjectModule.SOURCES;
import static java.util.Objects.requireNonNull;

public class MavenProject implements BuildExecutorModule {

    public static final String POM = "pom/", MAVEN = "maven/", BOM = "bom/";
    public static final String EXPRESSIONS = "expressions.properties", SCOPES = "scopes.properties";
    private static final String SCAN = "scan", POM_METADATA = "metadata.", RELOCATION = "relocation.", BOMS = "boms",
            POMS = "poms", SKIPPED = "skipped.properties", INDEX = "index.properties";
    private static final Set<String> JARS = Set.of("jar", "bundle"), AGGREGATES = Set.of("pom", "bom");
    private static final String SIBLING_MODULE_PREFIX = MultiProjectModule.MODULE + "-";
    private static final Set<String> COMMANDED = Set.of("version", "scm.tag", "scm.revision", "scm.tree");
    private static final Pattern UNRESOLVED = Pattern.compile("\\$\\{([^}]+)}");
    private static final Function<List<Path>, SequencedSet<Path>> OWN_CONFIGURATIONS =
            locals -> Collections.unmodifiableSequencedSet(new LinkedHashSet<>(locals));

    private final Path root;
    private final String group;
    private final String prefix;
    private final MavenRepository repository;
    private final MavenResolver resolver;
    private final Platform platform;
    private final Consumer<String> printing;
    private final Palette palette;
    private final Function<List<Path>, SequencedSet<Path>> configurations;
    private final List<String> sources;

    public MavenProject(Path root,
                        String prefix,
                        MavenRepository repository,
                        MavenResolver resolver) {
        this(root,
                "main",
                prefix,
                repository,
                resolver,
                new Platform(),
                null,
                Palette.NONE,
                OWN_CONFIGURATIONS,
                List.of());
    }

    public static MavenProject ofEnvironment(Environment environment,
                                      Path root,
                                      String prefix,
                                      MavenRepository repository,
                                      MavenResolver resolver) {
        List<String> sources = environment.entries("maven.sources");
        return new MavenProject(root, prefix, repository, resolver)
                .sources(sources == null ? List.of() : sources)
                .platform(Platform.ofEnvironment(environment))
                .printing(environment.flag("print.progress", true) ? environment.out() : null,
                        Palette.ofEnvironment(environment));
    }

    private MavenProject(Path root,
                         String group,
                         String prefix,
                         MavenRepository repository,
                         MavenResolver resolver,
                         Platform platform,
                         Consumer<String> printing,
                         Palette palette,
                         Function<List<Path>, SequencedSet<Path>> configurations,
                         List<String> sources) {
        this.root = root;
        this.group = group;
        this.prefix = prefix;
        this.repository = repository;
        this.resolver = resolver;
        this.platform = platform;
        this.printing = printing;
        this.palette = palette;
        this.configurations = configurations;
        this.sources = sources;
    }

    public MavenProject group(String group) {
        return new MavenProject(root, group, prefix, repository, resolver, platform, printing, palette, configurations, sources);
    }

    public MavenProject platform(Platform platform) {
        return new MavenProject(root, group, prefix, repository, resolver, platform, printing, palette, configurations, sources);
    }

    public MavenProject printing(Consumer<String> printing, Palette palette) {
        return new MavenProject(root, group, prefix, repository, resolver, platform, printing, palette, configurations, sources);
    }

    public MavenProject sources(List<String> sources) {
        for (String source : sources) {
            if (source.isBlank() || source.contains("/") || source.contains("\\") || source.equals("..")) {
                throw new IllegalArgumentException("jenesis.maven.sources names " + source + " - name a folder"
                        + " beside src/main/java and src/test/java, such as kotlin or groovy");
            }
        }
        return new MavenProject(root,
                group,
                prefix,
                repository,
                resolver,
                platform,
                printing,
                palette,
                configurations,
                List.copyOf(sources));
    }

    public MavenProject configurations(Function<List<Path>, SequencedSet<Path>> configurations) {
        return new MavenProject(root, group, prefix, repository, resolver, platform, printing, palette, configurations, sources);
    }

    public static BuildExecutorModule make(Environment environment,
                                           Path root,
                                           MultiProjectAssembler<? super MavenModuleDescriptor> assembler) {
        return make(environment,
                root,
                "main",
                "maven",
                Map.of("maven", MavenDefaultRepository.ofEnvironment(environment)),
                Map.of("maven", MavenPomResolver.ofEnvironment(environment)),
                null,
                Collections.emptyNavigableSet(),
                OWN_CONFIGURATIONS,
                assembler);
    }

    public static BuildExecutorModule make(Environment environment,
                                           Path root,
                                           String group,
                                           String prefix,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers,
                                           Pinning pinning,
                                           SequencedSet<Path> spdx,
                                           Function<List<Path>, SequencedSet<Path>> configurations,
                                           MultiProjectAssembler<? super MavenModuleDescriptor> assembler) {
        MavenRepository repository = MavenRepository.of(requireNonNull(repositories.get(prefix)));
        MavenResolver resolver = MavenResolver.of(resolvers.get(prefix));
        Dependencies dependencyModule = Dependencies.ofEnvironment(environment, repositories, resolvers);
        return new MultiProjectModule(MavenProject.ofEnvironment(environment, root, prefix, repository, resolver)
                        .group(group)
                        .configurations(configurations),
                identifier -> identifier.indexOf('/') < 0
                        ? Optional.empty()
                        : Optional.of(identifier.substring(0, identifier.indexOf('/'))),
                _ -> (name, dependencies, arguments) -> {
                    Path location = MultiProjectModule.location(root, arguments);
                    boolean test = MultiProjectModule.test(arguments);
                    SequencedSet<String> spdxInherited = new LinkedHashSet<>();
                    for (int index = 0; index < spdx.size(); index++) {
                        spdxInherited.add(BuildExecutorModule.PREVIOUS + MultiProjectModule.SPDX + "-" + index);
                    }
                    AssemblyDescriptor packaging = assembler.apply(
                            new MavenModuleDescriptor(name, dependencies.sequencedKeySet(), Collections.emptyNavigableSet(), spdxInherited, location, test),
                            repositories,
                            resolvers);
                    return packaging.nest(packaging.build(), PRODUCE, _ -> (buildExecutor, inherited) -> {
                    Map<String, Repository> mergedRepositories = Repository.prepend(repositories,
                            Repository.ofProperties(BuildStep.IDENTITY,
                                    inherited.entrySet().stream()
                                            .filter(entry ->
                                                    entry.getKey().startsWith(PREVIOUS + packaging.build() + "-")
                                                            && entry.getKey().endsWith("/" + ASSIGN))
                                            .map(Map.Entry::getValue)
                                            .toList(),
                                    (folder, file) -> folder.resolve(file).normalize().toUri(),
                                    null));
                    SequencedSet<String> spdxSources = new LinkedHashSet<>();
                    int spdxIndex = 0;
                    for (Path file : spdx) {
                        String source = MultiProjectModule.SPDX + "-" + spdxIndex++;
                        buildExecutor.addSource(source,
                                new Bind(Map.of(Path.of(""), Path.of(Dependencies.SPDX))),
                                file);
                        spdxSources.add(source);
                    }
                    SequencedMap<String, String> dependencyDeps = new LinkedHashMap<>();
                    for (String key : inherited.sequencedKeySet()) {
                        dependencyDeps.put(key, key);
                    }
                    for (String source : spdxSources) {
                        dependencyDeps.put(source, source);
                    }
                    buildExecutor.addModule(DEPENDENCIES, (depExec, depInherited) -> {
                        depExec.addStep(PREPARE,
                                new MultiProjectDependencies(
                                        identifier -> identifier.contains("/" + MultiProjectModule.IDENTIFIER + "/" + name + "/")),
                                depInherited.sequencedKeySet().stream().filter(key -> !spdxSources.contains(key)));
                        SequencedSet<String> artifactInputs = new LinkedHashSet<>();
                        artifactInputs.add(PREPARE);
                        artifactInputs.addAll(spdxSources);
                        depExec.addModule(ARTIFACTS,
                                dependencyModule.repositories(mergedRepositories)
                                        .pinning(pinning)
                                        .pathPlacement(test ? PathPlacement.CLASS_PATH : PathPlacement.INFERRED),
                                artifactInputs);
                    }, dependencyDeps);
                    SequencedMap<String, String> produceDeps = new LinkedHashMap<>();
                    produceDeps.put(MultiProjectModule.IDENTIFIER_PATH + name + "/" + SOURCES, SOURCES);
                    produceDeps.put(MultiProjectModule.IDENTIFIER_PATH + name + "/" + MANIFESTS, MANIFESTS);
                    produceDeps.put(MultiProjectModule.IDENTIFIER_PATH + name + "/" + COORDINATES, COORDINATES);
                    SequencedSet<String> resources = new LinkedHashSet<>();
                    String resourcesPrefix = MultiProjectModule.IDENTIFIER_PATH + name + "/resources-";
                    for (String key : inherited.sequencedKeySet()) {
                        if (key.startsWith(resourcesPrefix)) {
                            String synonym = key.substring(MultiProjectModule.IDENTIFIER_PATH.length() + name.length() + 1);
                            produceDeps.put(key, synonym);
                            resources.add(BuildExecutorModule.PREVIOUS + synonym);
                        }
                    }
                    produceDeps.put(DEPENDENCIES + "/" + ARTIFACTS, DEPENDENCIES + "/" + ARTIFACTS);
                    for (String source : spdxSources) {
                        produceDeps.put(source, source);
                    }
                    for (String key : inherited.sequencedKeySet()) {
                        produceDeps.putIfAbsent(key, key);
                    }
                    buildExecutor.addModule(PRODUCE,
                            assembler.apply(new MavenModuleDescriptor(name, dependencies.sequencedKeySet(), resources, spdxInherited, location, test),
                                    mergedRepositories,
                                    resolvers).toBuildModule(),
                            produceDeps);
                    buildExecutor.addStep(ASSIGN,
                            new Assign(),
                            MultiProjectModule.IDENTIFIER_PATH + name + "/" + COORDINATES,
                            PRODUCE);
                    buildExecutor.addStep(MultiProjectModule.INVENTORY,
                            Inventory.ofEnvironment(environment),
                            MultiProjectModule.IDENTIFIER_PATH + name + "/" + MANIFESTS,
                            ASSIGN,
                            PRODUCE,
                            DEPENDENCIES + "/" + ARTIFACTS);
                    });
                });
    }

    @Override
    public Optional<String> resolve(String path) {
        String wrapped = MultiProjectModule.MODULE + "/";
        if (path.startsWith(wrapped)) {
            return Optional.of(path.substring(wrapped.length()));
        }
        return path.equals(BOMS + "/" + POMS) ? Optional.of(BOMS) : Optional.empty();
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        if (!Files.exists(root.resolve("pom.xml"))) {
            return;
        }
        String group = this.group;
        Platform platform = this.platform;
        buildExecutor.addStep(SCAN, new Scan(root));
        buildExecutor.addStep(PREPARE,
                new Prepare(prefix, resolver, repository, sources),
                Stream.concat(Stream.of(SCAN), inherited.sequencedKeySet().stream()));
        buildExecutor.addModule(BOMS, (boms, prepared) -> {
            SortedSet<String> unstaged = new TreeSet<>();
            Path descriptors = prepared.get(PREVIOUS + PREPARE).resolve(BOM);
            if (Files.isRegularFile(descriptors.resolve(INDEX))) {
                SequencedProperties index = SequencedProperties.ofFiles(descriptors.resolve(INDEX));
                for (String name : index.stringPropertyNames()) {
                    SequencedProperties descriptor = SequencedProperties.ofFiles(descriptors.resolve(index.getProperty(name)));
                    String path = descriptor.getProperty("path");
                    if (descriptor.stringPropertyNames().stream().anyMatch(key -> key.startsWith(RELOCATION))) {
                        Path sources = root.resolve(path).resolve(descriptor.getProperty("sources.0"));
                        boolean compiled;
                        try (Stream<Path> found = Files.isDirectory(sources) ? Files.walk(sources) : Stream.empty()) {
                            compiled = found.anyMatch(Files::isRegularFile);
                        }
                        if (compiled) {
                            throw new IllegalArgumentException("The pom.xml in " + root.resolve(path)
                                    + " relocates its artifact with <distributionManagement><relocation>, which"
                                    + " publishes the POM alone, but " + sources + " holds sources: move them to"
                                    + " the module the relocation names, or remove the relocation");
                        }
                    }
                    Path packaging = BuildStep.locate(configurations.apply(new MavenModuleDescriptor(name,
                            Collections.emptyNavigableSet(),
                            Collections.emptyNavigableSet(),
                            Collections.emptyNavigableSet(),
                            root.resolve(path),
                            false).configurations()), "packaging.properties");
                    if (packaging != null && !SequencedProperties.ofFiles(packaging).flag("stage", true)) {
                        unstaged.add(path);
                    }
                }
            }
            boms.addStep(POMS, new Boms(unstaged), prepared.sequencedKeySet());
        }, Stream.concat(Stream.of(PREPARE), inherited.sequencedKeySet().stream()));
        buildExecutor.addModule(MODULE, (modules, paths) -> {
            Path skipped = paths.get(PREVIOUS + PREPARE).resolve(SKIPPED);
            if (printing != null && Files.exists(skipped)) {
                SequencedProperties.ofFiles(skipped).forEachProperty((pom, packaging) -> printing.accept(
                        ("%s%-11s%s %s builds nothing, as its packaging %s is none of jar and bundle, the packagings"
                                + " this build builds: where it is a jar with more in it, declare <packaging>jar</packaging>"
                                + " and let a plugin add the rest")
                                .formatted(palette.warning(),
                                        "[SKIPPED]",
                                        palette.reset(),
                                        Path.of(pom),
                                        packaging)));
            }
            Path described = paths.get(PREVIOUS + PREPARE).resolve(MAVEN);
            SequencedProperties descriptors = SequencedProperties.ofFiles(described.resolve(INDEX));
            for (String name : new TreeSet<>(descriptors.stringPropertyNames())) {
                Path file = described.resolve(descriptors.getProperty(name));
                modules.addModule(name, (module, modInherited) -> {
                    SequencedProperties properties = SequencedProperties.ofFiles(file);
                    boolean testModule = properties.flag("test", false);
                    boolean active = false;
                    Path base = root.resolve(properties.getProperty("path"));
                    String sourcesProperty = properties.getProperty("sources.0");
                    Path sources = sourcesProperty.isEmpty() ? null : base.resolve(sourcesProperty);
                    if (sources != null && Files.exists(sources)) {
                        active = true;
                    }
                    SequencedSet<Path> languages = new LinkedHashSet<>();
                    for (int languageIndex = 1; ; languageIndex++) {
                        String language = properties.getProperty("sources." + languageIndex);
                        if (language == null) {
                            break;
                        }
                        Path folder = base.resolve(language).normalize();
                        if (Files.isDirectory(folder) && (sources == null || !folder.equals(sources.normalize()))) {
                            languages.add(folder);
                            active = true;
                        }
                    }
                    int index = 0;
                    Set<Path> directories = new HashSet<>();
                    for (int resourceIndex = 0; ; resourceIndex++) {
                        String resource = properties.getProperty("resources." + resourceIndex);
                        if (resource == null) {
                            break;
                        }
                        Path resources = base.resolve(resource), directory = resources.toAbsolutePath().normalize();
                        if (Files.exists(resources) && directories.add(directory)) {
                            for (Path owned : List.of(paths.get(PREVIOUS + PREPARE), root.resolve(".jenesis"))) {
                                Path normalized = owned.toAbsolutePath().normalize();
                                if (normalized.startsWith(directory)) {
                                    throw new IllegalArgumentException("The resource directory " + resource
                                            + " of " + base.resolve("pom.xml")
                                            + " contains " + directory.relativize(normalized).getName(0)
                                            + ", which the build writes itself; a resource directory is copied whole,"
                                            + " without its includes, excludes or targetPath, so place a single file with"
                                            + " -Djenesis.project.resources=<file>:<path in the jar>, as"
                                            + " LICENSE:META-INF/LICENSE, and move that resource into a <profile> activated by"
                                            + " a property, such as <property><name>!jenesis</name></property>, which Maven"
                                            + " still activates and this build does not");
                                }
                            }
                            module.addSource("resources-" + ++index, Bind.asResources(), resources);
                            active = true;
                        } else if (!Files.exists(resources)
                                && !Path.of(resource).normalize().equals(Path.of("src", testModule ? "test" : "main", "resources"))
                                && printing != null) {
                            printing.accept(("%s%-11s%s %s names the resource directory %s, which does not exist"
                                    + " beside it, so it adds no resources")
                                    .formatted(palette.warning(),
                                            "[RESOURCES]",
                                            palette.reset(),
                                            Path.of(properties.getProperty("path")).resolve("pom.xml"),
                                            resource));
                        }
                    }
                    String tests = properties.value("tests");
                    if (!active && !testModule && tests != null) {
                        SequencedProperties testProperties = SequencedProperties.ofFiles(described.resolve(descriptors.getProperty(tests)));
                        active = testProperties.stringPropertyNames().stream()
                                .filter(key -> key.startsWith("sources.") || key.startsWith("resources."))
                                .map(testProperties::getProperty)
                                .filter(folder -> !folder.isEmpty())
                                .anyMatch(folder -> Files.exists(base.resolve(folder)))
                                || scans(base)
                                || configuresPlugin(base.resolve("src/test/build.jenesis"));
                    }
                    if (!active && testModule) {
                        active = scans(base)
                                || configuresPlugin(base.resolve("src/test/build.jenesis"));
                    }
                    if (!active && !testModule) {
                        active = configuresPlugin(base.resolve("src/main/build.jenesis"))
                                || configuresPlugin(base.resolve("build.jenesis"));
                        if (!active && printing != null) {
                            printing.accept(("%s%-11s%s %s builds no jar, as %s has neither sources nor resources:"
                                    + " a plugin that generates them is configured by a plugin-<name>.properties in"
                                    + " %s, which builds the module")
                                    .formatted(palette.warning(),
                                            "[SKIPPED]",
                                            palette.reset(),
                                            properties.getProperty("groupId") + ":" + properties.getProperty("artifactId"),
                                            Path.of(properties.getProperty("path")).resolve("pom.xml"),
                                            Path.of(properties.getProperty("path")).resolve("src/main/build.jenesis")));
                        }
                    }
                    if (active && printing != null && !testModule && properties.getProperty("release") == null) {
                        printing.accept(("%s%-11s%s %s compiles for release %d, the JDK the build runs on, as %s sets"
                                + " neither maven.compiler.release nor its target or source - maven.compiler.release sets it")
                                .formatted(palette.warning(),
                                        "[RELEASE]",
                                        palette.reset(),
                                        properties.getProperty("groupId") + ":" + properties.getProperty("artifactId"),
                                        Runtime.version().feature(),
                                        Path.of(properties.getProperty("path")).resolve("pom.xml")));
                    }
                    if (active) {
                        SequencedSet<Path> folders = new LinkedHashSet<>();
                        folders.add(sources == null ? base : sources);
                        folders.addAll(languages);
                        module.addSource("sources", Bind.asSources(), folders);
                        module.addStep(COORDINATES, (_, context, _) -> {
                            SequencedProperties coordinates = new SequencedProperties();
                            coordinates.setProperty(properties.getProperty("coordinate"), "");
                            Path pomFile = paths.get(PREVIOUS + SCAN)
                                    .resolve(POM)
                                    .resolve(properties.getProperty("path"))
                                    .resolve("pom.xml");
                            coordinates.setProperty(properties.getProperty("pom"),
                                    context.next().relativize(pomFile).toString().replace(File.separatorChar, '/'));
                            coordinates.store(context.next().resolve(IDENTITY));
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        });
                        int feature = Runtime.version().feature();
                        module.addStep(MANIFESTS, (_, context, manifestArgs) -> {
                            String[] coordinateParts = properties.getProperty("coordinate").split("/");
                            String testsOf = coordinateParts.length == 6 && "tests".equals(coordinateParts[4])
                                    ? coordinateParts[2]
                                    : null;
                            SequencedProperties requires = new SequencedProperties();
                            String compile = properties.getProperty("dependencies.compile", "");
                            String provided = properties.getProperty("dependencies.provided", "");
                            String runtime = properties.getProperty("dependencies.runtime", "");
                            String test = properties.getProperty("dependencies.test", "");
                            String checksums = properties.getProperty("checksums", "");
                            Map<String, String> checksumByCoordinate = new LinkedHashMap<>();
                            for (String entry : checksums.isEmpty() ? new String[0] : checksums.split("\t")) {
                                int split = entry.indexOf('=');
                                if (split > 0) {
                                    checksumByCoordinate.put(entry.substring(0, split), entry.substring(split + 1));
                                }
                            }
                            for (String dependency : compile.isEmpty() ? new String[0] : compile.split("\t")) {
                                String value = checksumByCoordinate.getOrDefault(dependency, "");
                                requires.setProperty(group + "/compile/" + dependency, value);
                                requires.setProperty(group + "/runtime/" + dependency, value);
                            }
                            for (String dependency : provided.isEmpty() ? new String[0] : provided.split("\t")) {
                                requires.setProperty(group + "/compile/" + dependency, checksumByCoordinate.getOrDefault(dependency, ""));
                            }
                            for (String dependency : runtime.isEmpty() ? new String[0] : runtime.split("\t")) {
                                requires.setProperty(group + "/runtime/" + dependency, checksumByCoordinate.getOrDefault(dependency, ""));
                            }
                            for (String dependency : test.isEmpty() ? new String[0] : test.split("\t")) {
                                String value = checksumByCoordinate.getOrDefault(dependency, "");
                                requires.setProperty(group + "/compile/" + dependency, value);
                                requires.setProperty(group + "/runtime/" + dependency, value);
                            }
                            String attach = properties.getProperty("attachments", "");
                            SequencedProperties attachments = new SequencedProperties();
                            for (String entry : attach.isEmpty() ? new String[0] : attach.split("\t")) {
                                int split = entry.indexOf('=');
                                String left = entry.substring(0, split), arguments = entry.substring(split + 1);
                                int space = left.indexOf(' ');
                                String key = space < 0 ? left : left.substring(0, space);
                                String version = space < 0 ? null : left.substring(space + 1);
                                int slash = key.indexOf('/');
                                String seeded = key.substring(0, slash) + "/agent/" + key.substring(slash + 1);
                                String coordinate = key.substring(slash + 1) + (version == null ? "" : "/" + version);
                                if (requires.getProperty(key.substring(0, slash) + "/runtime/" + coordinate) == null) {
                                    requires.setProperty(key.substring(0, slash) + "/agent/" + coordinate, "");
                                }
                                attachments.setProperty(seeded, arguments);
                            }
                            String declared = properties.getProperty("plugins", "");
                            for (String entry : declared.isEmpty() ? new String[0] : declared.split("\t")) {
                                int split = entry.indexOf('=');
                                String plugin = entry.substring(0, split);
                                requires.setProperty(entry.substring(split + 1)
                                        + "/plugin/"
                                        + plugin
                                        + (plugin.startsWith("maven/") && plugin.split("/").length == 3
                                                ? "/RELEASE"
                                                : ""), "");
                            }
                            requires.store(context.next().resolve(BuildStep.REQUIRES));
                            String reactor = properties.getProperty("reactor", "");
                            if (!reactor.isEmpty()) {
                                SequencedProperties ordered = new SequencedProperties();
                                for (String coordinate : reactor.split("\t")) {
                                    ordered.setProperty(coordinate, "");
                                }
                                ordered.store(context.next().resolve(Group.REACTOR));
                            }
                            String keys = properties.getProperty("signatures", "");
                            if (!keys.isEmpty()) {
                                SequencedProperties signatures = new SequencedProperties();
                                for (String entry : keys.split("\t")) {
                                    int split = entry.indexOf('=');
                                    signatures.setProperty(entry.substring(0, split),
                                            entry.substring(split + 1));
                                }
                                signatures.store(context.next().resolve(BuildStep.SIGNATURES));
                            }
                            if (!attachments.isEmpty()) {
                                attachments.store(context.next().resolve(BuildStep.ATTACHMENTS));
                            }
                            String granted = properties.getProperty("natives", "");
                            if (!granted.isEmpty()) {
                                SequencedProperties natives = new SequencedProperties();
                                for (String key : granted.split("\t")) {
                                    int slash = key.indexOf('/');
                                    natives.setProperty(key.substring(0, slash) + "/native/" + key.substring(slash + 1), "");
                                }
                                natives.store(context.next().resolve(BuildStep.NATIVES));
                            }
                            List<String> aliased = properties.value("aliases") == null
                                    ? List.of()
                                    : List.of(properties.value("aliases").split("\t"));
                            if (!aliased.isEmpty()) {
                                SequencedProperties aliases = new SequencedProperties();
                                for (String entry : aliased) {
                                    int split = entry.indexOf('=');
                                    aliases.setProperty(group + "/module/" + entry.substring(0, split),
                                            entry.substring(split + 1));
                                }
                                aliases.store(context.next().resolve(BuildStep.ALIASES));
                            }
                            String named = properties.getProperty("named"), release = properties.getProperty("release");
                            boolean preview = release != null && release.endsWith("-preview");
                            if (named != null || preview || !aliased.isEmpty()) {
                                Manifest manifest = new Manifest();
                                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                                if (named != null) {
                                    manifest.getMainAttributes().putValue(PathPlacement.NATIVE_ACCESS, named);
                                }
                                if (!aliased.isEmpty()) {
                                    manifest.getMainAttributes().putValue(PathPlacement.ALIASES, String.join(",", aliased));
                                }
                                if (preview) {
                                    manifest.getMainAttributes().putValue(PathPlacement.PREVIEW,
                                            release.substring(0, release.length() - "-preview".length()));
                                }
                                try (OutputStream out = Files.newOutputStream(context.next().resolve(Versions.MANIFEST))) {
                                    manifest.write(out);
                                }
                            }
                            SequencedProperties exclusionsProperties = new SequencedProperties();
                            for (String key : requires.stringPropertyNames()) {
                                int scopeSlash = key.indexOf('/', key.indexOf('/') + 1);
                                String exclusion = properties.getProperty("exclusions." + key.substring(scopeSlash + 1));
                                if (exclusion != null) {
                                    exclusionsProperties.setProperty(key, exclusion);
                                }
                            }
                            if (!exclusionsProperties.isEmpty()) {
                                exclusionsProperties.store(context.next().resolve(BuildStep.EXCLUSIONS));
                            }
                            SequencedProperties managedExclusions = new SequencedProperties();
                            for (String key : properties.stringPropertyNames()) {
                                if (key.startsWith("managed.exclusions.")) {
                                    managedExclusions.setProperty(group + "/" + key.substring("managed.exclusions.".length()),
                                            properties.getProperty(key));
                                }
                            }
                            if (!managedExclusions.isEmpty()) {
                                managedExclusions.store(context.next().resolve(BuildStep.MANAGED));
                            }
                            SequencedProperties managedScopes = new SequencedProperties();
                            for (String key : properties.stringPropertyNames()) {
                                if (key.startsWith("managed.scope.")) {
                                    managedScopes.setProperty(group + "/" + key.substring("managed.scope.".length()),
                                            properties.getProperty(key));
                                }
                            }
                            if (!managedScopes.isEmpty()) {
                                managedScopes.store(context.next().resolve(SCOPES));
                            }
                            SequencedProperties expressions = new SequencedProperties();
                            for (String key : properties.stringPropertyNames()) {
                                if (key.startsWith("expression.")) {
                                    expressions.setProperty(key.substring("expression.".length()),
                                            properties.getProperty(key));
                                }
                            }
                            if (!expressions.isEmpty()) {
                                expressions.store(context.next().resolve(EXPRESSIONS));
                            }
                            String optionalDependencies = properties.getProperty("optional");
                            if (optionalDependencies != null) {
                                List<String> optional = List.of(optionalDependencies.split("\t"));
                                SequencedProperties optionals = new SequencedProperties();
                                for (String key : requires.stringPropertyNames()) {
                                    int scopeSlash = key.indexOf('/', key.indexOf('/') + 1);
                                    if (optional.contains(key.substring(scopeSlash + 1))) {
                                        optionals.setProperty(key, "");
                                    }
                                }
                                optionals.store(context.next().resolve(BuildStep.OPTIONALS));
                            }
                            SequencedProperties versions = new SequencedProperties();
                            String managed = properties.getProperty("managedDependencies", "");
                            if (!managed.isEmpty()) {
                                for (String entry : managed.split("\t")) {
                                    int split = entry.indexOf('=');
                                    String coord = entry.substring(0, split), version = entry.substring(split + 1);
                                    versions.setProperty(group + "/" + coord, version);
                                }
                            }
                            String qualified = properties.getProperty("qualifiedDependencies", "");
                            if (!qualified.isEmpty()) {
                                SequencedMap<String, String> unguarded = new LinkedHashMap<>();
                                SequencedMap<String, SequencedMap<String, String>> variants = new LinkedHashMap<>();
                                for (String entry : qualified.split("\t")) {
                                    int split = entry.indexOf('=');
                                    String key = entry.substring(0, split), value = entry.substring(split + 1);
                                    int bracket = key.indexOf('[');
                                    if (bracket < 0) {
                                        unguarded.put(key, value);
                                    } else {
                                        variants.computeIfAbsent(key.substring(0, bracket), _ -> new LinkedHashMap<>())
                                                .put(key.substring(bracket + 1, key.length() - 1), value);
                                    }
                                }
                                for (Map.Entry<String, SequencedMap<String, String>> variant : variants.entrySet()) {
                                    String selected = platform.select(variant.getKey(),
                                            unguarded.get(variant.getKey()),
                                            variant.getValue());
                                    if (selected != null) {
                                        unguarded.put(variant.getKey(), selected);
                                    }
                                }
                                unguarded.forEach(versions::setProperty);
                            }
                            if (!versions.isEmpty()) {
                                versions.store(context.next().resolve(BuildStep.VERSIONS));
                            }
                            Javac.writeRelease(context.next(), properties.getProperty("release"), feature);
                            SequencedProperties descriptor = new SequencedProperties();
                            descriptor.setProperty("path", properties.getProperty("path"));
                            descriptor.setProperty("sources", properties.getProperty("sources.0"));
                            descriptor.setProperty("modular", "false");
                            if (testsOf != null) {
                                descriptor.setProperty("test", testsOf);
                            }
                            String mainClass = properties.getProperty("mainClass");
                            if (mainClass != null && testsOf == null) {
                                descriptor.setProperty("main", mainClass);
                            }
                            if (properties.flag("native")) {
                                descriptor.setProperty("native", "true");
                            }
                            descriptor.store(context.next().resolve(BuildStep.MODULE));
                            metadata(properties, manifestArgs.values()).store(context.next().resolve(BuildStep.METADATA));
                            return CompletableFuture.completedStage(new BuildStepResult(true));
                        }, modInherited.sequencedKeySet());
                    }
                }, paths.sequencedKeySet().stream());
            }
        }, Stream.concat(Stream.of(SCAN, PREPARE), inherited.sequencedKeySet().stream()));
    }

    private static SequencedProperties metadata(SequencedProperties properties,
                                                Collection<BuildStepArgument> arguments) throws IOException {
        SequencedProperties metadata = new SequencedProperties();
        metadata.setProperty("project", properties.getProperty("groupId"));
        metadata.setProperty("artifact", properties.getProperty("artifactId"));
        metadata.setProperty("version", properties.getProperty("version"));
        SequencedProperties own = new SequencedProperties();
        for (String key : properties.stringPropertyNames()) {
            if (key.startsWith(POM_METADATA)) {
                own.setProperty(key.substring(POM_METADATA.length()), properties.getProperty(key));
            }
        }
        own.forEach(metadata::put);
        Set<String> lists = own.stringPropertyNames().stream()
                .filter(key -> key.startsWith("license.") || key.startsWith("developer."))
                .map(key -> key.substring(0, key.indexOf('.') + 1))
                .collect(Collectors.toSet());
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path upstream = argument.folder().resolve(BuildStep.METADATA);
            if (Files.isRegularFile(upstream)) {
                SequencedProperties.ofFiles(upstream).forEachProperty((key, value) -> {
                    if (COMMANDED.contains(key)
                            || !own.containsKey(key)
                            && lists.stream().noneMatch(key::startsWith)) {
                        metadata.setProperty(key, value);
                    }
                });
            }
        }
        return metadata;
    }

    private static boolean scans(Path base) throws IOException {
        for (Path configuration : List.of(base.resolve("src/test/build.jenesis"), base.resolve("build.jenesis"))) {
            Path file = configuration.resolve("test.properties");
            if (Files.isRegularFile(file) && SequencedProperties.ofFiles(file).value("scan") != null) {
                return true;
            }
        }
        return false;
    }

    private static boolean configuresPlugin(Path configuration) throws IOException {
        if (!Files.isDirectory(configuration)) {
            return false;
        }
        try (Stream<Path> configured = Files.list(configuration)) {
            return configured.map(path -> path.getFileName().toString())
                    .anyMatch(entry -> entry.startsWith(ProjectPlugins.PLUGIN + "-") && entry.endsWith(".properties"));
        }
    }

    private record Boms(SortedSet<String> unstaged) implements BuildStep {

        private Boms {
            unstaged = new TreeSet<>(unstaged);
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path descriptors = arguments.get(PREVIOUS + PREPARE).folder().resolve(BOM);
            if (!Files.isDirectory(descriptors)) {
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }
            List<BuildStepArgument> upstream = arguments.entrySet().stream()
                    .filter(entry -> !entry.getKey().equals(PREVIOUS + PREPARE))
                    .map(Map.Entry::getValue)
                    .toList();
            SequencedProperties described = SequencedProperties.ofFiles(descriptors.resolve(INDEX));
            SequencedProperties inventory = new SequencedProperties();
            MavenPomEmitter emitter = new MavenPomEmitter();
            for (String name : new TreeSet<>(described.stringPropertyNames())) {
                SequencedProperties properties = SequencedProperties.ofFiles(descriptors.resolve(described.getProperty(name)));
                SequencedProperties metadata = metadata(properties, upstream);
                SequencedMap<MavenDependencyKey, MavenDependencyValue> managed = new LinkedHashMap<>();
                for (int index = 0; properties.value("managed." + index) != null; index++) {
                    String prefix = "managed." + index;
                    MavenDependencyKey.Versioned parsed = MavenDependencyKey.parse(properties.value(prefix));
                    List<String> exclusions = properties.entries(prefix + ".exclusions");
                    String scope = properties.value(prefix + ".scope"), optional = properties.value(prefix + ".optional");
                    managed.put(parsed.key(), new MavenDependencyValue(parsed.version(),
                            scope == null ? null : MavenDependencyScope.of(scope),
                            null,
                            exclusions == null ? null : exclusions.stream().map(exclusion -> new MavenDependencyName(
                                    exclusion.substring(0, exclusion.indexOf('/')),
                                    exclusion.substring(exclusion.indexOf('/') + 1))).toList(),
                            optional == null ? null : Boolean.valueOf(optional)));
                }
                String path = properties.getProperty("path");
                Path pom = Files.createDirectory(context.next().resolve(name))
                        .resolve(Pom.POM);
                boolean relocated = properties.stringPropertyNames().stream().anyMatch(key -> key.startsWith(RELOCATION));
                try (Writer writer = Files.newBufferedWriter(pom)) {
                    emitter.emit(metadata.getProperty("project"),
                            metadata.getProperty("artifact"),
                            metadata.getProperty("version"),
                            "pom",
                            Collections.emptyNavigableMap(),
                            managed,
                            MavenPomEmitter.Metadata.of(metadata),
                            relocated ? new MavenPomEmitter.Relocation(properties.value(RELOCATION + "groupId"),
                                    properties.value(RELOCATION + "artifactId"),
                                    properties.value(RELOCATION + "version"),
                                    properties.value(RELOCATION + "message")) : null).accept(writer);
                }
                String prefix = Inventory.prefixOf(path);
                inventory.setProperty(prefix + "path", path);
                inventory.setProperty(prefix + "pom", context.next().relativize(pom).toString().replace(File.separatorChar, '/'));
                inventory.setProperty(prefix + "packaging", "pom");
                if (unstaged.contains(path)) {
                    inventory.setProperty(prefix + "stage", "false");
                }
            }
            if (!inventory.isEmpty()) {
                inventory.store(context.next().resolve(Inventory.INVENTORY));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Scan(Path root) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path poms = Files.createDirectory(context.next().resolve(POM));
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.getFileName().toString().equals("pom.xml")) {
                        Path target = poms.resolve(root.relativize(file));
                        Files.createDirectories(target.getParent());
                        BuildStep.linkOrCopy(target, file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (!dir.equals(root) && Files.exists(dir.resolve(BuildExecutor.SKIP_MARKER))) {
                        Files.createFile(Files.createDirectories(poms.resolve(root.relativize(dir)))
                                .resolve(BuildExecutor.SKIP_MARKER));
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }
    }

    private static class Prepare implements BuildStep {

        private final String prefix;
        private final MavenResolver resolver;
        private final List<String> sources;
        private final transient MavenRepository repository;

        private Prepare(String prefix, MavenResolver resolver, MavenRepository repository, List<String> sources) {
            this.prefix = prefix;
            this.resolver = resolver;
            this.repository = repository;
            this.sources = sources;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path maven = Files.createDirectory(context.next().resolve(MAVEN));
            SequencedProperties skipped = new SequencedProperties(), modules = new SequencedProperties(), boms = new SequencedProperties();
            String version = null;
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (argument.getKey().equals(SCAN) || argument.getValue().removed()) {
                    continue;
                }
                Path file = argument.getValue().folder().resolve(BuildStep.METADATA);
                if (Files.isRegularFile(file)) {
                    version = SequencedProperties.ofFiles(file).value("version", version);
                }
            }
            SequencedMap<Path, MavenLocalPom> poms = resolver.local(executor,
                    repository,
                    arguments.get(SCAN).folder().resolve(POM));
            Map<String, String> siblings = new HashMap<>();
            poms.values().forEach(pom -> siblings.put(pom.groupId() + "/" + pom.artifactId(), pom.version()));
            for (Map.Entry<Path, MavenLocalPom> entry : poms.entrySet()) {
                MavenLocalPom value = entry.getValue();
                if (version != null) {
                    value = value.version(version)
                            .dependencies(versioned(value.dependencies(), siblings, version))
                            .managedDependencies(versioned(value.managedDependencies(), siblings, version))
                            .bom(versioned(value.bom(), siblings, version))
                            .plugins(versionedPlugins(value.plugins(), siblings, version))
                            .testPlugins(versionedPlugins(value.testPlugins(), siblings, version))
                            .pluginExclusions(versionedPlugins(value.pluginExclusions(), siblings, version))
                            .reactor(versioned(value.reactor(), siblings, version));
                }
                Path pomFile = entry.getKey().resolve("pom.xml");
                String unresolved = unresolved(value.version());
                if (unresolved != null) {
                    throw new IllegalArgumentException("The version " + value.version() + " of " + value.groupId() + ":"
                            + value.artifactId() + " in " + pomFile + " names the property " + unresolved + ", which no"
                            + " pom.xml defines and which this build does not take from a Maven extension: define it in"
                            + " the pom.xml, or set the version as jenesis.project.version, on the command line or in"
                            + " jenesis.properties");
                }
                if (value.dependencies() != null) {
                    for (Map.Entry<MavenDependencyKey, MavenDependencyValue> dependency : value.dependencies().entrySet()) {
                        MavenDependencyKey key = dependency.getKey();
                        SequencedMap<String, String> components = new LinkedHashMap<>();
                        components.put("groupId", key.groupId());
                        components.put("artifactId", key.artifactId());
                        components.put("type", key.type());
                        components.put("classifier", key.classifier());
                        components.put("version", dependency.getValue().version());
                        for (Map.Entry<String, String> component : components.entrySet()) {
                            String property = unresolved(component.getValue());
                            if (property != null) {
                                throw new IllegalArgumentException("The " + component.getKey() + " "
                                        + component.getValue() + " of the dependency " + key.groupId() + ":"
                                        + key.artifactId() + " in " + pomFile + " names the property " + property
                                        + ", which no pom.xml defines: define it in the pom.xml");
                            }
                        }
                    }
                }
                if (value.bom() != null || value.relocation() != null) {
                    String id = SIBLING_MODULE_PREFIX
                            + BuildExecutorModule.encodePath(entry.getKey().toString().replace(File.separatorChar, '/'));
                    writeBom(Files.createDirectories(context.next().resolve(BOM)).resolve(id + ".properties"), value, entry.getKey());
                    boms.setProperty(id, id + ".properties");
                    continue;
                } else if (value.packaging() != null && !JARS.contains(value.packaging())) {
                    if (!AGGREGATES.contains(value.packaging())) {
                        skipped.setProperty(pomFile.toString().replace(File.separatorChar, '/'), value.packaging());
                    }
                    continue;
                }
                if (value.dependencies() != null) {
                    for (MavenDependencyKey key : value.dependencies().sequencedKeySet()) {
                        if (!key.jar() && !key.type().equals("pom")) {
                            throw new IllegalArgumentException("The dependency " + key.groupId() + ":" + key.artifactId()
                                    + " in " + pomFile + " is of type " + key.type() + ", which Maven places on no path"
                                    + " and which Jenesis does not unpack: declare it in a profile that a property"
                                    + " activates, such as <property><name>!jenesis</name></property>, which Maven"
                                    + " activates and Jenesis never does");
                        }
                    }
                }
                String coordinate = new MavenDependencyKey(value.groupId(), value.artifactId(), "jar", null)
                        .coordinate(prefix, value.version());
                MavenDependencyKey selfPom = new MavenDependencyKey(value.groupId(), value.artifactId(), "pom", null);
                String relativePath = entry.getKey().toString().replace(File.separatorChar, '/');
                String qualifiedDependencies = value.qualifiedDependencies() == null ? "" : value.qualifiedDependencies().entrySet().stream()
                        .map(requires -> requires.getKey() + "=" + requires.getValue())
                        .collect(Collectors.joining("\t"));
                String main = SIBLING_MODULE_PREFIX + BuildExecutorModule.encodePath(relativePath), tests = "test" + main;
                writeModule(maven.resolve(main + ".properties"),
                        tests,
                        value,
                        relativePath,
                        coordinate,
                        selfPom,
                        false,
                        qualifiedDependencies,
                        attachments(value, false));
                writeModule(maven.resolve(tests + ".properties"),
                        null,
                        value,
                        relativePath,
                        coordinate,
                        selfPom,
                        true,
                        qualifiedDependencies,
                        attachments(value, true));
                modules.setProperty(main, main + ".properties");
                modules.setProperty(tests, tests + ".properties");
            }
            modules.store(maven.resolve(INDEX));
            if (!boms.isEmpty()) {
                boms.store(context.next().resolve(BOM).resolve(INDEX));
            }
            if (!skipped.isEmpty()) {
                skipped.store(context.next().resolve(SKIPPED));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static SequencedMap<MavenDependencyKey, MavenDependencyValue> versioned(
                SequencedMap<MavenDependencyKey, MavenDependencyValue> dependencies,
                Map<String, String> siblings,
                String version) {
            if (dependencies == null) {
                return null;
            }
            SequencedMap<MavenDependencyKey, MavenDependencyValue> versioned = new LinkedHashMap<>();
            dependencies.forEach((key, value) -> versioned.put(key,
                    Objects.equals(siblings.get(key.groupId() + "/" + key.artifactId()), value.version())
                            ? value.version(version)
                            : value));
            return versioned;
        }

        private static SequencedMap<String, String> versionedPlugins(SequencedMap<String, String> plugins,
                                                                     Map<String, String> siblings,
                                                                     String version) {
            if (plugins == null) {
                return null;
            }
            SequencedMap<String, String> versioned = new LinkedHashMap<>();
            plugins.forEach((plugin, group) -> {
                String[] segments = plugin.split("/");
                versioned.put(segments.length == 4
                        && segments[0].equals("maven")
                        && segments[3].equals(siblings.get(segments[1] + "/" + segments[2]))
                        ? String.join("/", segments[0], segments[1], segments[2], version)
                        : plugin, group);
            });
            return versioned;
        }

        private static String unresolved(String text) {
            Matcher matcher = UNRESOLVED.matcher(text == null ? "" : text);
            return matcher.find() ? matcher.group(1) : null;
        }

        private static void writeBom(Path file, MavenLocalPom value, Path path) throws IOException {
            String relativePath = path.toString().replace(File.separatorChar, '/');
            SequencedProperties properties = new SequencedProperties();
            properties.setProperty("path", relativePath);
            properties.setProperty("groupId", value.groupId());
            properties.setProperty("artifactId", value.artifactId());
            properties.setProperty("version", value.version());
            if (value.relocation() != null) {
                properties.setProperty("sources.0", value.sourceDirectory() == null
                        ? "src/main/java"
                        : value.sourceDirectory().replace(File.separatorChar, '/'));
                SequencedMap<String, String> relocation = new LinkedHashMap<>();
                relocation.put("groupId", value.relocation().groupId());
                relocation.put("artifactId", value.relocation().artifactId());
                relocation.put("version", value.relocation().version());
                relocation.put("message", value.relocation().message());
                relocation.forEach((key, declared) -> {
                    if (declared != null) {
                        properties.setProperty(RELOCATION + key, declared);
                    }
                });
            }
            int index = 0;
            for (Map.Entry<MavenDependencyKey, MavenDependencyValue> managed : (value.bom() == null
                    ? Collections.<MavenDependencyKey, MavenDependencyValue>emptyNavigableMap()
                    : value.bom()).entrySet()) {
                String prefix = "managed." + index++;
                properties.setProperty(prefix, managed.getKey().coordinate(null, managed.getValue().version()));
                if (managed.getValue().scope() != null) {
                    properties.setProperty(prefix + ".scope", managed.getValue().scope().name().toLowerCase(Locale.ROOT));
                }
                if (managed.getValue().exclusions() != null) {
                    properties.setProperty(prefix + ".exclusions", managed.getValue().exclusions().stream()
                            .map(name -> name.groupId() + "/" + name.artifactId())
                            .collect(Collectors.joining(",")));
                }
                if (managed.getValue().optional() != null) {
                    properties.setProperty(prefix + ".optional", managed.getValue().optional().toString());
                }
            }
            value.metadata().properties().forEach((key, metadata) -> properties.setProperty(POM_METADATA + key, metadata));
            properties.store(file);
        }

        private String attachments(MavenLocalPom value, boolean test) {
            if (value.attachments() == null || value.attachments().isEmpty()) {
                return "";
            }
            List<String> entries = new ArrayList<>();
            for (Map.Entry<String, String> attachment : value.attachments().entrySet()) {
                String key = attachment.getKey();
                String spec = key.substring(key.indexOf('/') + 1);
                String version = null;
                MavenDependencyScope scope = null;
                if (value.dependencies() != null) {
                    for (Map.Entry<MavenDependencyKey, MavenDependencyValue> dependency : value.dependencies().entrySet()) {
                        if (dependency.getKey().coordinate(prefix, null).equals(spec)) {
                            version = dependency.getValue().version();
                            scope = dependency.getValue().scope();
                            break;
                        }
                    }
                }
                if (version == null && value.managedDependencies() != null) {
                    for (Map.Entry<MavenDependencyKey, MavenDependencyValue> dependency : value.managedDependencies().entrySet()) {
                        if (dependency.getKey().coordinate(prefix, null).equals(spec)) {
                            version = dependency.getValue().version();
                            break;
                        }
                    }
                }
                if (scope == MavenDependencyScope.TEST && !test) {
                    continue;
                }
                if (version == null
                        && spec.startsWith(prefix + "/")
                        && spec.chars().filter(character -> character == '/').count() > 2) {
                    throw new IllegalArgumentException("Cannot determine version for jenesis.attach "
                            + key
                            + ": declare it as a dependency or manage its version");
                }
                entries.add(key + (version == null ? "" : " " + version) + "=" + attachment.getValue());
            }
            return String.join("\t", entries);
        }

        private void writeModule(Path file,
                                 String tests,
                                 MavenLocalPom value,
                                 String relativePath,
                                 String coordinate,
                                 MavenDependencyKey selfPom,
                                 boolean test,
                                 String qualifiedDependencies,
                                 String attachments) throws IOException {
            SequencedProperties properties = new SequencedProperties();
            properties.setProperty("coordinate", test
                    ? new MavenDependencyKey(value.groupId(), value.artifactId(), "jar", "tests")
                            .coordinate(prefix, value.version())
                    : coordinate);
            properties.setProperty("pom", selfPom.coordinate(prefix, value.version()));
            properties.setProperty("path", relativePath);
            properties.setProperty("groupId", value.groupId());
            properties.setProperty("artifactId", value.artifactId());
            properties.setProperty("version", value.version());
            String release = test ? value.testRelease() : value.release();
            if (release != null) {
                properties.setProperty("release", release);
            }
            if (!test && value.mainClass() != null) {
                properties.setProperty("mainClass", value.mainClass());
            }
            if (test) {
                String testDependencies = value.dependencies() == null
                        ? ""
                        : value.dependencies().entrySet().stream()
                                .filter(dep -> dep.getValue().scope() == MavenDependencyScope.COMPILE
                                        || dep.getValue().scope() == MavenDependencyScope.RUNTIME
                                        || dep.getValue().scope() == MavenDependencyScope.PROVIDED
                                        || dep.getValue().scope() == MavenDependencyScope.TEST)
                                .map(dep -> dep.getKey().coordinate(prefix, dep.getValue().version()))
                                .collect(Collectors.joining("\t"));
                properties.setProperty("dependencies.test", testDependencies.isEmpty()
                        ? coordinate
                        : testDependencies + "\t" + coordinate);
            } else {
                for (MavenDependencyScope scope : List.of(
                        MavenDependencyScope.COMPILE,
                        MavenDependencyScope.PROVIDED,
                        MavenDependencyScope.RUNTIME)) {
                    properties.setProperty("dependencies." + scope.name().toLowerCase(Locale.ROOT),
                            value.dependencies() == null ? "" : value.dependencies().entrySet().stream()
                                    .filter(dep -> dep.getValue().scope() == scope)
                                    .map(dep -> dep.getKey().coordinate(prefix, dep.getValue().version()))
                                    .collect(Collectors.joining("\t")));
                }
            }
            if (!test && value.dependencies() != null) {
                String optional = value.dependencies().entrySet().stream()
                        .filter(dep -> dep.getValue().scope() != MavenDependencyScope.TEST
                                && Boolean.TRUE.equals(dep.getValue().optional()))
                        .map(dep -> dep.getKey().coordinate(prefix, dep.getValue().version()))
                        .collect(Collectors.joining("\t"));
                if (!optional.isEmpty()) {
                    properties.setProperty("optional", optional);
                }
            }
            if (value.dependencies() != null) {
                value.dependencies().forEach((depKey, depValue) -> {
                    if (depValue.exclusions() != null && !depValue.exclusions().isEmpty()) {
                        properties.setProperty(
                                "exclusions." + depKey.coordinate(prefix, depValue.version()),
                                depValue.exclusions().stream()
                                        .map(name -> name.groupId() + "/" + name.artifactId())
                                        .collect(Collectors.joining(",")));
                    }
                });
            }
            value.expressions().forEach((depKey, expression) -> {
                MavenDependencyValue resolved = value.managedDependencies() == null
                        ? null
                        : value.managedDependencies().get(depKey);
                if (resolved == null && value.dependencies() != null) {
                    resolved = value.dependencies().get(depKey);
                }
                MavenDependencyKey raw = expression.key();
                properties.setProperty("expression." + depKey.coordinate(null, null),
                        raw.groupId() + "/" + raw.artifactId()
                                + "/" + (raw.type() == null ? "" : raw.type())
                                + "/" + (raw.classifier() == null ? "" : raw.classifier())
                                + (expression.version() == null || resolved == null || resolved.version() == null
                                        ? ""
                                        : " " + expression.version() + " " + resolved.version()));
            });
            if (value.managedDependencies() != null) {
                value.managedDependencies().forEach((depKey, depValue) -> {
                    if (depValue.exclusions() != null && !depValue.exclusions().isEmpty()) {
                        properties.setProperty(
                                "managed.exclusions." + depKey.coordinate(prefix, null),
                                depValue.exclusions().stream()
                                        .map(name -> name.groupId() + "/" + name.artifactId())
                                        .collect(Collectors.joining(",")));
                    }
                    if (depValue.scope() != null && depValue.scope() != MavenDependencyScope.IMPORT) {
                        properties.setProperty("managed.scope." + depKey.coordinate(prefix, null),
                                depValue.scope().name().toLowerCase(Locale.ROOT));
                    }
                });
            }
            String managed = value.managedDependencies() == null ? "" : value.managedDependencies().entrySet().stream()
                    .map(dep -> dep.getKey().coordinate(prefix, null)
                            + "=" + dep.getValue().version()
                            + (dep.getValue().checksum() == null ? "" : " " + dep.getValue().checksum()))
                    .collect(Collectors.joining("\t"));
            properties.setProperty("managedDependencies", managed);
            if (value.reactor() != null && !value.reactor().isEmpty()) {
                properties.setProperty("reactor", value.reactor().entrySet().stream()
                        .map(dep -> dep.getKey().coordinate(prefix, dep.getValue().version()))
                        .collect(Collectors.joining("\t")));
            }
            if (!qualifiedDependencies.isEmpty()) {
                properties.setProperty("qualifiedDependencies", qualifiedDependencies);
            }
            if (!attachments.isEmpty()) {
                properties.setProperty("attachments", attachments);
            }
            if (value.natives() != null && !value.natives().isEmpty()) {
                String self = "main/maven/" + value.groupId() + "/" + value.artifactId();
                if (!test) {
                    properties.setProperty("named", value.natives().stream()
                            .map(key -> key.startsWith("main/module/")
                                    ? key.substring("main/module/".length())
                                    : key.startsWith("main/maven/") && key.chars().filter(character -> character == '/').count() == 3
                                            ? key.substring("main/maven/".length())
                                            : key)
                            .collect(Collectors.joining(",")));
                    if (value.natives().contains(self)) {
                        properties.setProperty("native", "true");
                    }
                }
                String natives = value.natives().stream()
                        .filter(key -> test || !key.equals(self))
                        .collect(Collectors.joining("\t"));
                if (!natives.isEmpty()) {
                    properties.setProperty("natives", natives);
                }
            }
            SequencedMap<String, String> plugins = test ? value.testPlugins() : value.plugins();
            if (plugins != null && !plugins.isEmpty()) {
                properties.setProperty("plugins", plugins.entrySet().stream()
                        .map(plugin -> plugin.getKey() + "=" + plugin.getValue())
                        .collect(Collectors.joining("\t")));
                if (value.pluginExclusions() != null) {
                    value.pluginExclusions().forEach((plugin, excluded) -> {
                        if (plugins.containsKey(plugin)) {
                            properties.setProperty("exclusions." + plugin
                                    + (plugin.split("/").length == 3 ? "/RELEASE" : ""), excluded);
                        }
                    });
                }
            }
            SequencedMap<String, String> aliases = value.aliases() == null
                    ? new LinkedHashMap<>()
                    : new LinkedHashMap<>(value.aliases());
            if (!test && value.dependencies() != null) {
                Map<Boolean, Set<String>> tokens = value.dependencies().entrySet().stream()
                        .collect(Collectors.partitioningBy(dep -> dep.getValue().scope() == MavenDependencyScope.TEST,
                                Collectors.mapping(dep -> dep.getKey().coordinate(null, null), Collectors.toSet())));
                aliases.values().removeIf(token -> tokens.get(true).contains(token) && !tokens.get(false).contains(token));
            }
            if (!aliases.isEmpty()) {
                properties.setProperty("aliases", aliases.entrySet().stream()
                        .map(alias -> alias.getKey() + "=" + alias.getValue())
                        .collect(Collectors.joining("\t")));
            }
            if (value.signatures() != null && !value.signatures().isEmpty()) {
                properties.setProperty("signatures", value.signatures().entrySet().stream()
                        .map(signature -> signature.getKey() + "=" + signature.getValue())
                        .collect(Collectors.joining("\t")));
            }
            properties.setProperty("checksums",
                    value.dependencies() == null ? "" : value.dependencies().entrySet().stream()
                            .filter(dep -> dep.getValue().checksum() != null
                                    && (test
                                            ? dep.getValue().scope() == MavenDependencyScope.TEST
                                            : dep.getValue().scope() != MavenDependencyScope.TEST))
                            .map(dep -> dep.getKey().coordinate(prefix, dep.getValue().version())
                                    + "=" + dep.getValue().checksum())
                            .collect(Collectors.joining("\t")));
            String sourceDirectory = test ? value.testSourceDirectory() : value.sourceDirectory();
            properties.setProperty("sources.0", sourceDirectory == null
                    ? (test ? "src/test/java" : "src/main/java")
                    : sourceDirectory.replace(File.separatorChar, '/'));
            for (int index = 0; index < sources.size(); index++) {
                properties.setProperty("sources." + (index + 1), "src/" + (test ? "test" : "main") + "/" + sources.get(index));
            }
            List<String> resourceDirectories = test ? value.testResourceDirectories() : value.resourceDirectories();
            List<String> resources = resourceDirectories == null
                    ? List.of(test ? "src/test/resources" : "src/main/resources")
                    : resourceDirectories.stream()
                            .map(directory -> directory.replace(File.separatorChar, '/'))
                            .sorted()
                            .toList();
            for (int index = 0; index < resources.size(); index++) {
                properties.setProperty("resources." + index, resources.get(index));
            }
            value.metadata().properties().forEach((key, metadata) -> properties.setProperty(POM_METADATA + key, metadata));
            if (test) {
                properties.setProperty("test", "true");
            } else {
                properties.setProperty("tests", tests);
            }
            properties.store(file);
        }
    }

    public record MavenModuleDescriptor(String name,
                                        SequencedSet<String> dependencies,
                                        SequencedSet<String> resources,
                                        SequencedSet<String> spdx,
                                        Path location,
                                        boolean test) implements ProjectModule {

        public List<Path> configurations() {
            if (location == null) {
                return Collections.emptyList();
            }
            return List.of(
                    location.resolve("src")
                            .resolve(test ? "test" : "main")
                            .resolve("build.jenesis"),
                    location.resolve("build.jenesis"));
        }

        @Override
        public SequencedSet<String> sources() {
            return of(BuildExecutorModule.PREVIOUS + SOURCES);
        }

        @Override
        public SequencedSet<String> manifests() {
            return of(BuildExecutorModule.PREVIOUS + MANIFESTS);
        }

        @Override
        public SequencedSet<String> coordinates() {
            return of(BuildExecutorModule.PREVIOUS + COORDINATES);
        }

        @Override
        public SequencedSet<String> artifacts() {
            return of(BuildExecutorModule.PREVIOUS + DEPENDENCIES + "/" + ARTIFACTS);
        }

        private static SequencedSet<String> of(String value) {
            return Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(value)));
        }
    }
}

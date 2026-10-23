package build.jenesis.module;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.PathPlacement;
import build.jenesis.Pinning;
import build.jenesis.Platform;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.maven.MavenModuleRepository;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.MultiProjectDependencies;
import build.jenesis.project.MultiProjectModule;
import build.jenesis.project.ProjectModule;
import build.jenesis.step.Assign;
import build.jenesis.step.Bind;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Inventory;
import build.jenesis.step.Javac;
import build.jenesis.step.Versions;
import java.util.jar.Attributes;

import static build.jenesis.project.MultiProjectModule.ARTIFACTS;
import static build.jenesis.project.MultiProjectModule.ASSIGN;
import static build.jenesis.project.MultiProjectModule.COORDINATES;
import static build.jenesis.project.MultiProjectModule.DEPENDENCIES;
import static build.jenesis.project.MultiProjectModule.MANIFESTS;
import static build.jenesis.project.MultiProjectModule.PREPARE;
import static build.jenesis.project.MultiProjectModule.PRODUCE;
import static build.jenesis.project.MultiProjectModule.SOURCES;

public class ModularProject implements BuildExecutorModule {

    private static final String SIBLING_MODULE_PREFIX = MultiProjectModule.MODULE + "-";

    private final String group;
    private final String prefix;
    private final Path root;
    private final Predicate<Path> filter;
    private final boolean modular;
    private final Platform platform;
    private final SequencedSet<Path> boms;
    private final SequencedSet<Path> signatures;
    private final int segments;
    private final boolean maven;

    public ModularProject(String prefix, Path root) {
        this("main",
                prefix,
                root,
                _ -> true,
                true,
                new Platform(),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                MavenModuleRepository.segments(),
                true);
    }

    public static ModularProject ofEnvironment(Environment environment, String prefix, Path root) {
        ModularProject project = new ModularProject(prefix, root).platform(Platform.ofEnvironment(environment));
        Integer segments = environment.numberOrNull("maven.segments");
        return segments == null ? project : project.segments(segments);
    }

    private ModularProject(String group,
                           String prefix,
                           Path root,
                           Predicate<Path> filter,
                           boolean modular,
                           Platform platform,
                           SequencedSet<Path> boms,
                           SequencedSet<Path> signatures,
                           int segments,
                           boolean maven) {
        this.group = group;
        this.prefix = prefix;
        this.root = root;
        this.filter = filter;
        this.modular = modular;
        this.platform = platform;
        this.boms = boms;
        this.signatures = signatures;
        this.segments = segments;
        this.maven = maven;
    }

    public ModularProject group(String group) {
        return new ModularProject(group, prefix, root, filter, modular, platform, boms, signatures, segments, maven);
    }

    public ModularProject filter(Predicate<Path> filter) {
        return new ModularProject(group, prefix, root, filter, modular, platform, boms, signatures, segments, maven);
    }

    public ModularProject modular(boolean modular) {
        return new ModularProject(group, prefix, root, filter, modular, platform, boms, signatures, segments, maven);
    }

    public ModularProject platform(Platform platform) {
        return new ModularProject(group, prefix, root, filter, modular, platform, boms, signatures, segments, maven);
    }

    public ModularProject boms(SequencedSet<Path> boms) {
        return new ModularProject(group, prefix, root, filter, modular, platform, boms, signatures, segments, maven);
    }

    public ModularProject signatures(SequencedSet<Path> signatures) {
        return new ModularProject(group, prefix, root, filter, modular, platform, boms, signatures, segments, maven);
    }

    public ModularProject segments(int segments) {
        return new ModularProject(group,
                prefix,
                root,
                filter,
                modular,
                platform,
                boms,
                signatures,
                MavenModuleRepository.checkedSegments(segments),
                maven);
    }

    public ModularProject maven(boolean maven) {
        return new ModularProject(group, prefix, root, filter, modular, platform, boms, signatures, segments, maven);
    }

    public static BuildExecutorModule make(Environment environment,
                                           Path root,
                                           MultiProjectAssembler<? super ModularModuleDescriptor> assembler) {
        return make(environment,
                root,
                "main",
                "module",
                _ -> true,
                Map.of("module", JenesisRepository.ofEnvironment(environment, JenesisRepository.Scope.MODULE)),
                Map.of("module", ModularJarResolver.ofEnvironment(environment, false)),
                null,
                true,
                false,
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableSet(),
                assembler);
    }

    public static BuildExecutorModule make(Environment environment,
                                           Path root,
                                           String group,
                                           String prefix,
                                           Predicate<Path> filter,
                                           Map<String, Repository> repositories,
                                           Map<String, Resolver> resolvers,
                                           Pinning pinning,
                                           boolean modular,
                                           boolean maven,
                                           SequencedSet<Path> spdx,
                                           SequencedSet<Path> boms,
                                           SequencedSet<Path> signatures,
                                           MultiProjectAssembler<? super ModularModuleDescriptor> assembler) {
        Dependencies dependencyModule = Dependencies.ofEnvironment(environment, repositories, resolvers);
        Consumer<String> printing = environment.flag("print.progress", true) ? environment.out() : null;
        Palette palette = Palette.ofEnvironment(environment);
        return new MultiProjectModule(ModularProject.ofEnvironment(environment, prefix, root)
                .group(group).filter(filter).modular(modular).maven(maven).boms(boms).signatures(signatures),
                identity -> Optional.of(identity.substring(0, identity.indexOf('/'))),
                _ -> (name, dependencies, arguments) -> {
                    Path location = MultiProjectModule.location(root, arguments);
                    if (printing != null) {
                        for (Map.Entry<String, Path> argument : arguments.entrySet()) {
                            Path module = argument.getValue().resolve(BuildStep.MODULE);
                            if (argument.getKey().endsWith("/" + MANIFESTS) && Files.isRegularFile(module)) {
                                SequencedProperties properties = SequencedProperties.ofFiles(module);
                                if (properties.value("release") == null) {
                                    printing.accept(("%s%-11s%s %s compiles for release %d, the JDK the build runs on,"
                                            + " as %s declares no @jenesis.release - @jenesis.release sets it")
                                            .formatted(palette.warning(),
                                                    "[RELEASE]",
                                                    palette.reset(),
                                                    properties.getProperty("module"),
                                                    Runtime.version().feature(),
                                                    Path.of(properties.getProperty("path")).resolve("module-info.java")));
                                }
                            }
                        }
                    }
                    SequencedSet<String> spdxInherited = new LinkedHashSet<>();
                    for (int index = 0; index < spdx.size(); index++) {
                        spdxInherited.add(BuildExecutorModule.PREVIOUS + MultiProjectModule.SPDX + "-" + index);
                    }
                    AssemblyDescriptor packaging = assembler.apply(
                            new ModularModuleDescriptor(name, dependencies.sequencedKeySet(), spdxInherited, location),
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
                                    (BiFunction<URI, String, Optional<URI>> & Serializable) (base, _) -> Optional.of(base),
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
                                dependencyModule.repositories(mergedRepositories).pinning(pinning),
                                artifactInputs);
                    }, dependencyDeps);
                    SequencedMap<String, String> produceDeps = new LinkedHashMap<>();
                    produceDeps.put(MultiProjectModule.IDENTIFIER_PATH + name + "/" + SOURCES, SOURCES);
                    produceDeps.put(MultiProjectModule.IDENTIFIER_PATH + name + "/" + MANIFESTS, MANIFESTS);
                    produceDeps.put(MultiProjectModule.IDENTIFIER_PATH + name + "/" + COORDINATES, COORDINATES);
                    produceDeps.put(DEPENDENCIES + "/" + ARTIFACTS, DEPENDENCIES + "/" + ARTIFACTS);
                    for (String source : spdxSources) {
                        produceDeps.put(source, source);
                    }
                    for (String key : inherited.sequencedKeySet()) {
                        produceDeps.putIfAbsent(key, key);
                    }
                    buildExecutor.addModule(PRODUCE,
                            assembler.apply(new ModularModuleDescriptor(name, dependencies.sequencedKeySet(), spdxInherited, location),
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

    public static BuildStep manifests(String group, String prefix, Platform platform) {
        return new Manifests(group,
                prefix,
                "",
                true,
                platform,
                Runtime.version().feature(),
                MavenModuleRepository.segments(),
                true);
    }

    private record Coordinates(String prefix) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties module = SequencedProperties.ofFiles(arguments.get(MANIFESTS)
                    .folder()
                    .resolve(BuildStep.MODULE));
            SequencedProperties coordinates = new SequencedProperties();
            coordinates.setProperty(prefix + "/" + module.getProperty("module"), "");
            coordinates.store(context.next().resolve(BuildStep.IDENTITY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Manifests(String group,
                             String prefix,
                             String path,
                             boolean modular,
                             Platform platform,
                             int feature,
                             int segments,
                             boolean maven) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path descriptor = arguments.get("sources").folder().resolve(BuildStep.SOURCES).resolve("module-info.java");
            if (!Files.isRegularFile(descriptor)) {
                throw new IllegalStateException("The sources of " + arguments.get("sources").folder()
                        + " are not modular (missing module-info.java): declare the module in a module-info.java");
            }
            ModuleInfo info = new ModuleInfoParser(group).identify(descriptor);
            if (info.testOf() != null && !info.testOf().isEmpty() && !info.requires().contains(info.testOf())) {
                throw new IllegalStateException("Test module '"
                        + info.coordinate()
                        + "' declares @jenesis.test "
                        + info.testOf()
                        + " but does not 'requires "
                        + info.testOf()
                        + ";' (declared requires: "
                        + info.requires()
                        + ")");
            }
            if (!info.layers().isEmpty() && !info.requires().contains("build.jenesis.launcher")) {
                throw new IllegalStateException("Module '"
                        + info.coordinate()
                        + "' declares a layer but does not 'requires build.jenesis.launcher;' - a layer is"
                        + " defined by the module that declares it, through the launcher's own API");
            }
            for (Map.Entry<String, SequencedSet<String>> layer : info.layers().entrySet()) {
                String api = info.layerApis().get(layer.getKey());
                if (api == null) {
                    throw new IllegalStateException("Layer "
                            + layer.getKey()
                            + " of module '"
                            + info.coordinate()
                            + "' names no API module - declare @jenesis.layer "
                            + layer.getKey()
                            + " api <module>, the one module the layer shares with this one");
                }
                if (layer.getValue().isEmpty()) {
                    throw new IllegalStateException("Layer "
                            + layer.getKey()
                            + " of module '"
                            + info.coordinate()
                            + "' isolates nothing - declare @jenesis.layer "
                            + layer.getKey()
                            + " provider <token>, the root the layer holds");
                }
                if (!info.requires().contains(api)) {
                    throw new IllegalStateException("Module '"
                            + info.coordinate()
                            + "' shares "
                            + api
                            + " with layer "
                            + layer.getKey()
                            + " but does not require it - a layer's API module is read from its host");
                }
                for (String coordinate : layer.getValue()) {
                    String module = coordinate.startsWith("module/") ? coordinate.substring(7) : null;
                    if (module != null && info.requires().contains(module)) {
                        throw new IllegalStateException("Module '"
                                + info.coordinate()
                                + "' requires "
                                + module
                                + ", which it isolates in layer "
                                + layer.getKey()
                                + ": an isolated module is not on this module's path, so reach it through a"
                                + " service the layer provides");
                    }
                }
            }
            SequencedMap<String, String> versions = new LinkedHashMap<>(info.versions());
            for (Map.Entry<String, SequencedMap<String, String>> variant : info.variants().entrySet()) {
                String selected = platform.select(variant.getKey(),
                        versions.get(variant.getKey()),
                        variant.getValue());
                if (selected != null) {
                    versions.put(variant.getKey(), selected);
                }
            }
            if (!maven && !info.aliases().isEmpty()) {
                Map.Entry<String, String> alias = info.aliases().firstEntry();
                throw new IllegalArgumentException("@jenesis.alias " + alias.getKey() + " " + alias.getValue() + " in "
                        + Path.of(path).resolve("module-info.java") + " names a Maven artifact, which the modular layout"
                        + " does not resolve, as it reads modules by name from the module repository alone - build"
                        + " with -Djenesis.project.layout=modular_to_maven, or require the module by a name the module"
                        + " repository serves, whose variant @jenesis.pin <module> :<classifier> selects");
            }
            for (Map.Entry<String, String> alias : info.aliases().entrySet()) {
                String named = versions.remove(group + "/" + prefix + "/" + alias.getKey());
                if (named != null) {
                    versions.putIfAbsent(group + "/maven/" + alias.getValue(), named);
                }
            }
            SequencedMap<String, String> targets = new LinkedHashMap<>(info.aliases());
            for (Map.Entry<String, String> alias : targets.entrySet()) {
                String declared = versions.get(group + "/maven/" + alias.getValue());
                if (declared == null || !declared.startsWith(":")) {
                    continue;
                }
                int space = declared.indexOf(' ');
                String qualifier = space < 0 ? declared : declared.substring(0, space);
                int divider = qualifier.indexOf(':', 1);
                String classifier = divider < 0 ? qualifier.substring(1) : qualifier.substring(1, divider);
                String[] segments = alias.getValue().split("/");
                String target = segments[0] + "/" + segments[1] + "/" + (segments.length > 2 ? segments[2] : "jar")
                        + "/" + classifier;
                versions.remove(group + "/maven/" + alias.getValue());
                if (divider > 0) {
                    versions.put(group + "/maven/" + target,
                            qualifier.substring(divider + 1) + (space < 0 ? "" : declared.substring(space)));
                }
                alias.setValue(target);
            }
            SequencedProperties requires = new SequencedProperties();
            for (String dependency : info.requires()) {
                String target = targets.get(dependency);
                String coordinate = target == null
                        ? prefix + "/" + dependency
                        : "maven/" + target + pinned(versions, group + "/maven/" + target);
                requires.setProperty(group + "/compile/" + coordinate, "");
                if (info.runtimeRequires().contains(dependency)) {
                    requires.setProperty(group + "/runtime/" + coordinate, "");
                }
            }
            info.plugins().forEach((coordinate, group) ->
                    requires.setProperty(group + "/plugin/" + coordinate
                            + (coordinate.startsWith("maven/") && coordinate.split("/").length == 3
                                    ? "/RELEASE"
                                    : ""), ""));
            info.layers().forEach((layer, coordinates) -> coordinates.forEach(coordinate ->
                    requires.setProperty("layer:"
                            + layer
                            + "/runtime/"
                            + coordinate
                            + (coordinate.startsWith("module/")
                                    ? ""
                                    : version(versions, "layer:" + layer + "/" + coordinate)), "")));
            info.attachments().forEach((key, _) -> {
                int slash = key.indexOf('/');
                int second = key.indexOf('/', slash + 1);
                String repository = key.substring(slash + 1, second);
                String coordinate = key.substring(second + 1);
                if (repository.equals(prefix) && info.requires().contains(coordinate)) {
                    return;
                }
                requires.setProperty(key.substring(0, slash)
                        + "/agent/"
                        + key.substring(slash + 1)
                        + (repository.equals("module") ? "" : version(versions, key)), "");
            });
            requires.store(context.next().resolve(BuildStep.REQUIRES));
            if (!info.layers().isEmpty()) {
                SequencedProperties layers = new SequencedProperties();
                info.layers().keySet().forEach(layer -> layers.setProperty(layer,
                        info.coordinate() + " " + info.layerApis().get(layer)));
                layers.store(context.next().resolve(BuildStep.LAYERS));
            }
            if (!info.attachments().isEmpty()) {
                SequencedProperties attachments = new SequencedProperties();
                info.attachments().forEach((key, options) -> {
                    int slash = key.indexOf('/');
                    attachments.setProperty(key.substring(0, slash) + "/agent/" + key.substring(slash + 1), options);
                });
                attachments.store(context.next().resolve(BuildStep.ATTACHMENTS));
            }
            String self = group + "/module/" + info.coordinate();
            SequencedProperties natives = new SequencedProperties();
            List<String> named = new ArrayList<>();
            for (String key : info.natives()) {
                int slash = key.indexOf('/');
                String coordinate = key.substring(slash + 1);
                named.add(!key.startsWith(group + "/")
                        ? key
                        : coordinate.startsWith("module/")
                                ? coordinate.substring(7)
                                : coordinate.startsWith("maven/") && coordinate.indexOf('/', 6) == coordinate.lastIndexOf('/')
                                        ? coordinate.substring(6)
                                        : key);
                if (!key.equals(self)) {
                    natives.setProperty(key.substring(0, slash) + "/native/" + coordinate, "");
                }
            }
            if (!natives.isEmpty()) {
                natives.store(context.next().resolve(BuildStep.NATIVES));
            }
            if (!targets.isEmpty()) {
                SequencedProperties aliases = new SequencedProperties();
                for (Map.Entry<String, String> entry : targets.entrySet()) {
                    aliases.setProperty(group + "/" + prefix + "/" + entry.getKey(), entry.getValue());
                }
                aliases.store(context.next().resolve(BuildStep.ALIASES));
            }
            if (!info.overrides().isEmpty()) {
                SequencedProperties overrides = new SequencedProperties();
                for (Map.Entry<String, SequencedSet<String>> entry : info.overrides().entrySet()) {
                    overrides.setProperty(group + "/" + prefix + "/" + entry.getKey(),
                            String.join(" ", entry.getValue()));
                }
                overrides.store(context.next().resolve(BuildStep.OVERRIDES));
            }
            boolean preview = info.release() != null && info.release().endsWith("-preview");
            if (!targets.isEmpty() || !info.overrides().isEmpty() || !info.layers().isEmpty() || !named.isEmpty() || preview) {
                Manifest manifest = new Manifest();
                manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
                if (!named.isEmpty()) {
                    manifest.getMainAttributes().putValue(PathPlacement.NATIVE_ACCESS, String.join(",", named));
                }
                if (preview) {
                    manifest.getMainAttributes().putValue(PathPlacement.PREVIEW,
                            info.release().substring(0, info.release().length() - "-preview".length()));
                }
                if (!targets.isEmpty()) {
                    List<String> declarations = new ArrayList<>();
                    targets.forEach((alias, target) -> declarations.add(alias + "=" + target));
                    manifest.getMainAttributes().putValue(PathPlacement.ALIASES, String.join(",", declarations));
                }
                if (!info.overrides().isEmpty()) {
                    List<String> declarations = new ArrayList<>();
                    info.overrides().forEach((module, carriers) ->
                            declarations.add(module + "=" + String.join(" ", carriers)));
                    manifest.getMainAttributes().putValue(PathPlacement.OVERRIDES, String.join(",", declarations));
                }
                if (!info.layers().isEmpty()) {
                    List<String> declarations = new ArrayList<>();
                    info.layers().forEach((layer, coordinates) -> {
                        List<String> tokens = new ArrayList<>(List.of(info.layerApis().get(layer)));
                        coordinates.forEach(coordinate -> tokens.add(coordinate
                                + (coordinate.startsWith("module/")
                                        ? ""
                                        : version(versions, "layer:" + layer + "/" + coordinate))));
                        declarations.add(layer + "=" + String.join(" ", tokens));
                    });
                    manifest.getMainAttributes().putValue(PathPlacement.LAYERS, String.join(",", declarations));
                }
                try (OutputStream out = Files.newOutputStream(context.next().resolve(Versions.MANIFEST))) {
                    manifest.write(out);
                }
            }
            if (!info.excludes().isEmpty()) {
                SequencedProperties exclusions = new SequencedProperties();
                for (Map.Entry<String, SequencedSet<String>> entry : info.excludes().entrySet()) {
                    if (!info.requires().contains(entry.getKey())) {
                        throw new IllegalArgumentException("Cannot apply @jenesis.exclude to "
                                + entry.getKey()
                                + " which is not required by "
                                + info.coordinate()
                                + " (declared requires: "
                                + info.requires()
                                + ")");
                    }
                    String target = targets.get(entry.getKey());
                    String coordinate = target == null
                            ? prefix + "/" + entry.getKey()
                            : "maven/" + target + pinned(versions, group + "/maven/" + target);
                    String excluded = String.join(",", entry.getValue());
                    exclusions.setProperty(group + "/compile/" + coordinate, excluded);
                    if (info.runtimeRequires().contains(entry.getKey())) {
                        exclusions.setProperty(group + "/runtime/" + coordinate, excluded);
                    }
                }
                exclusions.store(context.next().resolve(BuildStep.EXCLUSIONS));
            }
            if (!versions.isEmpty()) {
                SequencedProperties properties = new SequencedProperties();
                versions.forEach(properties::setProperty);
                properties.store(context.next().resolve(BuildStep.VERSIONS));
            }
            if (!info.signatures().isEmpty()) {
                SequencedMap<String, SequencedSet<String>> declaredKeys = new TreeMap<>();
                for (Map.Entry<String, String> entry : info.signatures().entrySet()) {
                    int slash = entry.getKey().indexOf('/');
                    String name = entry.getKey().substring(slash + 1);
                    if (!entry.getValue().isEmpty()
                            || name.indexOf('/') >= 0
                            || !name.startsWith("signature-")
                            || !name.endsWith(".properties")) {
                        declaredKeys.computeIfAbsent(entry.getKey(), _ -> new TreeSet<>())
                                .addAll(List.of(entry.getValue().split(" ")));
                        continue;
                    }
                    Path file = null;
                    for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                        if (argument.getValue().removed()) {
                            continue;
                        }
                        if (argument.getKey().startsWith("signatures-")) {
                            Path candidate = argument.getValue().folder().resolve(name);
                            if (Files.isRegularFile(candidate)) {
                                file = candidate;
                                break;
                            }
                        }
                    }
                    if (file == null) {
                        throw new IllegalStateException("No " + name + " found in any location of"
                                + " jenesis.project.signatures; a key list is only ever read from disk");
                    }
                    String qualifier = entry.getKey().substring(0, slash);
                    SequencedProperties listed = SequencedProperties.ofFiles(file);
                    for (String fingerprint : listed.stringPropertyNames()) {
                        SequencedSet<String> tokens = declaredKeys.computeIfAbsent(fingerprint, _ -> new TreeSet<>());
                        for (String token : listed.getProperty(fingerprint).trim().split("\\s+")) {
                            if (token.isEmpty()) {
                                continue;
                            }
                            boolean wildcard = token.endsWith("/*");
                            String base = wildcard ? token.substring(0, token.length() - 2) : token;
                            int first = base.indexOf('/');
                            String expanded;
                            if (first < 0) {
                                expanded = qualifier + (wildcard ? "/maven/" : "/module/") + base;
                            } else if (base.indexOf('/', first + 1) < 0) {
                                expanded = qualifier + "/maven/" + base;
                            } else {
                                expanded = base;
                            }
                            tokens.add(wildcard ? expanded + "/*" : expanded);
                        }
                    }
                }
                SequencedProperties properties = new SequencedProperties();
                declaredKeys.forEach((fingerprint, tokens) ->
                        properties.setProperty(fingerprint, String.join(" ", tokens)));
                properties.store(context.next().resolve(BuildStep.SIGNATURES));
            }
            SequencedMap<String, String> declared = new LinkedHashMap<>(info.boms());
            for (Map.Entry<String, SequencedMap<String, String>> variant : info.bomVariants().entrySet()) {
                String selected = platform.select(variant.getKey(),
                        declared.get(variant.getKey()),
                        variant.getValue());
                if (selected != null) {
                    declared.put(variant.getKey(), selected);
                }
            }
            if (!declared.isEmpty()) {
                SequencedProperties boms = new SequencedProperties();
                for (Map.Entry<String, String> entry : declared.entrySet()) {
                    int slash = entry.getKey().indexOf('/');
                    String name = entry.getKey().substring(slash + 1);
                    if (name.indexOf('/') >= 0 || !name.startsWith("pin-") || !name.endsWith(".properties")) {
                        boms.setProperty("bom/" + entry.getKey(), entry.getValue());
                        continue;
                    }
                    Path file = null;
                    for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                        if (argument.getValue().removed()) {
                            continue;
                        }
                        if (argument.getKey().startsWith("boms-")) {
                            Path candidate = argument.getValue().folder().resolve(name);
                            if (Files.isRegularFile(candidate)) {
                                file = candidate;
                                break;
                            }
                        }
                    }
                    if (file == null) {
                        throw new IllegalArgumentException("Local BOM not found: " + entry.getKey());
                    }
                    Dependencies.bomEntries(SequencedProperties.ofFiles(file),
                            entry.getKey().substring(0, slash)).forEach((key, value) -> {
                        if (!boms.containsKey("entry/" + key)) {
                            boms.setProperty("entry/" + key, value);
                        }
                    });
                }
                boms.store(context.next().resolve(BuildStep.BOMS));
            }
            Javac.writeRelease(context.next(), info.release(), feature);
            SequencedProperties module = new SequencedProperties();
            module.setProperty("path", path);
            module.setProperty("module", info.coordinate());
            module.setProperty("modular", Boolean.toString(modular));
            if (info.release() != null) {
                module.setProperty("release", info.release());
            }
            if (info.testOf() != null) {
                module.setProperty("test", info.testOf());
            }
            if (info.abstractTest()) {
                module.setProperty("abstract", "true");
            }
            if (info.main() != null) {
                module.setProperty("main", info.main());
            }
            if (info.natives().contains(self)) {
                module.setProperty("native", "true");
            }
            module.store(context.next().resolve(BuildStep.MODULE));
            SequencedProperties metadata = new SequencedProperties();
            String moduleName = info.coordinate();
            if (maven) {
                metadata.setProperty("project", MavenModuleRepository.groupId(moduleName, segments));
            }
            metadata.setProperty("artifact", moduleName);
            if (info.name() != null) {
                metadata.setProperty("name", info.name());
            }
            if (info.description() != null) {
                metadata.setProperty("description", info.description());
            }
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path upstream = argument.folder().resolve(BuildStep.METADATA);
                if (Files.isRegularFile(upstream)) {
                    SequencedProperties.ofFiles(upstream).forEach(metadata::put);
                }
            }
            Path own = arguments.get("sources").folder()
                    .resolve(BuildStep.SOURCES)
                    .resolve("META-INF/build.jenesis/project.properties");
            if (Files.isRegularFile(own)) {
                SequencedProperties.ofFiles(own).forEach(metadata::put);
            }
            metadata.store(context.next().resolve(BuildStep.METADATA));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static String version(SequencedMap<String, String> versions, String key) {
            String bare = bare(versions, key);
            return "/" + (bare.isEmpty() ? "RELEASE" : bare);
        }

        private static String pinned(SequencedMap<String, String> versions, String key) {
            String bare = bare(versions, key);
            return bare.isEmpty() ? "" : "/" + bare;
        }

        private static String bare(SequencedMap<String, String> versions, String key) {
            String pinned = versions.get(key);
            int space = pinned == null ? -1 : pinned.indexOf(' ');
            String bare = pinned == null ? "" : space < 0 ? pinned : pinned.substring(0, space);
            return bare.startsWith(":") ? "" : bare;
        }
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
        List<Path> moduleInfos = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                if (file.getFileName().toString().equals("module-info.java")) {
                    moduleInfos.add(file);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                if (!dir.equals(root) && Files.exists(dir.resolve(BuildExecutor.SKIP_MARKER))) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }
        });
        moduleInfos.sort(null);
        SequencedMap<String, Path> bomFiles = new LinkedHashMap<>();
        for (Path folder : boms) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(Files::isRegularFile)
                        .filter(file -> {
                            String name = file.getFileName().toString();
                            return name.startsWith("pin-") && name.endsWith(".properties");
                        })
                        .sorted()
                        .forEach(file -> bomFiles.putIfAbsent(file.getFileName().toString(), file));
            }
        }
        SequencedMap<String, Path> signatureFiles = new LinkedHashMap<>();
        for (Path folder : signatures) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (Stream<Path> files = Files.list(folder)) {
                files.filter(Files::isRegularFile)
                        .filter(file -> {
                            String name = file.getFileName().toString();
                            return name.startsWith("signature-") && name.endsWith(".properties");
                        })
                        .sorted()
                        .forEach(file -> signatureFiles.putIfAbsent(file.getFileName().toString(), file));
            }
        }
        for (Path file : moduleInfos) {
            Path parent = file.getParent(), location = root.relativize(parent);
            if (filter.test(location)) {
                String relative = location.toString().replace(File.separatorChar, '/');
                buildExecutor.addModule(SIBLING_MODULE_PREFIX + BuildExecutorModule.encodePath(relative), (module, modInherited) -> {
                    module.addSource("sources", Bind.asSources(), parent);
                    SequencedSet<String> manifestDeps = new LinkedHashSet<>();
                    manifestDeps.add("sources");
                    int index = 0;
                    for (Path bom : bomFiles.values()) {
                        String source = "boms-" + index++;
                        module.addSource(source, new Bind(Map.of(Path.of(""), bom.getFileName())), bom);
                        manifestDeps.add(source);
                    }
                    for (Path signature : signatureFiles.values()) {
                        String source = "signatures-" + index++;
                        module.addSource(source, new Bind(Map.of(Path.of(""), signature.getFileName())), signature);
                        manifestDeps.add(source);
                    }
                    manifestDeps.addAll(modInherited.sequencedKeySet());
                    module.addStep(MANIFESTS, new Manifests(group,
                            prefix,
                            relative,
                            modular,
                            platform,
                            Runtime.version().feature(),
                            segments,
                            maven), manifestDeps);
                    module.addStep(COORDINATES, new Coordinates(prefix), MANIFESTS);
                }, inherited.sequencedKeySet().stream());
            }
        }
    }

    public record ModularModuleDescriptor(String name, SequencedSet<String> dependencies, SequencedSet<String> spdx, Path location) implements ProjectModule {

        @Override
        public SequencedSet<String> sources() {
            return of(BuildExecutorModule.PREVIOUS + SOURCES);
        }

        @Override
        public SequencedSet<String> resources() {
            return Collections.emptyNavigableSet();
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

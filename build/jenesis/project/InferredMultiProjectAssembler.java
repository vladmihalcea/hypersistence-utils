package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.PathPlacement;
import build.jenesis.Repository;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Bind;
import build.jenesis.step.Bundle;
import build.jenesis.step.Classifiers;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Docker;
import build.jenesis.step.Inventory;
import build.jenesis.step.JLink;
import build.jenesis.step.JMod;
import build.jenesis.step.JPackage;
import build.jenesis.step.Jar;
import build.jenesis.step.Layers;
import build.jenesis.step.Legal;
import build.jenesis.step.NativeImage;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.Sbom;
import build.jenesis.step.Versions;

public record InferredMultiProjectAssembler(Function<InferredSourceCodeQualityModule, BuildExecutorModule> check,
                                            Function<InferredSourceFormattingModule, BuildExecutorModule> format,
                                            Function<InferredComplianceModule, BuildExecutorModule> compliance,
                                            Function<InferredJavaToolchainModule, BuildExecutorModule> toolchain,
                                            Function<InferredByteCodeQualityModule, BuildExecutorModule> validate,
                                            Function<InferredArtifactQualityModule, BuildExecutorModule> artifact,
                                            Function<InferredTestObservationModule, BuildExecutorModule> observe,
                                            Function<InferredDocumentationModule, BuildExecutorModule> documentation,
                                            SequencedMap<String, BuildExecutorModule> custom,
                                            SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins,
                                            SequencedMap<Path, Path> resources,
                                            Environment environment) implements MultiProjectAssembler<ProjectModuleDescriptor> {

    private static final List<String> HOOK_POINTS = List.of("",
            "check",
            "format",
            "compliance",
            "binary",
            "binary/generated",
            "binary/compiled",
            "binary/transform",
            "binary/validate",
            "artifact",
            "observed",
            "observe",
            "test",
            "language",
            "package",
            "documentation",
            "documentation/generate");

    public InferredMultiProjectAssembler() {
        this(Environment.NONE);
    }

    private InferredMultiProjectAssembler(Environment environment) {
        this(module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                module -> module,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                environment);
    }

    public static InferredMultiProjectAssembler ofEnvironment(Environment environment) {
        return new InferredMultiProjectAssembler(environment);
    }

    public InferredMultiProjectAssembler check(Function<InferredSourceCodeQualityModule, BuildExecutorModule> check) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler format(Function<InferredSourceFormattingModule, BuildExecutorModule> format) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler compliance(Function<InferredComplianceModule, BuildExecutorModule> compliance) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler toolchain(Function<InferredJavaToolchainModule, BuildExecutorModule> toolchain) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler validate(Function<InferredByteCodeQualityModule, BuildExecutorModule> validate) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler artifact(Function<InferredArtifactQualityModule, BuildExecutorModule> artifact) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler observe(Function<InferredTestObservationModule, BuildExecutorModule> observe) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler documentation(Function<InferredDocumentationModule, BuildExecutorModule> documentation) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler custom(SequencedMap<String, BuildExecutorModule> custom) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler plugins(SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins) {
        for (String key : plugins.keySet()) {
            int plus = key.indexOf('+');
            String name = plus == -1 ? key : key.substring(0, plus);
            if (name.isEmpty() || name.contains("/") || plus != -1 && !HOOK_POINTS.contains(key.substring(plus + 1))
                    || plus == key.length() - 1) {
                throw new IllegalArgumentException("Cannot add the plugin " + key + " - name a plugin as <name>+<hook point>"
                        + " with a hook point of " + HOOK_POINTS.stream().filter(hook -> !hook.isEmpty()).toList()
                        + ", or as <name> alone for the module build, where the name holds no / or +");
            }
        }
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler resources(SequencedMap<Path, Path> resources) {
        return new InferredMultiProjectAssembler(check,
                format,
                compliance,
                toolchain,
                validate,
                artifact,
                observe,
                documentation,
                custom,
                plugins,
                resources,
                environment);
    }

    public InferredMultiProjectAssembler custom(String name, BuildExecutorModule module) {
        if (custom.containsKey(name)) {
            throw new IllegalArgumentException("A custom module named " + name + " is added already - give this one"
                    + " another name");
        }
        SequencedMap<String, BuildExecutorModule> added = new LinkedHashMap<>(custom);
        added.put(name, module);
        return custom(added);
    }

    public InferredMultiProjectAssembler custom(String name, BuildStep step) {
        return custom(name, step.asModule(name));
    }

    @Override
    public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                    Map<String, Repository> repositories,
                                    Map<String, Resolver> resolvers) throws IOException {
        Packaging packaging = Packaging.configured(
                BuildStep.locate(descriptor.configuration(), "packaging.properties"));
        SequencedMap<String, SequencedMap<String, String>> overrides = perTool(descriptor.configuration(), "process-"),
                environments = perTool(descriptor.configuration(), "environment-");
        ProcessHandler.Factory factory = ProcessHandler.Factory.ofEnvironment(environment);
        ModularizeModule modules = descriptor.pathPlacement() == PathPlacement.MODULE_PATH
                ? null
                : modularized(ModularizeModule.ofEnvironment(environment, factory),
                        BuildStep.locate(descriptor.configuration(), "modules.properties"));
        SequencedMap<String, SequencedMap<String, BuildExecutorModule>> hooks = new LinkedHashMap<>();
        hooks.put("", new LinkedHashMap<>(custom));
        for (Map.Entry<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugin : plugins.entrySet()) {
            int plus = plugin.getKey().indexOf('+');
            String name = plus == -1 ? plugin.getKey() : plugin.getKey().substring(0, plus);
            Path file = BuildStep.locate(descriptor.configuration(), ProjectPlugins.PLUGIN + "-" + name + ".properties");
            if (file == null && !plugin.getKey().endsWith("+language")) {
                continue;
            }
            SequencedMap<String, String> properties = new LinkedHashMap<>();
            if (file != null) {
                SequencedProperties.ofFiles(file).forEachProperty(properties::put);
            }
            String hook = plus == -1 ? "" : plugin.getKey().substring(plus + 1);
            SequencedMap<String, BuildExecutorModule> hooked = hooks.computeIfAbsent(hook, _ -> new LinkedHashMap<>());
            BuildExecutorModule module = plugin.getValue().apply(descriptor.location(), properties);
            if (module instanceof PluginModule<?> tooled) {
                module = tooled.repositories(ProjectPlugins.repositories(repositories, environment))
                        .resolvers(ProjectPlugins.resolvers(resolvers, environment));
            }
            if (hooked.putIfAbsent(name, module) != null) {
                throw new IllegalArgumentException("The plugin " + plugin.getKey() + " takes the name of a custom module"
                        + " that is added already - give the plugin another name");
            }
        }
        SequencedMap<String, BuildExecutorModule> none = Collections.emptyNavigableMap();
        SequencedMap<String, BuildExecutorModule> observers = hooks.getOrDefault("observe", none),
                frameworks = hooks.getOrDefault("test", none),
                languages = hooks.getOrDefault("language", none);
        Classifiers classifiers = Classifiers.configured(descriptor.configuration(), descriptor.location(), descriptor.root());
        Sbom sbom = environment.flag("sbom.cyclonedx", true)
                ? Sbom.configured(BuildStep.locate(descriptor.configuration(), "sbom.properties"))
                : null;
        AssemblyDescriptor assembly = AssemblyDescriptor.of((sub, outerInherited) -> {
            SequencedSet<String> closure = new LinkedHashSet<>(descriptor.artifacts());
            if (modules != null) {
                sub.addModule("modules", modules, descriptor.artifacts().stream());
                closure = new LinkedHashSet<>(Set.of("modules"));
            }
            sub.addStep("prepare",
                    new Prepare(descriptor.pathPlacement(),
                            packaging.stage(),
                            List.copyOf(packaging.formats()),
                            List.copyOf(descriptor.manifests()),
                            overrides,
                            environments),
                    outerInherited.sequencedKeySet().stream());
            if (sbom != null) {
                sub.addStep("sbom",
                        sbom,
                        Stream.of(descriptor.manifests().stream(),
                                        descriptor.artifacts().stream(),
                                        descriptor.sources().stream(),
                                        descriptor.resources().stream(),
                                        descriptor.spdx().stream())
                                .flatMap(Function.identity()));
            }
            InferredJavaToolchainModule toolchainModule = InferredJavaToolchainModule.ofEnvironment(environment,
                            descriptor.configuration(),
                            descriptor.location(),
                            descriptor.root(),
                            repositories,
                            resolvers)
                    .pinning(descriptor.pinning())
                    .pathPlacement(descriptor.pathPlacement())
                    .validator(_ -> null)
                    .custom(hooks.getOrDefault("binary", none));
            if (hooks.containsKey("binary/generated")) {
                toolchainModule = toolchainModule.generatorModule(InferredSourceGenerationModule.ofEnvironment(environment,
                                descriptor.configuration(),
                                descriptor.location(),
                                descriptor.root(),
                                repositories,
                                resolvers)
                        .custom(hooks.get("binary/generated")));
            }
            toolchainModule = toolchainModule.compilerModule(InferredCompilerChainModule.ofEnvironment(environment, repositories, resolvers)
                    .includeResources(descriptor.includeResources())
                    .custom(hooks.getOrDefault("binary/compiled", none))
                    .languages(languages));
            if (hooks.containsKey("binary/transform")) {
                toolchainModule = toolchainModule.transformer(new ClassTransformModule(hooks.get("binary/transform")));
            }
            if (!resources.isEmpty()) {
                SequencedMap<Path, Path> bound = new LinkedHashMap<>();
                resources.forEach((target, source) -> bound.put(Path.of(BuildStep.RESOURCES).resolve(target), source));
                sub.addModule("include", Bind.asInputs(new LinkedHashMap<>(Map.of("resources", bound))));
            }
            sub.addModule("binary",
                    toolchain.apply(toolchainModule),
                    Stream.of(
                            Stream.of("prepare"),
                            inputs(descriptor, closure),
                            descriptor.resources().stream(),
                            descriptor.synthetics().stream(),
                            sbom == null ? Stream.<String>empty() : Stream.of("sbom"),
                            resources.isEmpty() ? Stream.<String>empty() : Stream.of("include"))
                            .flatMap(Function.identity()));
            if (classifiers != null) {
                sub.addModule("classifiers",
                        classifiers.timestamp(BuildStep.timestamp(environment)),
                        Stream.concat(Stream.of("binary"), descriptor.manifests().stream()));
            }
            sub.addStep("layers",
                    new Layers(),
                    Stream.concat(Stream.of("binary"), inputs(descriptor, closure)));
            if (descriptor.test()) {
                sub.addModule("observed",
                        observe.apply(
                        InferredTestObservationModule.ofEnvironment(environment, descriptor.configuration(), repositories, resolvers)
                                .pinning(descriptor.pinning())
                                .pathPlacement(descriptor.pathPlacement())
                                .untested(environment.flag("test.jars", true)
                                        ? Collections.emptyNavigableSet()
                                        : new LinkedHashSet<>(List.of("binary/" + JavaToolchainModule.ARTIFACTS)))
                                .custom(hooks.getOrDefault("observed", none))
                                .observers(observers)
                                .frameworks(frameworks)),
                        Stream.of(descriptor.resources().stream(),
                                        resources.isEmpty() ? Stream.<String>empty() : Stream.of("include"),
                                        Stream.of("prepare", "binary", "layers"),
                                        inputs(descriptor, closure))
                                .flatMap(Function.identity()));
            }
            if (packaging.jmod() || packaging.jlink() || packaging.jpackaged() || packaging.nativeImage()) {
                sub.addStep("legal", Legal.ofEnvironment(environment), Stream.concat(Stream.of("binary"), closure.stream()));
            }
            if (packaging.jmod()) {
                sub.addStep("jmod",
                        JMod.ofEnvironment(environment, factory),
                        Stream.of(Stream.of("binary", "legal"),
                                        descriptor.content().stream(),
                                        descriptor.resources().stream(),
                                        descriptor.synthetics().stream(),
                                        sbom == null ? Stream.<String>empty() : Stream.of("sbom"),
                                        resources.isEmpty() ? Stream.<String>empty() : Stream.of("include"))
                                .flatMap(Function.identity()));
            }
            if (!hooks.get("").isEmpty()) {
                sub.addModule("custom", (nested, nestedInherited) -> hooks.get("").forEach((name, module) ->
                        nested.addModule(name, module, nestedInherited.sequencedKeySet())), outerInherited.sequencedKeySet());
            }
        }, AssemblyRequirement.of().upstream(AssemblyDescriptor.BUILD));
        SequencedSet<String> closed = modules == null ? descriptor.artifacts() : new LinkedHashSet<>(Set.of("modules"));
        assembly = assembly.andThen("verify", (sub, inherited) -> {
            sub.addModule("check",
                    check.apply(InferredSourceCodeQualityModule.ofEnvironment(environment,
                                    descriptor.configuration(),
                                    repositories,
                                    resolvers)
                            .custom(hooks.getOrDefault("check", none))),
                    renamed(inherited, Stream.of(descriptor.sources().stream(),
                                    descriptor.spdx().stream(),
                                    descriptor.manifests().stream())
                            .flatMap(Function.identity())));
            sub.addModule("format",
                    format.apply(InferredSourceFormattingModule.ofEnvironment(environment,
                                    descriptor.configuration(),
                                    repositories,
                                    resolvers)
                            .dependencies(closed)
                            .custom(hooks.getOrDefault("format", none))),
                    renamed(inherited, inputs(descriptor, closed)));
            sub.addModule("compliance",
                    compliance.apply(InferredComplianceModule.ofEnvironment(environment, descriptor.configuration())
                            .custom(hooks.getOrDefault("compliance", none))),
                    renamed(inherited, Stream.concat(descriptor.manifests().stream(), descriptor.artifacts().stream())));
            sub.addModule("validate",
                    validate.apply(InferredByteCodeQualityModule.ofEnvironment(environment,
                                    descriptor.configuration(),
                                    repositories,
                                    resolvers)
                            .custom(hooks.getOrDefault("binary/validate", none))),
                    renamed(inherited, Stream.of(Stream.of("binary/" + (hooks.containsKey("binary/transform")
                                            ? JavaToolchainModule.TRANSFORM
                                            : JavaToolchainModule.CLASSES), "prepare"),
                                    inputs(descriptor, closed),
                                    descriptor.resources().stream(),
                                    descriptor.synthetics().stream(),
                                    sbom == null ? Stream.<String>empty() : Stream.of("sbom"),
                                    resources.isEmpty() ? Stream.<String>empty() : Stream.of("include"))
                            .flatMap(Function.identity())));
            sub.addModule("artifact",
                    artifact.apply(InferredArtifactQualityModule.ofEnvironment(environment,
                                    descriptor.configuration(),
                                    repositories,
                                    resolvers)
                            .custom(hooks.getOrDefault("artifact", none))),
                    renamed(inherited, Stream.concat(Stream.of("prepare", "binary"), inputs(descriptor, closed))));
            sub.addStep(MultiProjectModule.INVENTORY,
                    new Inventory().supplement(true),
                    Stream.concat(Stream.of("check", "format", "compliance", "validate", "artifact"),
                            renamed(inherited, descriptor.manifests().stream()).sequencedKeySet().stream()));
        }, AssemblyRequirement.of().after(AssemblyDescriptor.BUILD));
        if (descriptor.documentation()) {
            InferredDocumentationModule documentationModule = InferredDocumentationModule.ofEnvironment(environment,
                            descriptor.configuration(),
                            repositories,
                            resolvers)
                    .custom(hooks.getOrDefault("documentation", none))
                    .generateModule(InferredDocumentationChainModule.ofEnvironment(environment,
                                    descriptor.configuration(),
                                    repositories,
                                    resolvers)
                            .custom(hooks.getOrDefault("documentation/generate", none))
                            .languages(languages));
            BuildExecutorModule documented = documentation.apply(documentationModule);
            assembly = assembly.andThen("document", (sub, inherited) -> {
                sub.addModule("documentation",
                        documented,
                        renamed(inherited, Stream.concat(Stream.of("prepare", "binary"), inputs(descriptor, closed))));
                sub.addStep(MultiProjectModule.INVENTORY,
                        new Inventory().supplement(true),
                        Stream.concat(Stream.of("documentation"),
                                renamed(inherited, descriptor.manifests().stream()).sequencedKeySet().stream()));
            }, AssemblyRequirement.of().after(AssemblyDescriptor.BUILD));
        }
        if (descriptor.source()) {
            assembly = assembly.andThen("source", (sub, inherited) -> {
                sub.addModule("sources",
                        (module, sourcesInherited) -> module.addStep("archive",
                                Jar.ofEnvironment(environment, factory, Jar.Sort.SOURCES),
                                sourcesInherited.sequencedKeySet()),
                        renamed(inherited, Stream.of(descriptor.sources().stream(),
                                        descriptor.resources().stream(),
                                        resources.isEmpty() ? Stream.<String>empty() : Stream.of("include"),
                                        descriptor.manifests().stream(),
                                        Stream.of("binary"))
                                .flatMap(Function.identity())));
                sub.addStep(MultiProjectModule.INVENTORY,
                        new Inventory().supplement(true),
                        Stream.concat(Stream.of("sources"),
                                renamed(inherited, descriptor.manifests().stream()).sequencedKeySet().stream()));
            }, AssemblyRequirement.of().after(AssemblyDescriptor.BUILD));
        }
        SequencedMap<String, BuildExecutorModule> packagers = hooks.getOrDefault("package", none);
        if (packaging.jlink() || packaging.jpackaged() || packaging.bundle() || packaging.launcher() || packaging.nativeImage() || packaging.docker() != null
                || !packagers.isEmpty()) {
            assembly = assembly.andThen("package", (sub, inherited) -> {
                SequencedSet<String> images = new LinkedHashSet<>();
                SequencedSet<String> inputs = new LinkedHashSet<>(inherited.sequencedKeySet());
                SequencedSet<String> identified = Stream.of(descriptor.manifests(),
                                descriptor.coordinates(),
                                descriptor.sources(),
                                descriptor.resources())
                        .flatMap(SequencedSet::stream)
                        .map(InferredMultiProjectAssembler::local)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                inputs.removeIf(key -> identified.contains(phased(key)));
                if (modules != null) {
                    SequencedSet<String> replaced = descriptor.artifacts().stream()
                            .map(InferredMultiProjectAssembler::local)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    inputs.removeIf(key -> replaced.contains(phased(key)));
                }
                SequencedSet<String> linked = new LinkedHashSet<>(inputs);
                if (!packaging.jmod() && (packaging.jlink() || packaging.jpackaged())) {
                    SequencedSet<String> packed = descriptor.resources().stream()
                            .map(InferredMultiProjectAssembler::local)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    sub.addStep("jmod", JMod.ofEnvironment(environment, factory), Stream.concat(inputs.stream(),
                            inherited.sequencedKeySet().stream().filter(key -> packed.contains(phased(key)))));
                    linked.add("jmod");
                }
                if (packaging.jlink()) {
                    sub.addStep("jlink", JLink.ofEnvironment(environment, factory), linked);
                    images.add("jlink");
                }
                for (String format : packaging.formats()) {
                    sub.addStep(packaging.step(format), JPackage.ofEnvironment(environment, factory).type(format), packaging.jlink()
                                ? Stream.concat(Stream.of("jlink"), linked.stream())
                                : linked.stream());
                }
                if (packaging.jpackage().size() > 1) {
                    sub.addStep("jpackage", new Packaged(), packaging.jpackage().stream().map(packaging::step));
                }
                if (!packaging.jpackage().isEmpty()) {
                    images.add("jpackage");
                }
                if (packaging.bundle()) {
                    sub.addStep("bundle", Bundle.ofEnvironment(environment), inputs);
                }
                SequencedSet<String> locals = Stream.of(descriptor.manifests(),
                                descriptor.artifacts(),
                                descriptor.sources(),
                                descriptor.resources(),
                                descriptor.spdx())
                        .flatMap(SequencedSet::stream)
                        .map(InferredMultiProjectAssembler::local)
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                SequencedSet<String> described = inherited.sequencedKeySet().stream()
                        .filter(key -> locals.contains(phased(key)))
                        .collect(Collectors.toCollection(LinkedHashSet::new));
                if (packaging.launcher()) {
                    LauncherModule launcher = LauncherModule.ofEnvironment(environment, repositories, resolvers)
                            .pinning(descriptor.pinning())
                            .pathPlacement(descriptor.pathPlacement());
                    SequencedSet<String> launched = new LinkedHashSet<>(inputs);
                    if (sbom != null) {
                        launcher = launcher.sbom(sbom.type("application")).sbomInputs(described);
                        launched.addAll(described);
                    }
                    sub.addModule("launcher", launcher, launched);
                }
                if (packaging.docker() != null) {
                    SequencedSet<String> manifests = descriptor.manifests().stream()
                            .map(InferredMultiProjectAssembler::local)
                            .collect(Collectors.toCollection(LinkedHashSet::new));
                    sub.addStep("docker", Docker.ofEnvironment(environment, packaging.docker())
                            .labels(packaging.dockerLabels())
                            .jpackage(packaging.dockerJPackage()), Stream.of(
                                    packaging.dockerJPackage() == null
                                            ? Stream.<String>empty()
                                            : Stream.of(packaging.step(packaging.dockerJPackage())),
                                    inputs.stream(),
                                    inherited.sequencedKeySet().stream().filter(key -> manifests.contains(phased(key))))
                            .flatMap(Function.identity()));
                    images.add("docker");
                }
                if (packaging.nativeImage()) {
                    sub.addStep("reachability", new NativeImageMetadata(), inputs);
                    sub.addStep("native-image",
                                NativeImage.ofEnvironment(environment, descriptor.pathPlacement()),
                                Stream.concat(inputs.stream(), Stream.of("reachability")));
                    if (sbom == null) {
                        images.add("native-image");
                    } else {
                        sub.addStep("native-sbom",
                                sbom.type("application").graalvmLicense(environment.value("graalvm.license")),
                                Stream.concat(described.stream(), Stream.of("native-image")));
                        sub.addStep("native", new Described(), "native-image", "native-sbom");
                        images.add("native");
                    }
                }
                if (!packagers.isEmpty()) {
                    SequencedSet<String> handed = new LinkedHashSet<>(linked);
                    handed.addAll(images);
                    if (packaging.bundle()) {
                        handed.add("bundle");
                    }
                    if (packaging.launcher()) {
                        handed.add("launcher");
                    }
                    sub.addModule("custom", (nested, nestedInherited) -> packagers.forEach((name, module) ->
                            nested.addModule(name, module, nestedInherited.sequencedKeySet())), handed);
                    sub.addStep("packaged", new Packaged(), images.contains("jpackage")
                            ? Stream.of("jpackage", "custom")
                            : Stream.of("custom"));
                    images.remove("jpackage");
                    images.addFirst("packaged");
                    images.add("custom");
                }
                if (packaging.launcher()) {
                    images.add("launcher");
                }
                if (!images.isEmpty()) {
                    String named = descriptor.name().endsWith("-")
                            ? descriptor.name().substring(0, descriptor.name().length() - 1)
                            : descriptor.name();
                    sub.addStep("inventory", new Inventory().module(named), images.stream());
                }
            }, packaging.nativeImage()
                    ? AssemblyRequirement.of().after(AssemblyDescriptor.BUILD).tested(AssemblyDescriptor.BUILD)
                    : AssemblyRequirement.of().after(AssemblyDescriptor.BUILD));
        }
        return assembly;
    }

    private record Packaged() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path packages = context.next().resolve(JPackage.PACKAGES);
            for (Map.Entry<String, BuildStepArgument> argument : arguments.entrySet()) {
                if (argument.getValue().removed()) {
                    continue;
                }
                Path folder = argument.getValue().folder().resolve(JPackage.PACKAGES);
                if (!Files.isDirectory(folder)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(folder)) {
                    for (Path file : files.filter(Files::isRegularFile).toList()) {
                        Path target = packages.resolve(folder.relativize(file).toString());
                        if (Files.exists(target)) {
                            throw new IllegalStateException(argument.getKey() + " packages " + folder.relativize(file)
                                    + ", which another packager writes already - give each package a name of its own");
                        }
                        Files.createDirectories(target.getParent());
                        BuildStep.linkOrCopy(target, file);
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Described() implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path image = context.next().resolve(NativeImage.NATIVE);
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path folder = argument.folder().resolve(NativeImage.NATIVE);
                if (Files.isDirectory(folder)) {
                    try (Stream<Path> files = Files.walk(folder)) {
                        for (Path file : files.filter(Files::isRegularFile).toList()) {
                            Path target = image.resolve(folder.relativize(file).toString());
                            Files.createDirectories(target.getParent());
                            BuildStep.linkOrCopy(target, file);
                        }
                    }
                }
            }
            if (!Files.isDirectory(image)) {
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path fragment = argument.folder().resolve(Versions.MANIFEST);
                if (Files.isRegularFile(fragment)) {
                    String location;
                    try (InputStream in = Files.newInputStream(fragment)) {
                        location = new Manifest(in).getMainAttributes().getValue("Sbom-Location");
                    }
                    Path document = location == null ? null : argument.folder().resolve(RESOURCES).resolve(location);
                    if (document != null && Files.isRegularFile(document)) {
                        Path target = image.resolve(document.getFileName().toString());
                        if (Files.exists(target)) {
                            throw new IllegalStateException("The native image already holds " + target.getFileName()
                                    + ", where its bill of materials belongs - give the image another name");
                        }
                        BuildStep.linkOrCopy(target, document);
                    }
                }
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Packaging(boolean stage,
                            boolean jmod,
                            boolean jlink,
                            boolean bundle,
                            boolean launcher,
                            boolean nativeImage,
                            SequencedSet<String> jpackage,
                            String docker,
                            String dockerJPackage,
                            SequencedMap<String, String> dockerLabels) {

        private static final String DOCKER_LABEL = "docker.label.";

        private boolean jpackaged() {
            return !formats().isEmpty();
        }

        private SequencedSet<String> formats() {
            SequencedSet<String> formats = new LinkedHashSet<>(jpackage);
            if (dockerJPackage != null) {
                formats.add(dockerJPackage);
            }
            return formats;
        }

        private String step(String format) {
            return jpackage.size() == 1 && jpackage.contains(format) ? "jpackage" : "jpackage-" + format;
        }

        private static Packaging configured(Path properties) throws IOException {
            if (properties == null) {
                return new Packaging(true,
                        false,
                        false,
                        false,
                        false,
                        false,
                        Collections.emptyNavigableSet(),
                        null,
                        null,
                        Collections.emptyNavigableMap());
            }
            SequencedProperties configuration = SequencedProperties.ofFiles(properties);
            SequencedMap<String, String> labels = new LinkedHashMap<>();
            configuration.forEachProperty((key, value) -> {
                if (key.startsWith(DOCKER_LABEL)) {
                    labels.put(key.substring(DOCKER_LABEL.length()), value.strip());
                }
            });
            if (labels.containsKey("")) {
                throw new IllegalArgumentException(properties + " sets " + DOCKER_LABEL + " without naming the label,"
                        + " as " + DOCKER_LABEL + "<name>=<value>");
            }
            String docker = configuration.value("docker");
            if (docker == null && !labels.isEmpty()) {
                throw new IllegalArgumentException(properties + " sets " + DOCKER_LABEL + "* without docker=<image>,"
                        + " which names the image the labels belong to");
            }
            List<String> formats = configuration.entries("jpackage");
            String dockerJPackage = configuration.value("docker.jpackage");
            if (dockerJPackage != null && docker == null) {
                throw new IllegalArgumentException(properties + " sets docker.jpackage without docker=<image>,"
                        + " which names the base image the jpackage package is installed onto");
            } else if (dockerJPackage != null && !Docker.FORMATS.contains(dockerJPackage)) {
                throw new IllegalArgumentException(properties + " sets docker.jpackage=" + dockerJPackage
                        + ", which a Linux image cannot run - name one of " + Docker.FORMATS);
            }
            return new Packaging(configuration.flag("stage", true),
                    configuration.flag("jmod"),
                    configuration.flag("jlink"),
                    configuration.flag("bundle"),
                    configuration.flag("launcher"),
                    configuration.flag("native"),
                    formats == null ? Collections.emptyNavigableSet() : new LinkedHashSet<>(formats),
                    docker,
                    dockerJPackage,
                    labels);
        }
    }

    private static ModularizeModule modularized(ModularizeModule module, Path file) throws IOException {
        if (file == null) {
            return null;
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        for (String key : properties.stringPropertyNames()) {
            if (!key.equals("mode")) {
                throw new IllegalArgumentException(file + " names " + key + ", but takes mode=declared, synthetic"
                        + " or none alone");
            }
        }
        String mode = properties.value("mode", "declared");
        return switch (mode.toLowerCase(Locale.ROOT)) {
            case "declared" -> module.mode(ModularizeModule.Mode.DECLARED);
            case "synthetic" -> module.mode(ModularizeModule.Mode.SYNTHETIC);
            case "none" -> null;
            default -> throw new IllegalArgumentException(file + " names the mode " + mode + ", but takes declared to"
                    + " fail on a jar that declares no module name, synthetic to derive one, or none to leave the"
                    + " closure as it is");
        };
    }

    private static SequencedMap<String, String> renamed(SequencedMap<String, Path> inherited, Stream<String> names) {
        SequencedMap<String, String> renamed = new LinkedHashMap<>();
        names.forEach(name -> {
            String local = local(name);
            for (String key : inherited.sequencedKeySet()) {
                String phased = phased(key);
                if (phased.equals(local) || phased.startsWith(local + "/")) {
                    renamed.putIfAbsent(key, name + phased.substring(local.length()));
                }
            }
        });
        return renamed;
    }

    private static String phased(String identity) {
        String local = local(identity);
        return local.startsWith(AssemblyDescriptor.BUILD + "/") ? local.substring(AssemblyDescriptor.BUILD.length() + 1) : local;
    }

    private static String local(String identity) {
        while (identity.startsWith(BuildExecutorModule.PREVIOUS)) {
            identity = identity.substring(BuildExecutorModule.PREVIOUS.length());
        }
        return identity;
    }

    private static Stream<String> inputs(ProjectModuleDescriptor descriptor, SequencedSet<String> closure) {
        return Stream.of(descriptor.sources(),
                descriptor.manifests(),
                closure,
                descriptor.spdx()).flatMap(SequencedSet::stream);
    }

    private SequencedMap<String, SequencedMap<String, String>> perTool(SequencedSet<Path> configuration, String prefix)
            throws IOException {
        SequencedMap<String, Path> files = new LinkedHashMap<>();
        for (Path folder : configuration) {
            if (!Files.isDirectory(folder)) {
                continue;
            }
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder, prefix + "*.properties")) {
                for (Path file : stream) {
                    String fileName = file.getFileName().toString();
                    String tool = fileName.substring(prefix.length(), fileName.length() - ".properties".length());
                    files.putIfAbsent(tool, file);
                }
            }
        }
        SequencedMap<String, SequencedMap<String, String>> perTool = new LinkedHashMap<>();
        for (String tool : new TreeSet<>(files.keySet())) {
            SequencedMap<String, String> values = new LinkedHashMap<>();
            SequencedProperties.ofFiles(files.get(tool)).forEachProperty((key, value) -> {
                String resolved = environment.variable(files.get(tool) + " sets " + key, value);
                if (prefix.equals("process-") && key.startsWith("-D") && key.indexOf('=') < 0 && !resolved.isEmpty()) {
                    values.put(key + "=" + resolved, "");
                } else {
                    values.put(key, resolved);
                }
            });
            perTool.put(tool, values);
        }
        return perTool;
    }

    private record Prepare(PathPlacement pathPlacement,
                           boolean staged,
                           List<String> packageTypes,
                           List<String> manifests,
                           SequencedMap<String, SequencedMap<String, String>> overrides,
                           SequencedMap<String, SequencedMap<String, String>> environments) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            String main = null;
            String version = null;
            String artifact = null;
            String moduleName = null;
            SequencedProperties described = null;
            for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
                BuildStepArgument argument = entry.getValue();
                if (argument.removed()) {
                    continue;
                }
                Path moduleFile = argument.folder().resolve(BuildStep.MODULE);
                if (Files.isRegularFile(moduleFile)) {
                    SequencedProperties module = SequencedProperties.ofFiles(moduleFile);
                    if (main == null) {
                        String value = module.getProperty("main");
                        if (value != null && !value.isEmpty()) {
                            main = value;
                        }
                    }
                    if (moduleName == null) {
                        String value = module.getProperty("module");
                        if (value != null && !value.isEmpty()) {
                            moduleName = value;
                        }
                    }
                }
                if (manifests.contains(entry.getKey())) {
                    for (Map.Entry<String, SequencedMap<String, String>> override : overrides.entrySet()) {
                        Path generated = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, override.getKey());
                        if (generated == null) {
                            continue;
                        }
                        SequencedProperties declared = SequencedProperties.ofFiles(generated);
                        for (String key : override.getValue().keySet()) {
                            if (declared.containsKey(key)) {
                                String value = declared.getProperty(key);
                                throw new IllegalArgumentException("process-" + override.getKey() + ".properties sets "
                                        + key + ", which the build already hands " + override.getKey() + " as "
                                        + (value.isEmpty() ? key : key + " " + value) + " from the module's declaration - "
                                        + (key.equals("--release") || key.equals("--enable-preview")
                                                ? "declare the release there instead: @jenesis.release in"
                                                        + " module-info.java, or maven.compiler.release and, for the"
                                                        + " tests, maven.compiler.testRelease in pom.xml, written"
                                                        + " <release>-preview or with maven.compiler.enablePreview for"
                                                        + " preview features"
                                                : "change it there instead and remove the line"));
                            }
                        }
                    }
                }
                Path metadataFile = argument.folder().resolve(BuildStep.METADATA);
                if (Files.isRegularFile(metadataFile)) {
                    SequencedProperties metadata = SequencedProperties.ofFiles(metadataFile);
                    if (described == null) {
                        described = metadata;
                    }
                    if (version == null) {
                        String value = metadata.getProperty("version");
                        if (value != null && !value.isEmpty()) {
                            version = value;
                        }
                    }
                    if (artifact == null) {
                        String value = metadata.getProperty("artifact");
                        if (value != null && !value.isEmpty()) {
                            artifact = value;
                        }
                    }
                }
            }
            if (main != null) {
                SequencedProperties jar = new SequencedProperties();
                jar.setProperty("--main-class", main);
                ProcessBuildStep.store(context.next(), ProcessBuildStep.PROCESS, "jar", jar);
                SequencedProperties jpackage = new SequencedProperties();
                SequencedMap<String, SequencedProperties> typed = new TreeMap<>();
                if (artifact != null) {
                    jpackage.setProperty("--name", artifact);
                }
                if (pathPlacement.modular() && moduleName != null) {
                    jpackage.setProperty("--module", moduleName + "/" + main);
                } else {
                    jpackage.setProperty("--main-jar", Jar.Sort.CLASSES.file(arguments));
                    jpackage.setProperty("--main-class", main);
                }
                if (version != null) {
                    jpackage.setProperty("--app-version", version);
                }
                if (described != null) {
                    String description = described.value("description"),
                            url = described.value("url"),
                            vendor = described.value("organization.name"),
                            copyright = described.value("copyright");
                    if (description != null) {
                        jpackage.setProperty("--description", description.replaceAll("\\s+", " "));
                    }
                    if (vendor != null) {
                        jpackage.setProperty("--vendor", vendor);
                    }
                    if (copyright != null) {
                        jpackage.setProperty("--copyright", copyright);
                    }
                    if (url != null) {
                        packageTypes.stream()
                                .filter(type -> !type.equals("app-image"))
                                .forEach(type -> typed.computeIfAbsent(type, _ -> new SequencedProperties()).setProperty("--about-url", url));
                    }
                    List<String> emails = new ArrayList<>();
                    SequencedSet<String> licenses = new LinkedHashSet<>();
                    described.forEachProperty((key, value) -> {
                        if (key.startsWith("developer.") && key.endsWith(".email") && !value.isBlank()) {
                            emails.add(value.trim());
                        } else if (key.startsWith("license.") && (key.endsWith(".name") || key.endsWith(".url"))) {
                            licenses.add(key.substring(0, key.lastIndexOf('.')));
                        }
                    });
                    if (packageTypes.contains("deb") && !emails.isEmpty()) {
                        typed.computeIfAbsent("deb", _ -> new SequencedProperties())
                                .setProperty("--linux-deb-maintainer", emails.getFirst());
                    }
                    if (packageTypes.contains("rpm") && !licenses.isEmpty()) {
                        Map<String, String> aliases = Dependencies.aliases(arguments.values().stream()
                                .filter(argument -> !argument.removed())
                                .map(BuildStepArgument::folder)
                                .toList());
                        List<String> identifiers = new ArrayList<>();
                        for (String license : licenses) {
                            identifiers.add(new License(null,
                                    null,
                                    described.value(license + ".name"),
                                    described.value(license + ".url")).identified(aliases).id());
                        }
                        if (!identifiers.contains(null)) {
                            typed.computeIfAbsent("rpm", _ -> new SequencedProperties())
                                    .setProperty("--linux-rpm-license-type", String.join(" OR ", identifiers));
                        }
                    }
                }
                ProcessBuildStep.store(context.next(), ProcessBuildStep.PROCESS, "jpackage", jpackage);
                for (Map.Entry<String, SequencedProperties> type : typed.entrySet()) {
                    ProcessBuildStep.store(context.next(), ProcessBuildStep.PROCESS, "jpackage-" + type.getKey(), type.getValue());
                }
            }
            if (main != null || artifact != null) {
                SequencedProperties launcher = new SequencedProperties();
                if (main != null) {
                    launcher.setProperty("mainClass", main);
                    if (pathPlacement.modular() && moduleName != null) {
                        launcher.setProperty("mainModule", moduleName);
                    }
                }
                if (artifact != null) {
                    launcher.setProperty("name", artifact);
                }
                launcher.store(context.next().resolve("launcher.properties"));
            }
            if (moduleName != null) {
                SequencedProperties jlink = new SequencedProperties();
                jlink.setProperty("--add-modules", moduleName);
                ProcessBuildStep.store(context.next(), ProcessBuildStep.PROCESS, "jlink", jlink);
            }
            if (version != null) {
                SequencedProperties javac = new SequencedProperties();
                javac.setProperty("--module-version", version);
                ProcessBuildStep.store(context.next(), ProcessBuildStep.PROCESS, "javac", javac);
            }
            for (Map.Entry<String, SequencedMap<String, String>> override : overrides.entrySet()) {
                Path target = ProcessBuildStep.locate(context.next(), ProcessBuildStep.PROCESS, override.getKey());
                SequencedProperties merged = target == null ? new SequencedProperties() : SequencedProperties.ofFiles(target);
                override.getValue().forEach(merged::setProperty);
                ProcessBuildStep.store(context.next(), ProcessBuildStep.PROCESS, override.getKey(), merged);
            }
            for (Map.Entry<String, SequencedMap<String, String>> tool : environments.entrySet()) {
                SequencedProperties variables = new SequencedProperties();
                tool.getValue().forEach(variables::setProperty);
                ProcessBuildStep.store(context.next(), ProcessBuildStep.ENVIRONMENT, tool.getKey(), variables);
            }
            if (!staged) {
                Files.createFile(context.next().resolve(Inventory.UNSTAGED));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

    }
}

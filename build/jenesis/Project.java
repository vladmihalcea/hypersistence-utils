package build.jenesis;

import module java.base;
import build.jenesis.docker.DockerizedJava;
import build.jenesis.maven.MavenDefaultRepository;
import build.jenesis.maven.MavenModuleResolver;
import build.jenesis.maven.MavenPomResolver;
import build.jenesis.maven.MavenProject;
import build.jenesis.maven.MavenRepositoryExport;
import build.jenesis.maven.MavenRepositoryStaging;
import build.jenesis.maven.MavenResolver;
import build.jenesis.maven.PinPom;
import build.jenesis.maven.Pom;
import build.jenesis.module.JenesisModuleRepository;
import build.jenesis.module.JenesisModuleRepositoryExport;
import build.jenesis.module.JenesisRepository;
import build.jenesis.module.ModularJarResolver;
import build.jenesis.module.ModularProject;
import build.jenesis.module.ModularStaging;
import build.jenesis.module.PinModuleInfo;
import build.jenesis.project.AssemblyDescriptor;
import build.jenesis.project.AssemblyRequirement;
import build.jenesis.project.ExternalModule;
import build.jenesis.project.Ide;
import build.jenesis.project.InferredMultiProjectAssembler;
import build.jenesis.project.InternalModule;
import build.jenesis.project.MultiProjectAssembler;
import build.jenesis.project.MultiProjectModule;
import build.jenesis.project.ProjectModuleDescriptor;
import build.jenesis.project.ProjectPlugins;
import build.jenesis.project.ProjectWatch;
import build.jenesis.project.ReleaseModule;
import build.jenesis.step.Bind;
import build.jenesis.step.Bom;
import build.jenesis.step.Dependencies;
import build.jenesis.step.ImageStaging;
import build.jenesis.step.Inventory;
import build.jenesis.step.ProcessHandler;
import build.jenesis.step.ProjectFiles;
import build.jenesis.step.ReportStaging;
import build.jenesis.step.Tree;
import javax.lang.model.SourceVersion;

public record Project(
        Path root,
        Path target,
        Path reproduce,
        Path artifacts,
        SequencedSet<Path> metadata,
        SequencedSet<Path> configuration,
        SequencedSet<Path> boms,
        SequencedSet<Path> signatures,
        SequencedSet<Path> profiles,
        BuildExecutorCache cache,
        HashDigestFunction hashFunction,
        Layout layout,
        boolean tests,
        boolean sources,
        boolean documentation,
        Pinning pinning,
        String version,
        String tag,
        String revision,
        String tree,
        SequencedSet<String> defaultTarget,
        MultiProjectAssembler<? super ProjectModuleDescriptor> assembler,
        Supplier<BuildExecutor.Configuration> configurator,
        Map<String, Repository> repositories,
        Map<String, Resolver> resolvers,
        ProjectPlugins plugins,
        Environment environment) {

    public static final String BUILD = "build",
            STAGE = "stage",
            EXPORT = "export",
            RELEASE = "release",
            PLUGIN = "plugin",
            PIN = "pin",
            DEPENDENCIES = "dependencies",
            IDE = "ide",
            METADATA = "metadata",
            HELP = "help",
            SKILL = "skill",
            PROMPT = "prompt",
            PROPERTIES = "properties",
            CONFIGURATION = "configuration";

    @FunctionalInterface
    public interface Layout {

        Function<String, String> apply(BuildExecutor executor,
                                       Project project,
                                       MultiProjectAssembler<? super ProjectModuleDescriptor> assembler) throws IOException;

        private static Path modularConfigurationFolder(Path location) {
            return location == null ? null : location.resolve("META-INF").resolve("build.jenesis");
        }

        static SequencedSet<Path> configurations(Path local, SequencedSet<Path> folders, SequencedSet<Path> profiles) {
            return configurations(Collections.singletonList(local), folders, profiles);
        }

        static SequencedSet<Path> configurations(List<Path> locals, SequencedSet<Path> folders, SequencedSet<Path> profiles) {
            LinkedHashSet<Path> base = new LinkedHashSet<>();
            Stream.concat(locals.stream(), folders.stream())
                    .filter(Objects::nonNull)
                    .map(folder -> folder.toAbsolutePath().normalize())
                    .forEach(base::add);
            LinkedHashSet<Path> ordered = new LinkedHashSet<>();
            for (Path folder : base) {
                for (Path profile : profiles) {
                    Path resolved = folder.resolve(profile);
                    if (Files.isDirectory(resolved)) {
                        ordered.add(resolved);
                    }
                }
            }
            for (Path folder : base) {
                if (Files.isDirectory(folder)) {
                    ordered.add(folder);
                }
            }
            return Collections.unmodifiableSequencedSet(ordered);
        }

        Layout MAVEN = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("maven",
                    assembler.getClass().getName(),
                    project.environment().out(),
                    Palette.ofEnvironment(project.environment())));
            executor.addModule(SKILL, new SkillModule(project.target(), project.environment().out()));
            executor.addModule(PROMPT, new PromptModule(project.environment().out()));
            executor.addModule(METADATA, project.metadataModule());
            MultiProjectAssembler<? super ProjectModuleDescriptor> pomAware = new PomAwareAssembler(new AwaitingAssembler(assembler, project.environment().entries("project.await")),
                    null,
                    null,
                    project.environment().flag("maven.embed", true));
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                Discovery discovery = project.environment().flag("repository.discovery")
                        ? Discovery.ofEnvironment(project.environment())
                        : null;
                repositories.putIfAbsent("maven",
                        MavenDefaultRepository.ofEnvironment(project.environment(), discovery)
                                .cached(project.artifacts() == null ? null : skipped(project.artifacts())));
                repositories.putIfAbsent("OpenPGP", OpenPgpRepository.ofEnvironment(project.environment()));
                if (discovery != null) {
                    repositories.putIfAbsent(DiscoverySources.NAME,
                            DiscoverySources.ofEnvironment(project.environment(), discovery));
                }
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("maven", MavenPomResolver.ofEnvironment(project.environment()));
                SequencedSet<String> mavenDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(mavenDeps::add);
                sub.addModule(ProjectPlugins.PREPROCESS, project.toolPlugins().preprocess(project.profiles()));
                mavenDeps.add(ProjectPlugins.PREPROCESS);
                Function<List<Path>, SequencedSet<Path>> locations =
                        locals -> configurations(locals, project.configuration(), project.profiles());
                sub.addModule("maven",
                              project.toolPlugins().preprocessed(MavenProject.make(project.environment(),
                                                         project.root(),
                                                         "main",
                                                         "maven",
                                                         Collections.unmodifiableMap(repositories),
                                                         Collections.unmodifiableMap(resolvers),
                                                         project.pinning(),
                                                         project.licenseFiles(Dependencies.SPDX),
                                                         locations,
                                                         (descriptor, mergedRepos, mergedResolvers) -> pomAware.apply(
                                        new ProjectModuleDescriptor(descriptor)
                                                .location(descriptor.location())
                                                .root(project.root())
                                                .configuration(locations.apply(descriptor.configurations()))
                                                .test(project.tests())
                                                .source(project.sources())
                                                .documentation(project.documentation())
                                                .pinning(project.pinning())
                                                .pathPlacement(PathPlacement.CLASS_PATH)
                                                .includeResources(false),
                                        mergedRepos,
                                        mergedResolvers))),
                              mavenDeps);
                sub.addModule(ProjectPlugins.POSTPROCESS, project.toolPlugins().postprocess(project.profiles()), "maven");
            }, METADATA);
            SequencedMap<String, BuildStep> trees = new LinkedHashMap<>();
            trees.put("maven", MavenRepositoryStaging.ofEnvironment(project.environment()));
            trees.put("packages", new ImageStaging("package").noFolder(true));
            trees.put("native", new ImageStaging("native").noFolder(true));
            trees.put("docker", new ImageStaging("docker"));
            trees.put("reports", new ReportStaging());
            trees.put(ProjectPlugins.PROJECT, new ProjectFiles());
            executor.addModule(STAGE, project.toolPlugins().stage(project.profiles(), trees), BUILD);
            executor.addModule(EXPORT, (export, _) -> export.addStep("maven", MavenRepositoryExport.ofEnvironment(project.environment()), BuildExecutorModule.PREVIOUS + STAGE + "/maven"), STAGE);
            String prefix = BUILD + "/maven/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(DEPENDENCIES, (tree, inherited) -> tree.addStep(
                    "tree", Tree.ofEnvironment(project.environment()), inherited.sequencedKeySet()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, (release, inherited) -> {
                ReleaseModule.ofEnvironment(project.environment()).accept(release, inherited);
                project.toolPlugins()
                        .release(configurations((Path) null, project.configuration(), project.profiles()), project.root())
                        .accept(release, inherited);
            }, STAGE, METADATA);
            executor.addModule(PIN, PinModule.ofEnvironment(project.environment(),
                    project.root(),
                    "pom.xml",
                    (path, file) -> PinPom.ofEnvironment(project.environment(), "maven", path, List.of(file), project.hashFunction()),
                    project.hashFunction()).plugins(project.toolPlugins()), BUILD, RELEASE);
            executor.addModule(PLUGIN, project.toolPlugins().goal(project.profiles()));
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/" + AssemblyDescriptor.BUILD + "-module-" + BuildExecutorModule.encodePath(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout MODULAR = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("modular",
                    assembler.getClass().getName(),
                    project.environment().out(),
                    Palette.ofEnvironment(project.environment())));
            executor.addModule(SKILL, new SkillModule(project.target(), project.environment().out()));
            executor.addModule(PROMPT, new PromptModule(project.environment().out()));
            executor.addModule(METADATA, project.metadataModule());
            MultiProjectAssembler<? super ProjectModuleDescriptor> bomAware = new BomAwareAssembler(new AwaitingAssembler(assembler, project.environment().entries("project.await")), project.hashFunction());
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                Discovery discovery = project.environment().flag("repository.discovery")
                        ? Discovery.ofEnvironment(project.environment())
                        : null;
                repositories.putIfAbsent("module",
                        JenesisRepository.ofEnvironment(project.environment(),
                                        JenesisRepository.Scope.MODULE,
                                        discovery)
                                .cached(project.artifacts() == null ? null : skipped(project.artifacts())));
                repositories.putIfAbsent("OpenPGP", OpenPgpRepository.ofEnvironment(project.environment()));
                if (discovery != null) {
                    repositories.putIfAbsent(DiscoverySources.NAME,
                            DiscoverySources.ofEnvironment(project.environment(), discovery));
                }
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("module", ModularJarResolver.ofEnvironment(project.environment(), false));
                SequencedSet<String> modulesDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(modulesDeps::add);
                sub.addModule(ProjectPlugins.PREPROCESS, project.toolPlugins().preprocess(project.profiles()));
                modulesDeps.add(ProjectPlugins.PREPROCESS);
                sub.addModule("modules",
                              project.toolPlugins().preprocessed(ModularProject.make(project.environment(),
                                                             project.root(),
                                                             "main",
                                                             "module",
                                                             _ -> true,
                                                             Collections.unmodifiableMap(repositories),
                                                             Collections.unmodifiableMap(resolvers),
                                                             project.pinning(),
                                                             true,
                                                             false,
                                                             project.licenseFiles(Dependencies.SPDX),
                                                             project.boms(),
                                                             project.signatures(),
                                                             (descriptor, mergedRepos, mergedResolvers) -> bomAware.apply(
                                        new ProjectModuleDescriptor(descriptor)
                                                .location(descriptor.location())
                                                .root(project.root())
                                                .configuration(configurations(
                                                                modularConfigurationFolder(descriptor.location()),
                                                                project.configuration(),
                                                                project.profiles()))
                                                .test(project.tests())
                                                .source(project.sources())
                                                .documentation(project.documentation())
                                                .pinning(project.pinning())
                                                .pathPlacement(PathPlacement.MODULE_PATH),
                                        mergedRepos,
                                        mergedResolvers))),
                              modulesDeps);
                sub.addModule(ProjectPlugins.POSTPROCESS, project.toolPlugins().postprocess(project.profiles()), "modules");
            }, METADATA);
            SequencedMap<String, BuildStep> trees = new LinkedHashMap<>();
            trees.put("modular", ModularStaging.ofEnvironment(project.environment()));
            trees.put("packages", new ImageStaging("package").noFolder(true));
            trees.put("runtime", new ImageStaging("image"));
            trees.put("layers", new ImageStaging("layers"));
            trees.put("native", new ImageStaging("native").noFolder(true));
            trees.put("docker", new ImageStaging("docker"));
            trees.put("reports", new ReportStaging());
            trees.put(ProjectPlugins.PROJECT, new ProjectFiles());
            executor.addModule(STAGE, project.toolPlugins().stage(project.profiles(), trees), BUILD);
            executor.addModule(EXPORT, (export, _) -> export.addStep("modular", JenesisModuleRepositoryExport.ofEnvironment(project.environment()), BuildExecutorModule.PREVIOUS + STAGE + "/modular"), STAGE);
            String prefix = BUILD + "/modules/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addModule(DEPENDENCIES, (tree, inherited) -> tree.addStep(
                    "tree", Tree.ofEnvironment(project.environment()), inherited.sequencedKeySet()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, (release, inherited) -> {
                ReleaseModule.ofEnvironment(project.environment()).accept(release, inherited);
                project.toolPlugins()
                        .release(configurations((Path) null, project.configuration(), project.profiles()), project.root())
                        .accept(release, inherited);
            }, STAGE, METADATA);
            executor.addModule(PIN, PinModule.ofEnvironment(project.environment(),
                    project.root(),
                    "module-info.java",
                    (path, file) -> PinModuleInfo.ofEnvironment(project.environment(), "module", path, List.of(file), project.hashFunction()),
                    project.hashFunction()).plugins(project.toolPlugins()), BUILD, RELEASE);
            executor.addModule(PLUGIN, project.toolPlugins().goal(project.profiles()));
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/" + AssemblyDescriptor.BUILD + "-module-" + BuildExecutorModule.encodePath(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout MODULAR_TO_MAVEN = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("modular_to_maven",
                    assembler.getClass().getName(),
                    project.environment().out(),
                    Palette.ofEnvironment(project.environment())));
            executor.addModule(SKILL, new SkillModule(project.target(), project.environment().out()));
            executor.addModule(PROMPT, new PromptModule(project.environment().out()));
            executor.addModule(METADATA, project.metadataModule());
            MultiProjectAssembler<? super ProjectModuleDescriptor> pomAware = new PomAwareAssembler(new AwaitingAssembler(assembler, project.environment().entries("project.await")),
                    BuildExecutorModule.PREVIOUS.repeat(2) + MultiProjectModule.MANIFESTS,
                    "module",
                    project.environment().flag("maven.embed", true));
            MultiProjectAssembler<? super ProjectModuleDescriptor> bomAware = new BomAwareAssembler(pomAware, project.hashFunction());
            executor.addModule(BUILD, (sub, inherited) -> {
                Map<String, Repository> repositories = new LinkedHashMap<>(project.repositories());
                Discovery discovery = project.environment().flag("repository.discovery")
                        ? Discovery.ofEnvironment(project.environment())
                        : null;
                repositories.putIfAbsent("maven",
                        MavenDefaultRepository.ofEnvironment(project.environment(), discovery)
                                .cached(project.artifacts() == null ? null : skipped(project.artifacts())));
                repositories.putIfAbsent("module",
                        JenesisRepository.ofEnvironment(project.environment(),
                                        JenesisRepository.Scope.ARTIFACT,
                                        discovery)
                                .cached(project.artifacts() == null ? null : skipped(project.artifacts())));
                repositories.putIfAbsent("OpenPGP", OpenPgpRepository.ofEnvironment(project.environment()));
                if (discovery != null) {
                    repositories.putIfAbsent(DiscoverySources.NAME,
                            DiscoverySources.ofEnvironment(project.environment(), discovery));
                }
                Map<String, Resolver> resolvers = new LinkedHashMap<>(project.resolvers());
                resolvers.putIfAbsent("maven", MavenPomResolver.ofEnvironment(project.environment()));
                resolvers.putIfAbsent("module", new MavenModuleResolver("maven",
                        MavenResolver.of(resolvers.get("maven")),
                        repositories.get("module")));
                SequencedSet<String> modulesDeps = new LinkedHashSet<>();
                inherited.sequencedKeySet().stream()
                        .filter(key -> key.startsWith(BuildExecutorModule.PREVIOUS + METADATA + "/"))
                        .forEach(modulesDeps::add);
                sub.addModule(ProjectPlugins.PREPROCESS, project.toolPlugins().preprocess(project.profiles()));
                modulesDeps.add(ProjectPlugins.PREPROCESS);
                sub.addModule("modules",
                              project.toolPlugins().preprocessed(ModularProject.make(project.environment(),
                                                             project.root(),
                                                             "main",
                                                             "module",
                                                             _ -> true,
                                                             Collections.unmodifiableMap(repositories),
                                                             Collections.unmodifiableMap(resolvers),
                                                             project.pinning(),
                                                             true,
                                                             true,
                                                             project.licenseFiles(Dependencies.SPDX),
                                                             project.boms(),
                                                             project.signatures(),
                                                             (descriptor, mergedRepos, mergedResolvers) -> bomAware.apply(
                                        new ProjectModuleDescriptor(descriptor)
                                                .location(descriptor.location())
                                                .root(project.root())
                                                .configuration(configurations(modularConfigurationFolder(descriptor.location()), project.configuration(), project.profiles()))
                                                .test(project.tests())
                                                .source(project.sources())
                                                .documentation(project.documentation())
                                                .pinning(project.pinning()),
                                        mergedRepos,
                                        mergedResolvers))),
                              modulesDeps);
                sub.addModule(ProjectPlugins.POSTPROCESS, project.toolPlugins().postprocess(project.profiles()), "modules");
            }, METADATA);
            SequencedMap<String, BuildStep> trees = new LinkedHashMap<>();
            trees.put("maven", MavenRepositoryStaging.ofEnvironment(project.environment()));
            trees.put("modular", ModularStaging.ofEnvironment(project.environment()));
            trees.put("packages", new ImageStaging("package").noFolder(true));
            trees.put("runtime", new ImageStaging("image"));
            trees.put("layers", new ImageStaging("layers"));
            trees.put("native", new ImageStaging("native").noFolder(true));
            trees.put("docker", new ImageStaging("docker"));
            trees.put("reports", new ReportStaging());
            trees.put(ProjectPlugins.PROJECT, new ProjectFiles());
            executor.addModule(STAGE, project.toolPlugins().stage(project.profiles(), trees), BUILD);
            executor.addModule(EXPORT, (export, _) -> {
                export.addStep("maven", MavenRepositoryExport.ofEnvironment(project.environment()), BuildExecutorModule.PREVIOUS + STAGE + "/maven");
                export.addStep("modular", JenesisModuleRepositoryExport.ofEnvironment(project.environment()), BuildExecutorModule.PREVIOUS + STAGE + "/modular");
            }, STAGE);
            String prefix = BUILD + "/modules/" + MultiProjectModule.COMPOSE + "/" + MultiProjectModule.MODULE;
            executor.addStep(DEPENDENCIES, Tree.ofEnvironment(project.environment()), BUILD);
            executor.addModule(IDE, new Ide(project.root()), BUILD);
            executor.addModule(RELEASE, (release, inherited) -> {
                ReleaseModule.ofEnvironment(project.environment()).accept(release, inherited);
                project.toolPlugins()
                        .release(configurations((Path) null, project.configuration(), project.profiles()), project.root())
                        .accept(release, inherited);
            }, STAGE, METADATA);
            executor.addModule(PIN,
                    PinModule.ofEnvironment(project.environment(),
                            project.root(),
                            "module-info.java",
                            (path, file) -> PinModuleInfo.ofEnvironment(project.environment(), "module", path, List.of(file), project.hashFunction()),
                            project.hashFunction()).plugins(project.toolPlugins()),
                    BUILD,
                    RELEASE);
            executor.addModule(PLUGIN, project.toolPlugins().goal(project.profiles()));
            return name -> {
                int slash = name.indexOf('/');
                String module = (slash == -1 ? name : name.substring(0, slash)).replace('+', '/');
                return prefix + "/" + AssemblyDescriptor.BUILD + "-module-" + BuildExecutorModule.encodePath(module)
                        + (slash == -1 ? "" : "/" + name.substring(slash + 1));
            };
        };

        Layout UNDESCRIBED = (executor, project, assembler) -> {
            executor.addModule(HELP, new HelpModule("none, no pom.xml or module-info.java yet",
                    assembler.getClass().getName(),
                    project.environment().out(),
                    Palette.ofEnvironment(project.environment())));
            executor.addModule(SKILL, new SkillModule(project.target(), project.environment().out()));
            executor.addModule(PROMPT, new PromptModule(project.environment().out()));
            for (String name : List.of(BUILD, STAGE, EXPORT, RELEASE, PLUGIN, PIN, DEPENDENCIES, IDE, METADATA)) {
                executor.addModule(name, (_, _) -> {
                    throw new IllegalStateException("No build descriptor found under "
                            + project.root().toAbsolutePath().normalize()
                            + " (expected a module-info.java or a pom.xml); to move a Maven or Gradle build here,"
                            + " run java build/jenesis/Make.java skill/migrate");
                });
            }
            return name -> {
                throw new IllegalStateException("No build descriptor found under "
                        + project.root().toAbsolutePath().normalize() + ", so +" + name + " names no module");
            };
        };

        Layout AUTO = (executor, project, assembler) -> of(project.root()).apply(executor, project, assembler);

        static Layout of(Path root) throws IOException {
            if (Files.isRegularFile(root.resolve("pom.xml"))) {
                return MAVEN;
            }
            List<Path> moduleInfos = new ArrayList<>();
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    if (!directory.equals(root)
                            && Files.exists(directory.resolve(BuildExecutor.SKIP_MARKER))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    Path name = file.getFileName();
                    if (name != null && "module-info.java".equals(name.toString())) {
                        moduleInfos.add(file);
                        return FileVisitResult.TERMINATE;
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return moduleInfos.isEmpty() ? UNDESCRIBED : MODULAR_TO_MAVEN;
        }
    }

    private SequencedSet<Path> licenseFiles(String file) {
        SequencedSet<Path> located = new LinkedHashSet<>();
        for (Path folder : Layout.configurations((Path) null, configuration, profiles)) {
            Path candidate = folder.resolve(file);
            if (Files.isRegularFile(candidate)) {
                located.add(candidate);
            }
        }
        return Collections.unmodifiableSequencedSet(located);
    }

    private BuildExecutorModule metadataModule() {
        Path base = root.toAbsolutePath().normalize();
        SequencedMap<String, Path> files = new LinkedHashMap<>();
        for (Path file : metadata != null ? metadata
                : Files.isRegularFile(root.resolve("project.properties")) ? List.of(Path.of("project.properties"))
                : List.<Path>of()) {
            Path absolute = (file.isAbsolute() ? file : root.resolve(file)).toAbsolutePath().normalize();
            Path relative = base.relativize(absolute);
            files.put(METADATA + "-" + BuildExecutorModule.encode(relative.toString()), absolute);
        }
        return new MetadataModule(files, version, tag, revision, tree);
    }

    private record MetadataModule(SequencedMap<String, Path> files,
                                  String version,
                                  String tag,
                                  String revision,
                                  String tree) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            files.forEach((name, file) -> buildExecutor.addSource("file-" + name, Bind.asMetadata(), file));
            SequencedMap<String, String> values = new LinkedHashMap<>();
            if (version != null && !version.isEmpty()) {
                values.put("version", version);
            }
            if (tag != null) {
                values.put("scm.tag", tag);
            }
            if (revision != null) {
                values.put("scm.revision", revision);
            }
            if (tree != null) {
                values.put("scm.tree", tree);
            }
            if (!values.isEmpty()) {
                buildExecutor.addStep("command", new MetadataValues(values));
            }
        }
    }

    private record MetadataValues(SequencedMap<String, String> values) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedProperties properties = new SequencedProperties();
            values.forEach(properties::setProperty);
            properties.store(context.next().resolve(BuildStep.METADATA));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record HelpModule(String layout, String assembler, Consumer<String> out, Palette palette)
            implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            out.accept(("""
                    %{title}Jenesis%{reset} - a Java build tool, written and configured in Java.

                    A coding agent runs %{name}java build/jenesis/Make.java skill/start%{reset} first, the briefing written for it.
                    To have one move a Maven or Gradle build here, enter %{name}! java build/jenesis/Make.java prompt/migrate%{reset}
                    in its prompt, or paste what that command prints.

                    %{header}Active configuration:%{reset}
                      layout      %{name}%{layout}%{reset}
                      assembler   %{name}%{assembler}%{reset}

                    %{header}Getting started:%{reset}
                      Jenesis infers the build from the project's own layout, so there is no build
                      script to write. Pass selectors as command-line arguments; without one,
                      %{name}build%{reset} runs.

                        %{name}java build/jenesis/Make.java%{reset}         Build every module
                        %{name}java build/jenesis/Make.java stage%{reset}   Run another selector
                        %{name}jenesis stage%{reset}                        The same on the installed CLI

                      Every step prints a line naming its place in the build graph, and those
                      names are the selector grammar: run %{name}build%{reset} once and read them back.

                    %{header}Selectors:%{reset}
                      %{name}build%{reset}         Resolve, compile, package, and test every module
                      %{name}stage%{reset}         Stage produced artifacts into a local repository
                      %{name}export%{reset}        Export the staged repository as the build deliverable
                      %{name}release%{reset}       Release the staged trees: into jenesis.release.uri and
                                    jenesis.deploy.uri, and by the plugins of the hook point release;
                                    a dry run that publishes nothing unless the command line passes
                                    %{name}-Djenesis.release.publish=true%{reset}
                      %{name}plugin/<name>%{reset} Run a plugin the project names under the hook point plugin, on demand
                      %{name}pin%{reset}           Rewrite version/checksum pins into pom.xml or module-info.java,
                                    after building, staging and releasing dry what it pins
                      %{name}dependencies%{reset}  Print each module's resolved dependency graph; with
                                    %{name}-Djenesis.test.skip=true%{reset} it runs no tests on the way
                      %{name}ide%{reset}           Generate IntelliJ IDEA, VS Code, and Eclipse project metadata
                      %{name}metadata%{reset}      Refresh the metadata module outputs
                      %{name}configuration%{reset} Print every setting with the value in force, one per line
                      %{name}properties%{reset}    Print only the %{name}-Djenesis.*%{reset} properties that are set
                      %{name}help%{reset}          Print this message
                      %{name}skill%{reset}         Print the briefing for a coding agent; %{name}skill/start%{reset} where to begin
                      %{name}prompt%{reset}        Print a task to hand a coding agent; %{name}prompt/migrate%{reset} moves a build here

                      %{name}+<module>%{reset} narrows %{name}build%{reset} to one module, not %{name}stage%{reset}, %{name}export%{reset} or
                      %{name}pin%{reset}, and %{name}+<module>/<step>%{reset} narrows it to a single step inside that
                      module; %{name}:%{reset} matches one path segment and %{name}::%{reset} any depth, as in
                      %{name}::/test%{reset}.

                    %{header}Watching what a step does:%{reset}
                      A step prints its name and its timing, and nothing of the tool underneath.
                      To see into one:
                      %{name}-Djenesis.print.process%{reset}   Stream each external tool's command line and its
                                                output to the console as it runs - the first thing
                                                to reach for when a step fails or will not finish.
                                                Narrow it to one tool with %{name}-Djenesis.print.<command>%{reset},
                                                as %{name}-Djenesis.print.javac%{reset} or %{name}-Djenesis.print.tests%{reset}
                      %{name}-Djenesis.print.command%{reset}   Print those command lines without their output
                      %{name}-Djenesis.print.fetch%{reset}     Name each artifact as it is fetched from a repository -
                                                what a long, quiet resolve is busy with
                      %{name}-Djenesis.print.cache%{reset}     Print each step served from or written to the cache
                      %{name}-Djenesis.print.signatures%{reset}
                                                Name each verified dependency with its signer, and each
                                                one no key covers
                      %{name}-Djenesis.print.checksum%{reset}  Print each step's input and output checksums, to
                                                see what made a step re-run
                      %{name}-Djenesis.print.progress=false%{reset}
                                                Drop the progress lines themselves

                      To always build this way, keep the setting in your own
                      %{name}~/.jenesis/jenesis.properties%{reset} rather than typing it (see below).

                    %{header}Configuration:%{reset}
                      Every setting is a %{name}-Djenesis.<area>.<name>%{reset} property. Three places set
                      them, each overriding the one before it:

                        %{name}jenesis.properties%{reset}             The project's own, committed with it
                        %{name}~/.jenesis/jenesis.properties%{reset}  Your own, for every project
                        %{name}-Djenesis.<key>=<value>%{reset}        This one run

                      So a taste in output lives in your home folder and travels with you,
                      what your machine settles holds for every project it builds, and a build
                      server that has no such file is unaffected by it. A project's file may not
                      name a program the build runs, a credential, a folder this machine shares or
                      a folder outside the project. A setting
                      may also follow the main class, before the selectors, and a whole command
                      line can live in a file the way the JDK's own tools read one:

                        java build/jenesis/Make.java -Djenesis.project.version=1.0.0 build
                        java build/jenesis/Make.java %{name}@release.args%{reset}

                      %{name}@<file>%{reset} expands to the settings and selectors it holds, one or more per
                      line, %{name}#%{reset} starting a comment and quotes holding what would otherwise split;
                      %{name}@@<text>%{reset} is an argument that starts with an @ rather than a file.
                      %{name}configuration%{reset} prints every setting with the value in force and whether
                      it is set or default, one per line to grep:

                        java build/jenesis/Make.java configuration | grep print

                      A module is configured by the %{name}@jenesis.*%{reset} tags on its %{name}module-info.java%{reset}
                      and by the files in its %{name}build.jenesis%{reset} folder.

                    %{header}Reading further:%{reset}
                      %{name}https://jenesis.build/tool%{reset}  The documentation
                      %{name}configuration%{reset}               Every setting, its value and what it does
                      %{name}skill%{reset}                       The tool as a briefing for a coding agent, by page
                    """)
                                     .replace("%{layout}", layout)
                                     .replace("%{assembler}", assembler)
                                     .replace("%{reset}", palette.reset())
                                     .replace("%{header}", palette.heading())
                                     .replace("%{name}", palette.detail())
                                     .replace("%{title}", palette.status()));
        }
    }

    private record PromptModule(Consumer<String> out) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            buildExecutor.addModule("migrate", (_, _) -> out.accept("""
                    Migrate the build of this project to Jenesis, which is vendored in build/jenesis
                    and needs nothing but the JDK. First run `java build/jenesis/Make.java skill/start`,
                    then follow the briefing of `java build/jenesis/Make.java skill/migrate` step by
                    step:

                    - Keep the existing build working beside the new one until what both produce
                      compares equal, and commit after every step that builds.
                    - Move the build to pom.xml first. Where the briefing finds that the code can be
                      modules, carry out the move to module-info.java afterwards, as a second phase
                      of its own.
                    - Replace every use of shading with a layer or with packaging, as the briefing
                      describes, rather than with a relocated copy of a library.

                    When you are done, give me a summary of the migration: what replaced each part of
                    the old build, what was dropped and why, how the two builds compare - the tests
                    run, the jar contents, the dependency tree and the published POM - and where a
                    layer or packaging replaced shading.
                    """));
        }
    }

    private record SkillModule(Path target, Consumer<String> out) implements BuildExecutorModule {

        private static final List<String> PAGES = List.of(
                "start", "invoke", "layout", "migrate", "registry", "target", "selectors", "tags",
                "tools", "settings", "execute", "pinning", "plugins", "extend", "demos", "engine");

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            for (String page : PAGES) {
                buildExecutor.addModule(page, new SkillPage(target, out, page));
            }
        }
    }

    private record SkillPage(Path target, Consumer<String> out, String page) implements BuildExecutorModule {

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
            out.accept((switch (page) {
                case "start" -> """
                    # Jenesis build tool - operating instructions

                    You are in a Jenesis-built Java project. This overview is what every task needs;
                    each page below goes deeper and is printed by naming it as a selector, as
                    `java build/jenesis/Make.java skill/tags`, and `skill` alone prints them all.
                    Full documentation: https://jenesis.build/tool. `help` prints a short human
                    orientation.

                    ## Essentials

                      java build/jenesis/Make.java [selectors...]  build; no selector runs `build`
                      java build/jenesis/Make.java configuration    every setting and the value in force
                      java build/jenesis/Execute.java [args...]     build and run the main class

                    - Never delete target/ and never pass -Djenesis.executor.rebuild=true: every step
                      is keyed by its inputs, so a build re-runs exactly what changed.
                    - What a step produced is in %{target}/<step path>/output/ - a build step's
                      below %{target}/build/, a staged tree's in %{target}/stage/<tree>/ - and
                      that path without the target/ prefix and /output is a selector of its own.
                    - A step prints [EXECUTED] when it ran, [SKIPPED] when its inputs had not changed
                      and its last output was reused, and [FAILED] with the error.
                    - After editing a build step's code, bump its serialVersionUID, or its cached
                      output is reused.
                    - Whenever you add or change a dependency, offer to run `pin`.
                    - Find the demo that matches the task and copy its shape rather than inventing
                      configuration. A number a page cites, as (62), is the demo-62-* folder of
                      https://github.com/jenesis/jenesis/tree/main/demo; skill/demos lists them.
                    - Moving a Maven or Gradle build here? Follow skill/migrate step by step, and end
                      with the summary of the migration it asks for. A user hands an agent that task by
                      entering `! java build/jenesis/Make.java prompt/migrate` in its prompt, or by
                      pasting what the command prints.

                    ## First moves in a project you do not know

                      java build/jenesis/Make.java                                 build, then read
                                                                                   target/.jenesis.events.jsonl
                      java build/jenesis/Make.java configuration | grep -F "[set]" what this project changed
                      java build/jenesis/Make.java dependencies                    what each module resolves

                    ## Pages

                      skill/invoke     entry points, the installed CLI, embedding, the JDK it runs on
                      skill/layout     maven, modular or modular_to_maven, and how one is picked
                      skill/migrate    move a Maven or Gradle build here, one concern at a time
                      skill/registry   the built-in that replaces each Maven or Gradle plugin
                      skill/target     what target/ holds, folders as selectors, per-module properties
                      skill/selectors  the entry points, +<module>, : and ::
                      skill/tags       pins, BOMs, aliases, layers, agents, native access, signatures
                      skill/tools      a file in a module switches a tool on: tests, quality, generators
                      skill/settings   the configuration selector, where settings live, the useful ones
                      skill/execute    Execute, the ToolProvider services, @<file> arguments
                      skill/pinning    versions, checksums, signatures, and when to offer them
                      skill/plugins    jenesis.plugins.properties, its hook points, inputs and arguments
                      skill/extend     write a plugin for what nothing built in does
                      skill/demos      the demos as a recipe book, by topic
                      skill/engine     editing a build step, and reading the source when stuck
                    """;
                case "invoke" -> """
                    # Jenesis - Invoke the build

                      java build/jenesis/Make.java [selectors...]  source mode, always available
                      jenesis [selectors...]                       installed CLI
                      ToolProvider.findFirst("jenesis-make").orElseThrow().run(out, err, selectors...)
                                                                   embedding it in a Java program

                    `Make` is the entry point, `Project` the configuration API and has no `main`. No
                    selector runs `build`; several, space-separated, run in one invocation. Pass
                    -Djenesis.palette.colors=none to read the output as plain text, without the
                    escape sequences that colour it. The `jenesis-make` ToolProvider takes leading
                    -Djenesis.* arguments as that run's settings and prints to the writers it is
                    handed, so a program builds in its own JVM without touching its properties.

                    The installed `jenesis` verifies `build/jenesis` against the released sources
                    named in `build/jenesis/jenesis.version` and refuses a tree that differs, while
                    `jenesis-make` runs the installed engine as it stands. Only source mode runs the
                    project's vendored build code; a ToolProvider runs the build.jenesis that the
                    embedding program resolved.

                    `Make` compiles the engine once and reuses those classes. Drive them yourself:

                      javac -d .jenesis/classes build/jenesis/Project.java
                      java -cp .jenesis/classes build.jenesis.Make [selectors...]

                    Pin the JDK a build runs on with -Djenesis.toolchain.version or the same key in
                    jenesis.properties, as 25, 25.0.3 or 25-temurin, its words matched against the
                    vendor and version in each JDK's release file. Make and Execute check the running
                    JVM first and otherwise relaunch on the newest match found under
                    jenesis.toolchain.searchpath, this system's usual JDK folders unless set, and only
                    the command line or ~/.jenesis/jenesis.properties may set it. Nothing is installed
                    unless jenesis.toolchain.installer, set in the same places or as the environment
                    variable JENESIS_TOOLCHAIN_INSTALLER, names a program that gets the version when no
                    JDK matches; the jenesis command names its jenesis-jdk there, for the run it starts,
                    where SDKMAN, mise or Scoop installed it, and that calls the tool back.

                    A project with its own entry point calls `new Make("build.Demo").run(selectors)`,
                    which returns the status to exit with. For a GraalVM native launcher, read the
                    documentation: it needs reachability metadata captured from a real build, a JDK
                    on PATH, and it cannot load foreign build modules.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "layout" -> """
                    # Jenesis - Take the layout the project infers

                      maven             pom.xml per module; jar plus pom.xml
                      modular           module-info.java per module; modular jar, no pom
                      modular_to_maven  module-info.java per module; modular jar plus generated pom

                    `auto` picks maven for a root pom.xml, else modular_to_maven; it never picks
                    plain modular. Override only with cause: -Djenesis.project.layout=<name>.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "migrate" -> """
                    # Jenesis - Migrate a Maven or Gradle build

                    Move one concern at a time and build after each step, so a failure always has a
                    single cause, and keep the old build working beside this one until what both
                    produce compares equal. The user receives a summary of the migration at the end,
                    as step 7 describes, so note each decision as it is taken.

                    ## 1. Bring the tool in

                      curl -fsSL https://get.jenesis.build | bash    vendor build/jenesis into the project
                      sdk install jenesis && jenesis-init            the same through SDKMAN

                    The build runs on JDK 25 or newer and needs nothing else; the release it compiles
                    for is a setting of its own, so an older target is no obstacle. Commit
                    build/jenesis and ignore what a build writes:

                      target/        every step's output; Maven's own target/ is the same folder
                      .jenesis/      the compiled engine and the artifacts a build fetched

                    A .gitignore line `build` or `build/`, common where Gradle ran, also hides
                    build/jenesis, and git cannot re-include a file below an ignored folder: write
                    `/build/*` and `!/build/jenesis/` instead. Gradle's own output is build/ as well,
                    so the root project's `clean` deletes build/jenesis (`git checkout --
                    build/jenesis` restores it). Move Gradle's output aside for as long as both
                    builds run, with one line in the root build.gradle.kts or build.gradle alike:

                      allprojects { layout.buildDirectory = layout.projectDirectory.dir("gradle-build") }

                    Every project of the Gradle build then writes into a gradle-build/ of its own,
                    which .gitignore lists in place of build/, and `clean` leaves build/jenesis
                    alone; gradle/ is no such name, since it holds the wrapper. `mvn` writes into
                    the target/ this build reads, so run a Maven build in a second checkout (`git
                    worktree add`) while both exist. Exclude build/jenesis from a header or licence
                    check the old build runs (Apache RAT, license-maven-plugin), which would flag
                    the vendored sources.

                    ## 2. Choose the build declaration

                      pom.xml            the maven layout; the quicker move. A Maven build keeps
                                         its pom.xml files as they stand, a Gradle build writes
                                         one per project; steps 3 and 4 follow it.
                      module-info.java   the modular_to_maven layout; the Java Module System
                                         declares the build, and the published POM is generated
                                         from it; step 3b follows it.

                    Check first whether the code can be modules as it stands. It cannot while a
                    package is split: held by the tests and the main code of one project, as
                    white-box tests are, or by two projects of the build. This program prints each
                    split package and where it is held, per project and across the main sources of
                    all of them, counting Java, Kotlin, Groovy and Scala sources of every source set,
                    so a Gradle integrationTest or jmh set counts as the tests do. Write it to a file outside the sources, and run it from the root as
                    `java SplitPackages.java`: the JDK the build runs on runs a source file as it
                    stands, on any operating system, so no shell tool is needed.

                      void main() throws IOException {
                          var source = Pattern.compile(
                                  "(.*)src/([a-zA-Z0-9]+)/(?:java|kotlin|groovy|scala)/(.+)/[^/]+[.](?:java|kt|groovy|scala)");
                          var owners = new TreeMap<String, Set<String>>();
                          try (var files = Files.walk(Path.of("."))) {
                              files.map(file -> file.toString().replace(File.separatorChar, '/'))
                                      .map(source::matcher)
                                      .filter(Matcher::matches)
                                      .forEach(match -> owners.computeIfAbsent(match.group(3), _ -> new TreeSet<>())
                                              .add(match.group(2) + " " + match.group(1)));
                          }
                          owners.forEach((folder, held) -> {
                              if (held.stream().filter(owner -> owner.startsWith("main ")).count() > 1
                                      || held.stream().anyMatch(owner -> !owner.startsWith("main ")
                                              && held.contains("main " + owner.substring(owner.indexOf(' ') + 1)))) {
                                  IO.println(folder.replace('/', '.') + " " + held);
                              }
                          });
                      }

                    First, though, the release: code compiled for Java 8 or older, as
                    maven.compiler.release or target, a toolchain or a Gradle release of 8 or below
                    says, can never be a module, because a module-info.java needs release 9. Such a project
                    migrates to pom.xml and stays there, whichever declaration was asked for: say so,
                    and attempt no phase two. Where it ships a descriptor for Java 9 and later, the
                    module-info.java lives in META-INF/versions/9/ of its sources and the pom.xml build
                    compiles it there, with no annotation processing and so none of the main
                    compilation's -A options, against the dependencies that carry a module name; one whose
                    jar declares none is named by a
                    <!--jenesis.alias <module> <groupId>/<artifactId>--> comment in the pom.xml, the
                    name the descriptor requires. The old build follows the move: ModiTect's
                    moduleInfoFile, and maven-javadoc-plugin's sourcepath and excludePackageNames.

                    Otherwise a package that SplitPackages.java prints, or one that a dependency
                    holds as well, rules modules out for now: migrate to pom.xml first, whichever
                    declaration was asked for, and say so - which packages are split and where.
                    That is phase one; it changes the build and nothing of the code. Phase two moves the result to
                    module-info.java as a change of its own, once the pom.xml build compares equal
                    with the old one, so close phase one by naming the split packages and offering
                    phase two, as step 3b describes. A Gradle build has no counterpart, since no
                    build.gradle is read: a pom.xml needs only coordinates, dependencies with their
                    scopes and maven.compiler.release. The first build that passes is the baseline
                    every later step is compared against. skill/layout has the rest.

                    ## 3. Know what a pom.xml keeps

                      read       coordinates; a parent, local when relativePath points at one with the
                                 same coordinates, fetched otherwise, and the build fails naming one
                                 it cannot fetch; a -SNAPSHOT from the local repository, or else
                                 as the remote repository's maven-metadata.xml names its newest
                                 timestamped file; <modules>; <properties> and
                                 ${...}, which neither a JVM property nor the environment fills, so
                                 a dependency naming one no POM defines fails the build naming it;
                                 dependencies of compile, provided, runtime and test scope;
                                 <optional>, <exclusions> with wildcards, <type> and <classifier> -
                                 a type other than jar, test-jar, ejb, ejb-client, maven-plugin and
                                 bundle is followed for its dependencies but placed on no path, as
                                 Maven places it on none, and refused as a dependency of a module
                                 the build builds unless it is pom;
                                 <dependencyManagement> with import-scoped BOMs, an entry's
                                 exclusions applied wherever its dependency appears;
                                 maven.compiler.release, or else maven.compiler.target or its
                                 source, 1.8 read as 8 - with none of them the JDK the build runs
                                 on, which a [RELEASE] line names; maven.compiler.testRelease for
                                 the tests and maven.compiler.enablePreview; name, description,
                                 url, inceptionYear, licenses with their distribution, developers,
                                 organization, scm, issueManagement and ciManagement of the
                                 module's own POM, and all of them but the name from its parents,
                                 local or fetched, where it declares none;
                                 sourceDirectory, testSourceDirectory and the <directory> of each
                                 resource, a local parent's where the module names none, and
                                 beside them src/main/<name> and src/test/<name> where they exist
                                 for each name -Djenesis.maven.sources lists, as kotlin,groovy where
                                 the Kotlin and GMavenPlus plugins read those folders; the <!--jenesis.plugin--> and
                                 <!--jenesis.alias--> comments of the module's POM and of a local
                                 parent; the <!--jenesis.pin--> comment of the module's own POM,
                                 where `pin` writes it, and never a parent's;
                                 a profile of any POM - the module's, a parent's, a BOM's or a
                                 dependency's - that Maven activates by <jdk>, matched against the
                                 JDK the build runs on (an unclosed [9, or [9 reads as [9,); a
                                 fetched POM's range that is none leaves its profile inactive, the
                                 project's own is refused), by <os>, its family, name, arch and
                                 version matched against the machine the build runs on as Maven
                                 matches them, or by <activeByDefault> when no
                                 other profile of that POM is active, with its <properties>,
                                 dependencies, <dependencyManagement> and resource directories
                      ignored    <build><plugins> and <pluginManagement>, so a compiler's
                                 <includes> and <excludes> as well and every source of a source
                                 directory compiles, a profile activated by
                                 a property, a file or -P, a profile's <modules>, which move
                                 into the POM's own <modules> to be built,
                                 <repositories> and settings.xml, a resource's includes,
                                 excludes, targetPath and filtering, system scope, and every
                                 packaging but jar and bundle
                                 - a pom aggregator is followed for its modules, a bundle
                                 (maven-bundle-plugin) is built as a jar whose OSGi headers are the
                                 project's own business, a MANIFEST.MF among the resources or a
                                 plugin, a war or any other packaging is not built at all and says
                                 so with a [SKIPPED] line, a jar with neither sources nor
                                 resources is built only where its src/main/build.jenesis
                                 configures a plugin, its tests without sources only where its
                                 src/test/build.jenesis does, and a src/test/java/module-info.java
                                 is a module of its own rather than patched into the main one,
                                 compiled as a module but run on the class path, so tests of Java
                                 Module System behaviour stay with the old build until the project
                                 moves to module-info.java
                      a BOM      a module of pom packaging that lists no modules but declares a
                                 <dependencyManagement>, as a library's -bom does, is staged, exported
                                 and released as its POM alone: its coordinate, packaging and
                                 metadata, and that <dependencyManagement> with its ${...}
                                 resolved and its parent's left out, since the parent is not
                                 published; stage=false in its packaging.properties keeps it out
                                 of the staged repositories, as it does a module's jar

                    A test module-info.java that names the main module itself, the --patch-module
                    idiom Gradle and Maven use for white-box tests, is not supported: compiled as a
                    module of its own it shadows the main one, and javac fails on every main class.
                    With pom.xml, move it out of the test sources, to a folder the old build alone
                    compiles, so the tests run on the class path against the main jar and an
                    --add-opens of theirs targets ALL-UNNAMED; with module-info.java, give the tests
                    a module of their own, as step 3b takes white-box tests apart.
                    Tests that ran on the module path behave differently on the class path: a
                    service the main module-info.java provides is found only where a
                    META-INF/services file names it, a stack trace and Class.getModule name no
                    module, and resources are found by scanning the class path. A test that
                    asserts one of them stays with the old build until the project moves to
                    module-info.java.

                    Nothing ignored is reported, so list the old build's plugins, profiles and
                    repositories before deleting anything: each needs an answer in step 4. A
                    resource directory is copied whole, so one that holds target/ or .jenesis/,
                    as ./ does, fails the build and its file moves to jenesis.project.resources.
                    ${project.basedir} in an inherited directory is the inheriting module's folder,
                    as in Maven, and a named directory that does not exist gets a [RESOURCES] line.
                    A source directory gives the jar only what its compilers read - .java, .kt,
                    .scala, .groovy - as Maven's does, so a template beside the sources stays
                    out of it, and a file that must ship moves to a resource directory.
                    A module's tests run in the folder the build was started in, the project's
                    root, rather than in the module's folder as Surefire's do, and see no basedir
                    property: a test that opens src/test/... by a relative path reads the file as
                    a test resource instead, or resolves it against a -Dbasedir=<module folder>
                    line of the module's process-test.properties. The tests run without -ea,
                    which Surefire and Gradle set, so a test that relies on an assert names -ea in
                    that file as well.
                    A module inherits its parents' metadata as Maven's model does: the url and the
                    scm locations with the module's artifactId appended, unless the parent sets
                    child.*.inherit.append.path="false", and licenses or developers only as a whole.
                    What a module declares or inherits wins; project.properties at the root, which
                    skill/target lists the keys of, fills in only what the POMs leave out.

                    ## 3b. Or declare the build in module-info.java

                    Directly, where step 2 found no split package, or as phase two over a pom.xml
                    build that already compares equal. Phase two first takes the splits apart, one
                    package at a time and building after each:

                      white-box tests      move them into packages of the test module's own, such
                                           as <package>.test, testing the public API where they
                                           can; a package the module does not export is made
                                           readable to the tests by
                                           `--add-exports=<module>/<package>=<test module>` in
                                           both process-javac.properties and
                                           process-test.properties of the test module, which
                                           leaves the published module-info.java as it is, where
                                           `exports <package> to <test module>` would publish it
                                           and make javac warn that the test module is not found
                                           when it compiles the main one, so -Werror would need
                                           `-Xlint\\:-module=` beside it
                      package-private      a member a test reaches in a package the module
                                           exports already is called by reflection rather than
                                           made public, which would widen the published API:
                                           JUnit's ReflectionSupport finds and invokes a method
                                           and reads a field (tryToReadFieldValue), given
                                           `--add-opens=<module>/<package>=org.junit.platform.commons`
                                           in process-test.properties, since the package opens to
                                           the module that makes the member accessible; it writes
                                           no field, so a test that sets one opens the package to
                                           the test module as well and calls Field.set itself
                      two projects         move the classes so each package lives in one module,
                                           or merge the projects, keeping a qualified export for
                                           a package only a sibling uses
                      a dependency         exclude the jar that holds the package, or move the
                                           project's classes out of it
                      two dependencies     no module path holds a package two jars share; after a
                                           build of the pom.xml, SharedPackages.java below prints
                                           each package two jars of a test closure hold, of the
                                           groups main and test that the module path takes and
                                           not of a processor's or a tool's - exclude one of
                                           them, or drop the test that needs both

                      void main() throws IOException {
                          var placed = Pattern.compile("(main|test)/(compile|runtime)/.+");
                          var closure = new TreeSet<Path>();
                          try (var files = Files.walk(Path.of("target/build"))) {
                              for (var index : files.filter(file -> file.toString().contains("testmodule-")
                                      && file.getFileName().toString().equals("dependencies.properties")).toList()) {
                                  var dependencies = new Properties();
                                  try (var reader = Files.newBufferedReader(index)) {
                                      dependencies.load(reader);
                                  }
                                  for (var key : dependencies.stringPropertyNames()) {
                                      var jar = index.resolveSibling(dependencies.getProperty(key).split(" ")[0]);
                                      if (placed.matcher(key).matches() && Files.isRegularFile(jar)) {
                                          closure.add(jar.normalize());
                                      }
                                  }
                              }
                          }
                          var holders = new TreeMap<String, Set<String>>();
                          for (var jar : closure) {
                              try (var zip = new ZipFile(jar.toFile())) {
                                  zip.stream().map(ZipEntry::getName)
                                          .filter(name -> name.endsWith(".class") && name.contains("/")
                                                  && !name.startsWith("META-INF/"))
                                          .forEach(name -> holders.computeIfAbsent(
                                                  name.substring(0, name.lastIndexOf('/')),
                                                  _ -> new TreeSet<>()).add(jar.getFileName().toString()));
                              } catch (ZipException _) {
                              }
                          }
                          holders.forEach((folder, jars) -> {
                              if (jars.size() > 1) {
                                  IO.println(folder.replace('/', '.') + " " + jars);
                              }
                          });
                      }

                    The tests then run on the module path, which breaks what read the class path:

                      an internal package  a test module reads only what the tested module
                                           exports, at compile time and when the tests run, so
                                           the --add-exports of white-box tests above goes into
                                           both files, or javac passes and the tests fail with
                                           IllegalAccessError
                      the unnamed package  a module holds none, so a test class there fails to
                                           load: move it into a package, and have a test that
                                           needs such a class compile it at run time
                      javac in a test      compile-testing and javax.tools compile against
                                           java.class.path, which is empty now: hand the compiler
                                           -classpath with System.getProperty("jdk.module.path"),
                                           or append that property to java.class.path before the
                                           tests run, from a LauncherSessionListener the test
                                           module provides
                      mocking, proxying    a library that mocks or proxies an interface of a JDK
                                           module, as java.compiler's Element, needs to read that
                                           module: `--add-reads=<library module>=java.compiler`
                                           in process-test.properties, naming the module concerned
                      a jar that is no     a dependency that names a module but holds a class
                      module               outside any package fails the tests' module path with
                                           a FindException, and nothing keeps one dependency of
                                           a module on the class path: move the tests that need
                                           it to a test source folder only the old build
                                           compiles; skill/execute lists what else stops a JVM
                      a test resource      one that shadows a main resource of the same name puts
                      overriding           its folder into two modules, which no module path
                                           allows: move it to a test-only folder whose name is no
                                           package, as test-config/, and point the tests there
                      a resource bundle    ResourceBundle.getBundle finds only a bundle of the
                                           module that calls it, so Spring's MessageSource finds
                                           none of the application's: read it as a resource of an
                                           open package or of a folder that is no package, as
                                           ReloadableResourceBundleMessageSource does

                    Each module is the folder whose module-info.java sits at the root of its
                    sources; in a Maven tree that is src/main/java, so the file stays where it is.
                    Every module-info.java below the root becomes a module, so one the old build
                    keeps for itself, as src/moditect/module-info.java, needs an empty .jenesis.skip
                    file in its folder, which leaves that folder and all below it out.
                    A root pom.xml makes the build pick the maven layout, so pass
                    -Djenesis.project.layout=modular_to_maven while the old build still needs it.
                    The pins `pin` wrote into pom.xml do not carry over: until `pin` runs again, the
                    module build resolves the newest versions. Write a bare @jenesis.pin <module>
                    <version> for each required module whose version to keep, and @jenesis.pin
                    <groupId>/<artifactId> <version> for one only a dependency's POM brings in, as
                    a module name pins only a module that is required by it. Then run `pin`, which
                    adds the checksums and the closure.
                    The old build's javadoc fails on the @jenesis tags of a module-info.java as
                    unknown, so register each tag the module uses as disabled there - javadoc takes
                    no wildcard: `-tag jenesis.pin:X` on its command line, Gradle's
                    `tags("jenesis.pin:X")` among the javadoc options, a
                    <tag><name>jenesis.pin</name><placement>X</placement></tag> in the <tags> of
                    maven-javadoc-plugin.
                    Maven's compiler plugin compiles a src/test/java/module-info.java whatever its
                    testExcludes or useModulePath say, and fails on the modules it requires, so
                    Maven compiles a copy of the tests instead: a copy-resources execution of
                    maven-resources-plugin at generate-test-sources copies src/test/java's *.java
                    but module-info.java into a folder of target, which default-testCompile names
                    as its only compileSourceRoots.

                      a dependency         `requires <module>`, `requires static` where it is only
                                           compiled against; a jar that declares no module name
                                           resolves by the name the Jenesis Module Index gives it,
                                           or by @jenesis.alias <module> <groupId>/<artifactId>
                      a version            @jenesis.pin <module> <version> for a required module,
                                           <groupId>/<artifactId> for one a POM brings in, which
                                           `pin` writes; @jenesis.bom for a BOM
                      the release          @jenesis.release <N> on the module's Javadoc
                      a processor          @jenesis.plugin maven/<groupId>/<artifactId>
                      a main class         @jenesis.main <class>
                      resources            there is no resources folder: a file beside the sources
                                           is packaged, so src/main/resources moves into
                                           src/main/java; -Djenesis.project.resources places a
                                           file of the project
                      the tests            a module of their own, such as src/test/java, whose
                                           module-info.java names another module, carries
                                           @jenesis.test <module it tests> and `requires` it and
                                           the test framework; its packages differ from the
                                           tested module's
                      name, description    the first sentence and the second paragraph of the
                                           module's Javadoc
                      the coordinate       groupId from the first two segments of the module name
                                           (jenesis.maven.segments), artifactId the module name;
                                           project=<groupId> and artifact=<artifactId> keep a
                                           published one: in project.properties at the root for
                                           every module, in META-INF/build.jenesis/project.properties
                                           beside a module's sources for that module alone, which
                                           wins over the root for any key
                      url, licenses,       project.properties, with the keys skill/target lists
                      developers, scm
                      the version          version=<version> in project.properties at the root,
                                           which -Djenesis.project.version overrides

                    demo-02 is one module, demo-04 several with a test module, demo-73 the
                    published coordinate; skill/tags lists every tag.

                    ## 4. Replace each plugin

                    Look every plugin up in skill/registry. Most become a file that switches a
                    built-in tool on, a line of packaging.properties, a @jenesis tag or <!--jenesis-->
                    comment, or a -Djenesis.* setting; copy the demo it names. Such a file sits in a
                    configuration location of the module: in the maven layout <module>/build.jenesis/
                    for both halves, src/main/build.jenesis/ for the main code alone and
                    src/test/build.jenesis/ for the tests alone, in a module-info.java build the
                    META-INF/build.jenesis/ folder beside the module's sources, and build.jenesis/ at
                    the root for every module. The plugin that runs a tool resolves in a group named
                    after it, such as checkstyle or jacoco, and the tool in <name>:tool, such as
                    checkstyle:tool, JaCoCo's agent in jacoco:agent; each resolves once per module,
                    since each module resolves alone, and a pin naming any other group is ignored.
                    A pom.xml pins it with a comment that is a child of <project>, one coordinate per
                    line, `<!--jenesis.pin checkstyle:tool/maven/<groupId>/<artifactId> <version>-->`,
                    which `pin` completes with the tool's closure. That comment is the module's own:
                    `pin` writes it into every module's POM and a parent's is not inherited, while a
                    <!--jenesis.plugin--> comment is, from a local parent but never from a POM that only
                    lists the module under <modules>. A module-info.java pins it with
                    @jenesis.pin checkstyle:tool/maven/<groupId>/<artifactId> <version>. Configuration that lived
                    inside a plugin moves here:

                      compilerArgs, options.compilerArgs   process-javac.properties
                      annotationProcessorPaths             @jenesis.plugin, or <type>processor</type>;
                                                           one named without a version takes
                                                           it from <dependencyManagement>
                      surefire includes and groups         -Djenesis.test.filter, -Djenesis.test.tag
                      surefire excludes                    -Djenesis.test.exclude=<regex>[,...],
                                                           as .*IntegrationTest, after the default
                                                           naming or the filter
                      argLine, jvmArgs of the tests        process-test.properties
                      surefire dependenciesToScan,         the scan key of test.properties, as no
                      testClassesDirs += zipTree(<jar>)    plugin block is read: the jar's test classes
                                                           run among the module's own
                      a second surefire <execution>, an    a sibling module that requires the tests,
                      extra Test task with other flags     scans them and carries its own
                                                           process-test.properties
                      environment of the tests             environment-test.properties
                      mainClass of the jar, shade or exec  a <mainClass> property in <properties>:
                      plugin                               the jar's Main-Class and what Execute,
                                                           launcher=true and bundle=true launch,
                                                           which stage nothing without one
                      manifestEntries,                     a META-INF/MANIFEST.MF among the resources,
                      jar.manifest.attributes              the basis of the jar's manifest; jar takes
                                                           no --manifest in process-jar.properties
                      a resource outside the resource      -Djenesis.project.resources=
                      folders, or with a targetPath          <file>:<path in the jar>
                      a <profile> chosen with -P or a      jenesis.properties and a
                      property, Gradle properties          jenesis-<profile>.properties each, selected
                                                           with -Djenesis.make.profiles
                      <repositories>, settings.xml         -Djenesis.maven.uri or MAVEN_REPOSITORY_URI;
                                                           its credential, jenesis.maven.token, never
                                                           in a file the project provides
                      a toolchain                          -Djenesis.toolchain.version
                      a -tests jar (test-jar goal)         -Djenesis.stage.tests=true
                      maven.deploy.skip, maven.install.skip
                      as for a module of tests alone       stage=false in packaging.properties: built
                                                           and tested, never staged, so neither
                                                           exported nor released; the pom.xml
                                                           property itself is not read

                    A process-<tool>.properties line is a flag and its argument, `-Xmaxwarns=500`, and a
                    bare flag has an empty value, `-parameters=`. A flag given more than once, as --add-opens
                    is, takes one argument per line of its value:
                    `--add-opens=java.base/java.lang=ALL-UNNAMED\\njava.base/java.util=ALL-UNNAMED`,
                    and a flag that takes two arguments has them parted by a tab,
                    `-linkoffline=https\\://example.com/api/\\toffline/api` for javadoc's.
                    A flag that holds a `:` or `=` escapes
                    it, since a properties file splits there: `-Xlint\\:all=`. A system property is the
                    one exception and is written as on a command line, `-Dkey=value`, its value a
                    variable where it differs per run, `-DshardIndex=@shard/0`. The release is not such a
                    flag: a `--release` there is refused, since maven.compiler.release, its testRelease
                    or @jenesis.release declares it for every tool. javac runs without -g, where
                    Maven and Gradle pass it, so a test reading parameter or local names needs `-g=`
                    or `-parameters=`. A plugin may pass flags its configuration never shows, as
                    a convention plugin adds -parameters or a compiler plugin its own, so compare the
                    old build's effective javac arguments: `mvn -X compile` prints them after
                    "Command line options:", `gradle compileJava --debug` on its "Compiler
                    arguments:" line. Checkstyle reads a copy of the sources below target/build/,
                    so a suppression keyed on a source folder, as src/test/java, matches nothing:
                    key it on the package's folders, as `[/\\\\]example[/\\\\]test[/\\\\]`. A key of
                    jenesis.properties keeps its prefix, `jenesis.test.tag=-slow`, as
                    `configuration` prints it.

                    Shading is not supported: nothing is relocated, and no class file is rewritten.
                    Convert every use of it, and always name each conversion to the user, with the
                    reason: a shaded library's classes sit in another jar under another name, where
                    licence and compliance checks no longer find it, so its licence goes
                    unattributed and a vulnerability in it unreported, and where the usage
                    detection an open source project relies on, its downloads and the dependency
                    graphs that list it, no longer counts it, which can cost that project its
                    funding. A layer, and packaging with launcher=true or bundle=true, keep each
                    library a jar of its own under its own coordinate, pinned, verified and
                    reported like any other dependency, while the application still ships as one.
                    What shading did is answered one way each:

                      a dependency kept private, a version that must not meet the consumer's
                                           a layer: @jenesis.layer <name> provider <coordinate>
                                           resolves it and its closure into a ModuleLayer of its
                                           own at run time, so two versions share one JVM under
                                           the same package names, with the API module the layer
                                           shares named by @jenesis.layer <name> api <module>.
                                           Layers are declared in module-info.java alone, so a
                                           pom.xml build that shaded keeps the dependency plain
                                           until phase two; the published POM lists the launcher
                                           and the API module, never what a layer holds (demo
                                           24, 25)
                      one runnable jar     launcher=true in packaging.properties, which stores
                                           every jar apart inside it (demo 08), or bundle=true,
                                           a zip of the jars and the argument file that launches
                                           them (demo 10)
                      fewer dependencies   publish the dependency as a dependency, and make a
                                           `requires static` on it a `requires`

                    One module compiled at two releases is not supported either: javac compiles a
                    module once, at its release, and only META-INF/versions/<N>/ adds a newer one, a
                    class there replacing the class of the same name the base compiles. A pom.xml
                    with a second compiler execution at another release, or a jar plugin excluding
                    classes it compiled, is converted, and the conversion named to the user:

                      an entry class at a lower release than the rest, so callers on the older JVM
                      are told the feature is unavailable rather than failing to link
                                           compile the module at the lower release, against API
                                           stubs for the newer JDK types it uses, and route each
                                           call that needs the newer JVM through a class of one
                                           name in the base and in META-INF/versions/<N>/, the
                                           base answering what the older JVM can (demo 11);
                                           a public class only META-INF/versions/<N>/ holds makes
                                           a jar the JDK's jar tool refuses
                      classes compiled against but never shipped, API stubs above all
                                           a module of their own, `requires static` in
                                           module-info.java, <scope>provided</scope> in a pom.xml

                    jenesis.test.filter matches the whole class name and replaces the default naming
                    (Test*, *Test, *Tests, *TestCase, IT*, *IT, *ITCase, never a nested class), which
                    under JUnit 4, as in Surefire, takes a class only where it or a superclass
                    declares an @Test method, a @RunWith or a suite(), or extends TestCase. A filter
                    drops that check, unlike Surefire's includes, so under JUnit 4 every class it
                    matches runs, a nested helper included: where the default naming fits, leave
                    classes out with jenesis.test.exclude, which keeps the check. A filter, an exclude
                    and a tag each switch off -Djenesis.test.incremental, so a rerun runs every test
                    they select rather than those a change reaches. A
                    <module>/<regex> entry reaches that module only, and a module no entry reaches
                    runs no tests. In a pom.xml build <module> is the folder of the module's pom.xml
                    relative to the root, so the root module's entry is written /<regex>, with
                    nothing before the slash. jenesis.test.exclude takes the same entries, without a #method,
                    and leaves out what they match, so excluding one class keeps the default naming;
                    a module whose every test it leaves out runs none. The test classes of a jar the
                    scan key of test.properties names are taken by the same naming, filter, exclude
                    and tag, among the module's own. Test sources that hold no class
                    the default naming takes run none either, as with Surefire, and a [FINDINGS] line
                    says so, where a jenesis.test.filter entry that matches nothing fails the build.
                    The tests run against the module's jar, so a test that turns
                    getResource into a java.io.File fails with "URI is not hierarchical";
                    -Djenesis.test.jars=false runs them against its classes and resources folders
                    instead, as Maven does, while the modules it depends on stay jars. A build prints
                    no test totals: -Djenesis.print.tests streams the runner's summary, and the test
                    step's supplement/output keeps it. A test run writes no report unless
                    -Djenesis.test.reporting=true asks for the legacy JUnit XML and the
                    open-test-reporting XML in the module's reports/tests, whose folder a failed run
                    then names above the runner's output: pass it when a failure needs the record of
                    every test, as when more tests failed than the printed tail of the output holds,
                    or when CI reads the reports, and leave it off otherwise, as writing them slows a
                    test run.

                    A plugin the registry marks as having no built-in is either something done
                    differently here, which the line names, or a plugin to write: skill/extend.

                    ## 5. Pin, then compare

                    Until it is pinned, a tool the build resolves itself - JUnit's launcher, Error
                    Prone, Checkstyle, a code generator - takes its newest release, skipping one
                    with a pre-release qualifier such as -rc-1 or -M2 while a version without one
                    is published, so the first pin also settles those. `pin` runs the build it
                    pins, so whatever the changed versions reach, a module's tests included, runs
                    again; -Djenesis.test.skip=true leaves the tests to the next build. It runs
                    last, after `stage` and a dry `release`, so the tool of a release plugin such
                    as JReleaser is resolved and pinned with the rest.
                    Commit before the first `pin`. It writes the versions and checksums it resolved
                    into each pom.xml's <dependencyManagement>, updating an entry where it stands and
                    adding the others at its end; every other entry, an imported BOM, a sibling
                    module's and a comment, stays as written, a SNAPSHOT is given no checksum, and an
                    entry `pin` wrote that nothing resolves any longer is dropped. Review that diff. An entry keeps the ${...} its coordinate was written with, and
                    its version property where that still names the version resolved, so a
                    classifier an OS profile selects stays selectable. A pinned entry outranks a BOM, as any managed version does,
                    so it repeats the exclusions and the <scope> the BOM managed for it: to
                    move to a new BOM version, change it, delete the entries `pin` wrote and pin
                    again. `pin` resolves what the active profiles build, so run it with every profile
                    CI builds with, as -Djenesis.make.profiles=release,docker, or a tool only one of
                    them switches on stays unpinned. Then build with -Djenesis.dependency.pin=strict, as CI should. Before retiring the old build, compare what both produce: the jar
                    contents, the dependency tree (`dependencies` against mvn dependency:tree or
                    gradle dependencies), the number of tests run, and the POM a consumer receives,
                    which is generated here rather than copied from yours.
                    An edit to a method body compiles every module that depends on the edited one
                    again, unless jenesis.abi.mode is set in jenesis.properties - modular for a
                    module-info.java build, classpath for a pom.xml one - which skips a dependent
                    whose dependencies changed only below their ABI; set it before timing an edit
                    against the old build.
                    A pom.xml resolves a conflict as Maven does, the version nearest the root
                    winning, where Gradle takes the highest version requested. No setting of
                    jenesis.resolver.maven does the latter: latest takes the newest version the
                    repository lists for every dependency, whatever was requested, and a
                    module-info.java build resolves as a pom.xml does. Compare the dependency trees
                    and pin each version Gradle resolved higher - in <dependencyManagement>, or as
                    @jenesis.pin <groupId>/<artifactId> <version> for one only a POM brings in.

                    ## 6. Retire the old build

                    Remove what Jenesis now replaces - the plugin configuration, the wrapper and the CI
                    steps that called it. A CI job that built runs `java
                    -Djenesis.dependency.pin=strict build/jenesis/Make.java`; one that published
                    runs the `release` selector with -Djenesis.release.publish=true, which stages
                    first, with a jreleaser.properties naming the jreleaser.yml, and the version as
                    -Djenesis.project.version. A pom.xml build passes it too, to `release` and to
                    `pin`, which runs the release dry: JReleaser is handed the version the build
                    was given, never a POM's, and fails on a blank one unless jreleaser.yml names
                    it. Without that setting, `release` and every `pin` run
                    the release dry and publish nothing. The job's credentials become the JRELEASER_*
                    variables JReleaser reads (74). A job that deployed to a repository of its own rather than to
                    Maven Central names it in MAVEN_RELEASE_URI and its key in MAVEN_RELEASE_TOKEN
                    instead (74). Then let `ide` write the IntelliJ, VS Code or Eclipse
                    project so no IDE depends on the old build: one IDE module per module, its
                    tests in its test sources. The IDE files it writes into a source folder never
                    reach a jar. A Maven layout keeps its pom.xml files, which are now its build
                    declaration. A module-info.java build that deletes them loses the version they
                    held: keep it as version=<version> in project.properties at the root, which
                    -Djenesis.project.version overrides for a release, or what is staged is
                    unversioned and its POM 0-SNAPSHOT.

                    ## 7. Summarise the migration

                    Close every migration, a finished one or one stopped at phase one, with a
                    summary for the user:

                      the layout          the build declaration chosen, and why
                      the plugins         each plugin of the old build and what replaced it, or
                                          that nothing does and what was done instead
                      split packages      the packages that kept the build on pom.xml, and the
                                          offer of phase two
                      shading             every shaded dependency and the layer or packaging that
                                          replaced it, always, with the licensing, compliance and
                                          funding reason of step 4
                      differences         what still differs from the old build's output, as the
                                          comparison of step 5 found it
                      CI                  the command a CI job now runs, and what of the old build
                                          is left to retire

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "registry" -> """
                    # Jenesis - Find what replaces a Maven or Gradle plugin

                    One line per capability: the Maven plugin / the Gradle plugin or task -> what
                    does the same here (the demo that shows it). A file name is found in the
                    module's configuration locations as skill/tools describes, and its absence
                    leaves the tool off; `configuration` names the key that switches one off again.

                    ## Compile and generate

                      maven-compiler-plugin release / java toolchain release
                          -> maven.compiler.release in pom.xml, @jenesis.release (16)
                      maven-compiler-plugin testRelease, a test-compile execution's release
                          -> maven.compiler.testRelease in pom.xml, the test module's @jenesis.release
                      compilerArgs / options.compilerArgs -> process-javac.properties (13)
                      maven-compiler-plugin fork with -J options, meminitial, maxmem
                          -> -J<option>= in process-javac.properties, which runs javac in a JVM of
                          its own, as the build's JVM refuses a -J option
                      annotationProcessorPaths / annotationProcessor -> @jenesis.plugin (14, 43)
                      Error Prone / net.ltgt.errorprone
                          -> @jenesis.plugin javac <coordinate>, in a pom.xml
                          <!--jenesis.plugin javac maven/<groupId>/<artifactId>-->, with the javac
                          options it needs in process-javac.properties (15)
                      kotlin-maven-plugin / kotlin("jvm") -> .kt sources, found on their own and
                          compiled by the Kotlin plugin the build resolves for every module that
                          holds one (46, 48); a pom.xml build names src/main/kotlin as
                          jenesis.maven.sources=kotlin in jenesis.properties, as it does groovy;
                          the tests see the tested module's internal declarations, as the tested jar
                          is their friend path, so no -Xfriend-paths is written; kotlin-stdlib, which
                          kotlin("jvm") adds on its own, is declared, as kotlinc compiles against the
                          module's dependencies alone; a compiler plugin is
                          @jenesis.plugin kotlin:tool <coordinate>; a Kotlin Multiplatform library is named
                          by its JVM variant, <artifactId>-jvm, where its root artifact holds no class
                          and its POM does not depend on that variant, as Gradle module metadata is
                          not read; Gradle's incremental Kotlin compilation (kotlin.incremental, the
                          default) is jenesis.kotlin.incremental=true, which needs Kotlin 2.3 or newer;
                          the plugin's <args> and Gradle's freeCompilerArgs are lines of
                          process-kotlinc.properties, one option per line as
                          process-javac.properties holds javac's, as -Xjsr305=strict, a -J
                          line one of the compiler's JVM
                      scala-maven-plugin / scala -> .scala sources, compiled by the Scala plugin
                          the build resolves where a module holds one, with Scala 3 (50);
                          Scala 2 is not supported; the incremental compilation of Zinc, which sbt,
                          Gradle and the plugin's recompileMode=incremental use, is
                          jenesis.scala.incremental=true
                      gmavenplus / groovy -> .groovy sources, compiled by the Groovy plugin the
                          build resolves where a module holds one, with Groovy 4 or newer after javac, so Groovy uses Java types and Java no Groovy type:
                          gmavenplus stubs for a joint compilation have no counterpart, and Java code
                          that uses Groovy moves to a module that requires the Groovy one
                      jaxb2-maven-plugin / a JAXB plugin -> xjc.properties, binding the schemas
                          as @schemas/xjc=<folder> (17)
                      protobuf-maven-plugin / com.google.protobuf -> protoc.properties, binding
                          the definitions as @definitions/protoc=<folder> (17); protoc is a per-platform
                          executable, so each platform needs its own checksum pin
                      avro-maven-plugin / an Avro plugin -> avro.properties, binding the
                          schemas as @schemas/avro=<folder> (17)
                      jaxws-maven-plugin / a JAX-WS plugin -> wsimport.properties, binding the
                          descriptions as @descriptions/wsimport=<folder> (18)
                      openapi-generator-maven-plugin / org.openapi.generator -> openapi.properties,
                          binding the specification as @specification/openapi/openapi.yaml=<file> (18)
                      antlr4-maven-plugin / antlr -> antlr.properties, binding the grammars as
                          @grammars/antlr=<folder> (19)
                      com.google.devtools.ksp / ksp(...) dependencies -> ksp.properties (49), each ksp(...)
                          processor as @jenesis.plugin ksp:processor <coordinate> or a pom.xml
                          <!--jenesis.plugin ksp:processor maven/<groupId>/<artifactId>-->, and ksp { arg(...) }
                          as its lines
                      javacc-maven-plugin, JJTree / org.javacc.javacc -> no built-in; a plugin at
                          binary/generated that forks JavaCC, which the project resolves with
                          @jenesis.plugin javacc maven/<groupId>/<artifactId>, in a pom.xml
                          <!--jenesis.plugin javacc maven/<groupId>/<artifactId>-->, skill/extend
                      build-helper add-source / sourceSets
                          -> sourceDirectory in pom.xml, or a plugin at binary/generated
                      multi-release jar configuration -> sources/META-INF/versions/<N>/ (12), Java
                          and Kotlin alike: kotlinc compiles a .kt there for release <N> into the
                          jar's META-INF/versions/<N>/, as javac does a .java, and sees the module's
                          internal declarations (46). No second source root is read, so the sources
                          of a src/main/java<N> folder, a compiler execution with multiReleaseOutput
                          or a Gradle java<N> source set move below META-INF/versions/<N>/ of the
                          module's sources - src/main/java/META-INF/versions/<N>/ in a pom.xml
                      moditect add-module-info, a descriptor for a Java 8 base
                          -> module-info.java in sources/META-INF/versions/9/ (12)
                      replacer, templating, a generated version class
                          -> a plugin at binary/generated that writes the source (62)
                      maven-resources-plugin targetPath, a file outside the resource folders
                          -> -Djenesis.project.resources=<file>:<path in the jar>
                      animal-sniffer with a JDK signature -> maven.compiler.release, as javac
                          checks the API of the release it compiles for, unless the code calls a
                          newer API behind a runtime check, which the release refuses: then
                          --source=<release> in process-javac.properties, as below, and nothing
                          checks the API; an Android signature: none, as Android is a plugin's
                          concern
                      maven.compiler.source/target without a release, where the code needs the
                          API of the JDK compiling it, as a guarded call of a newer one does
                          -> --source=<release> in process-javac.properties, since the build
                          otherwise reads them as --release

                    ## Check and test

                      maven-checkstyle-plugin / checkstyle -> checkstyle.properties (37), whose
                          configuration=<path of checkstyle.xml, relative to it> names the
                          configuration, handed on with the files it names as ${config_loc}/<path>
                          beside it, and whose other lines are its propertyExpansion or Gradle's
                          configProperties; the build sets ${config_loc} itself; without
                          properties, a checkstyle.xml in build.jenesis/ is read as it stands;
                          a jar of custom checks among the plugin's dependencies -> @jenesis.plugin
                          checkstyle:tool maven/<groupId>/<artifactId>, in a pom.xml
                          <!--jenesis.plugin checkstyle:tool maven/<groupId>/<artifactId>-->
                      maven-pmd-plugin / pmd -> pmd.xml in build.jenesis/ (37), or a pmd.properties
                          with configuration=<path of pmd.xml, relative to it>;
                          minimumPriority /
                          rulesMinimumPriority -> jenesis.pmd.priority
                      spotbugs-maven-plugin / com.github.spotbugs -> spotbugs.properties, or
                          excludeFilterFile -> spotbugs-exclude.xml in build.jenesis/ (37), or
                          configuration=<its path, relative to it> in spotbugs.properties
                      forbiddenapis (de.thetaphi) check and testCheck / the same
                          -> forbiddenapis.properties (37), checking main and test classes alike,
                          which takes
                          bundledSignatures -> bundled=jdk-unsafe,jdk-deprecated,..., a JDK
                          version appended from the classes' release as targetVersion would;
                          signaturesFiles -> signatures=<files, relative to it>;
                          suppressAnnotations -> suppress; includes, excludes;
                          signaturesWithSeverityWarn -> warn; failOnMissingClasses=false ->
                          allowmissingclasses; ignoreSignaturesOfMissingClasses ->
                          ignoresignaturesofmissingclasses
                      maven-jdeps-plugin jdkinternals and test-jdkinternals
                          -> jdeps.properties (37), the JDK's jdeps --jdk-internals over main and
                          test classes; accept=<internal classes or packages used on purpose>;
                          failOnWarning, true by default there -> jenesis.strict.jdeps=true
                      maven-jdeprscan-plugin forRemoval, release
                          -> jdeprscan.properties (37), the JDK's jdeprscan --for-removal;
                          removal=false reports every deprecated API, release=<n> checks the
                          deprecations of that Java release. Both run the build JDK's tool, so a
                          build on a newer JDK (jenesis.toolchain.version) finds what that JDK
                          encapsulated or scheduled for removal
                      fmt-maven-plugin, Spotless googleJavaFormat() / the same
                          -> googlejavaformat.properties (37), which verifies and
                          fails the build on an unformatted source; -Djenesis.format.rewrite=true
                          rewrites. It takes style=aosp for googleJavaFormat().aosp(), javadoc=false for
                          formatJavadoc(false), reflow=false for skipReflowingLongStrings, which a
                          Spotless googleJavaFormat() without reflowLongStrings() needs as well, and
                          imports=remove, imports=sort or imports=none for skipSortingImports and
                          skipRemovingUnusedImports
                      Spotless palantirJavaFormat() / com.palantir.java-format, the same
                          -> palantir.properties, which takes reflow and imports alike
                      Spotless eclipse().configFile(...), formatter-maven-plugin (net.revelc)
                          configFile -> eclipse.properties with configuration=<the profile Eclipse
                          exports, relative to it>, or an eclipse-formatter.xml alone (37); Spotless's importOrder and the
                          formatter plugin's import and cache settings have no counterpart
                      rewrite-maven-plugin / org.openrewrite.rewrite -> rewrite.yml in
                          build.jenesis/ (37), or an openrewrite.properties with
                          configuration=<path of rewrite.yml, relative to it>, which runs
                          every recipe the file declares before the Java formatter over the
                          module's sources, with its dependencies as the class path;
                          activeRecipes / activeRecipe(...) -> recipes=<recipe>[,...] there;
                          rewrite:dryRun / rewriteDryRun is the default, which fails the build
                          printing the diff, rewrite:run / rewriteRun is
                          -Djenesis.format.rewrite=true; a recipe library -> @jenesis.plugin
                          openrewrite:tool <coordinate> in the module-info.java, and a module the
                          library and OpenRewrite ask two versions of -> the @jenesis.pin
                          openrewrite:tool/... line the build names
                      Spotless ktlint() / the same -> .editorconfig in build.jenesis/ (47), or a
                          ktlint.properties with configuration=<path of .editorconfig>
                      Spotless ktfmt() / the same -> ktfmt.properties (47): style=google for googleStyle(),
                          kotlinlang for kotlinlangStyle(), meta, the default, otherwise;
                          removeUnusedImports false -> imports=sort; maxWidth and the indents have
                          no counterpart, as ktfmt's command line takes none
                      Spotless scalafmt() / the same -> .scalafmt.conf in build.jenesis/ (51), or
                          a scalafmt.properties with configuration=<path of .scalafmt.conf>
                      Spotless licenseHeader(), licenseHeaderFile() / the same,
                          license-maven-plugin (com.mycila) check and format
                          -> licenseheader.properties (37), text=<the header>, which every Java,
                          Kotlin, Scala and Groovy source starts with as a block comment, or as
                          written where the text is a comment already, as Spotless's file is; the
                          header is matched and written exactly as it stands, so Spotless's $YEAR
                          and mycila's ${...} properties are written out, a year included; without
                          text, the header is // SPDX-FileCopyrightText: <copyright> and
                          // SPDX-License-Identifier: <id> of the one license the project declares
                      detekt, ktlint / the same -> detekt.yml and .editorconfig in build.jenesis/
                          (47), or detekt.properties and ktlint.properties naming them as
                          configuration=
                      scalastyle, scalafmt / the same -> scalastyle-config.xml and .scalafmt.conf in
                          build.jenesis/ (51), or scalastyle.properties and scalafmt.properties
                          naming them as configuration=
                      codenarc / codenarc -> codenarc.xml in build.jenesis/ (53), or a
                          codenarc.properties naming it as configuration=
                      maven-surefire-plugin / test -> on by default; test.properties names the
                          framework where inference does not (38), and only changed tests re-run (39)
                      surefire systemPropertyVariables of ${project.artifactId} or ${project.version}
                          -> read at run time from the tested jar's META-INF/maven/<groupId>/
                          <artifactId>/pom.properties or its Implementation-* manifest entries, or a
                          process-test.properties of the module's own
                      surefire rerunFailingTestsCount / org.gradle.test-retry -> none in the build:
                          a retry belongs to the test, as JUnit Pioneer's @RetryingTest,
                          rerunner-jupiter or TestNG's IRetryAnalyzer, declared as a test dependency
                      surefire forkCount with reuseForks=false / forkEvery -> one JVM per test
                          module: a test that needs a JVM of its own goes to a module of its own
                      maven-surefire-plugin dependenciesToScan / testClassesDirs += zipTree(<jar>)
                          -> scan=<module>|<groupId>/<artifactId> in the module's test.properties,
                          as no plugin block of a pom.xml is read, runs the test classes of those
                          dependencies of the tests among the module's own (40)
                      a second surefire <execution> with another argLine / an extra Test task
                          -> a sibling module that requires the first one's tests, by a
                          <type>test-jar</type> dependency in a pom.xml, and scans them, with
                          its argLine in a process-test.properties of its own; a run is a
                          module (40)
                      maven-failsafe-plugin / an integration test task
                          -> no phase of its own: -Djenesis.test.tag, or a @jenesis.test module
                      jacoco-maven-plugin / jacoco -> jacoco.properties, a report (41); the check
                          goal's bundle rules / jacocoTestCoverageVerification -> minimum.<counter>
                          (a covered ratio) and maximum.missed.<counter> in it,
                          which fail the build
                      pitest-maven / info.solidsoft.pitest -> pitest.properties (42)
                      jmh-maven-plugin / me.champeau.jmh -> @jenesis.plugin and @jenesis.main (43)
                      jcstress (a shaded jar run by org.openjdk.jcstress.Main) -> jcstress-core as a
                          dependency and again with <type>processor</type>, the mainClass property,
                          and no module-info.java, as jcstress runs from the class path (44)
                      byte-buddy-maven-plugin / net.bytebuddy.byte-buddy-gradle-plugin -> no built-in:
                          those plugins discover Byte Buddy plugins themselves and take their settings
                          as XML or DSL, which configures awkwardly; here a plugin of the project at
                          binary/transform constructs the net.bytebuddy.build.Plugin instances in Java,
                          with values from plugin-<name>.properties, and runs them with Plugin.Engine
                          over the module's compiled classes and its compile class path (64)
                      japicmp-maven-plugin / me.champeau.gradle.japicmp -> japicmp.properties (45),
                          which takes the plugin's parameters
                      revapi-maven-plugin / org.revapi.revapi-gradle-plugin -> revapi.properties
                          (45): oldArtifacts ->
                          baseline=<groupId>/<artifactId>[/<version>], the last release by default;
                          failSeverity -> severity=equivalent|non-breaking|potentially-breaking|
                          breaking, the least severe difference reported; analysisConfiguration or
                          analysisConfigurationFiles -> configuration=<a JSON file beside it>,
                          revapi's own configuration as an array of
                          extensions, as revapi.differences ignores; failing the build ->
                          jenesis.strict.revapi=true; a test module is never compared

                    ## Dependencies

                      dependency:tree / dependencies -> the `dependencies` selector
                      <exclusions> / exclude -> kept in pom.xml, or @jenesis.exclude (20)
                      an imported BOM / platform() -> kept in pom.xml, or @jenesis.bom (21)
                      a published BOM / java-platform -> a pom.xml of pom packaging with a
                          <dependencyManagement> and no modules, staged as that POM
                      moditect / an extra-java-module-info plugin
                          -> @jenesis.alias, modules.properties (22)
                      a second maven-compiler-plugin execution at another release, or
                          maven-jar-plugin <excludes> of compiled stubs -> not supported:
                          skill/migrate says how one release and a stub module replace it
                      a relocating shade / shadow -> not supported, nothing is relocated: a
                          layer, @jenesis.layer in module-info.java, keeps a private dependency
                          and its version apart at run time (25, 26); a fat jar is launcher=true;
                          skill/migrate says which answers which use of shading
                      versions locking, checksums / dependency locking and verification
                          -> `pin`, -Djenesis.dependency.pin=strict (30)
                      pgpverify-maven-plugin / verification-metadata.xml signatures
                          -> @jenesis.signature (31, 32)
                      maven-enforcer-plugin / dependency constraints -> -Djenesis.dependency.pin,
                          jenesis.resolver.maven=fail, jenesis.toolchain.version
                      cyclonedx-maven-plugin / org.cyclonedx.bom -> on by default (33); published
                          beside the jar with the cyclonedx classifier, as the plugins attach it,
                          and embedded in every jar, not only an application's, at
                          META-INF/sbom/<groupId>/<artifactId>.cdx.json (<module>.cdx.json without
                          a group), which the manifest's Sbom-Location names
                      license-maven-plugin (org.codehaus.mojo) / a license report plugin
                          -> licensing.properties (34)
                      dependency-check-maven / org.owasp.dependencycheck
                          -> vulnerability.properties, checked against OSV (35)

                    ## Package and ship

                      maven-jar-plugin / jar -> on by default; archives are reproducible unless
                          jenesis.archive.timestamp is emptied (36)
                      maven-artifact-plugin / artifact:compare -> -Djenesis.project.reproduce=<folder>
                          builds again into that folder without a cache and names the step whose
                          output differs, then prints a fingerprint of the whole build (36)
                      maven-jar-plugin manifestEntries / jar.manifest.attributes -> a
                          META-INF/MANIFEST.MF among the resources, the basis of the jar's manifest
                          (13); addDefaultImplementationEntries -> on by default: Implementation-Title
                          and -Vendor, and -Version where the POM, project.properties or
                          jenesis.project.version names one
                      maven-source-plugin / withSourcesJar() -> -Djenesis.project.sources=true (8, 74),
                          whose jar holds the sources and, as Maven's and Gradle's do, the resources,
                          including those of -Djenesis.project.resources; set it, and the next one,
                          in the profile a release activates, such as jenesis-release.properties,
                          never in jenesis.properties, so that a regular build neither archives the
                          sources nor renders the documentation and nothing waits for either
                      maven-javadoc-plugin / withJavadocJar(), Dokka
                          -> -Djenesis.project.documentation=true (8, 74), which documents
                          the main modules, a test module only under jenesis.stage.tests, one with a
                          module-info.java as that module (module-summary.html, its pages below the
                          module's name), as maven-javadoc-plugin and Gradle do; javadoc runs with
                          -Xdoclint:none unless process-javadoc.properties names an -Xdoclint flag,
                          and fails the build on an error, -Werror= making a warning one;
                          excludePackageNames -> -exclude=<package>[:<package>...] there, which
                          leaves those packages and their subpackages undocumented; the language
                          plugins document their sources beside it, the Kotlin plugin with Dokka
                      maven-jar-plugin <classifier> executions, build-helper attach-artifact of a
                          prepared file -> classifiers.properties, one <classifier>=<folder>[,...]
                          line per variant: the module's jar plus each folder's files, staged
                          beside the jar as <artifact>-<version>-<classifier>.jar and
                          <module>-<classifier>.jar; <classifier>= republishes the jar as it is.
                          A platform's variant goes into the classifiers.properties of a profile
                          folder, and a consumer selects one with @jenesis.pin <module>
                          :<classifier> or a pom.xml <classifier> (66)
                      maven-plugin-plugin / packaging maven-plugin -> <packaging>jar</packaging>,
                          as Maven reads a plugin from its descriptor alone, and a
                          mavenplugin.properties in the module's configuration folder (75), whose
                          descriptor Maven's own plugin tools write into the jar from the mojos and
                          their javadoc; goalPrefix -> goalprefix=<prefix>, maven-plugin-api and
                          maven-plugin-annotations in scope provided or by requires static
                      java-gradle-plugin gradlePlugin.plugins -> a META-INF/gradle-plugins/<id>.properties
                          naming implementation-class among the resources, the Gradle API in scope
                          provided or by requires static, and a gradleplugin.properties in the
                          project's configuration folder (76), which stages the marker the plugins
                          block resolves an ID by; com.gradle.plugin-publish, the Gradle Plugin
                          Portal's own publisher, has no counterpart
                      exec-maven-plugin or maven-antrun-plugin running make, cmake or cargo,
                          nar-maven-plugin, Gradle's cpp-library or an Exec task calling cargo
                          -> a plugin at binary/compiled that forks the native tool as an
                          EnvironmentalProcessBuildStep and writes its library below a folder of
                          its output, as library/linux-x86_64/, which classifiers.properties names
                          as linux-x86_64=library/linux-x86_64 (66); os-maven-plugin's
                          ${os.detected.classifier} -> one profile per platform, each building on
                          its own machine; the compiler is the machine's, as with Maven and Gradle.
                          javac -h, as maven-compiler-plugin's <headerOutputDirectory> or Gradle's
                          options.headerOutputDirectory -> the same plugin first wires
                          new Javac(ProcessHandler.Factory.of()).headers(true), which compiles the
                          module's sources and hands on nothing but headers/, one header per class
                          with a native method, for the C compiler's include path (67)
                      dokka-maven-plugin / org.jetbrains.dokka -> dokka.properties (46): moduleName
                          -> module-name (documentation by default), documentedVisibilities ->
                          documented-visibilities=public,internal,..., suppressObviousFunctions,
                          suppressInheritedMembers, skipEmptyPackages, skipDeprecated,
                          reportUndocumented and failOnWarning -> the same names in kebab case,
                          perPackageOption suppress -> suppressed-packages=<regex>[,...],
                          jdkVersion -> jdk-version, sourceLink -> source-link=<the URL of the
                          source folder> and source-link-suffix (#L by default),
                          externalDocumentationLink -> external-link.<name>=<url> and
                          external-link.<name>.package-list, dokkaJavadoc / the javadoc format ->
                          format=javadoc, the DokkaBase plugin's footerMessage -> footer-message; includes, customAssets and customStyleSheets have
                          no counterpart, an unknown key fails the build, and a Gradle source set or
                          a multi-module aggregate has none either: each module is documented alone
                      asciidoctor-maven-plugin process-asciidoc / org.asciidoctor.jvm.convert
                          -> asciidoctor.properties in the configuration folder (71), whose
                          documents=<folder, relative to it> - ../src/docs/asciidoc for their
                          default - is converted once for the whole project, staged as
                          target/stage/project/output/docs/; a file whose name starts with _ is a
                          partial, only included; attributes -> attribute.<name>=<value>, backend
                          -> backend=html5 or docbook5, outputDirectory -> output=<folder below the
                          staged project files>
                      maven-shade-plugin / shadow -> launcher=true, one executable jar (9)
                      Spring Boot repackage / bootJar -> launcher=true, one executable jar (9), or
                          bundle=true, its jars and the argument file that launches them (11)
                      maven-assembly-plugin / application, distZip -> bundle=true (11)
                      exec-maven-plugin / application, run -> java build/jenesis/Execute.java
                      maven-jlink-plugin / org.beryx.jlink -> jmod=true, jlink=true (10)
                      jpackage-maven-plugin / org.beryx.runtime -> jpackage=<type> (9, 10)
                      jib-maven-plugin / com.google.cloud.tools.jib
                          -> docker=<image>, a build context; the build never runs Docker (9, 10)
                      native-maven-plugin / org.graalvm.buildtools.native
                          -> native=true, graal.properties (57)
                      maven-jarsigner-plugin / jar signing -> jenesis.jarsigner.* (72)
                      maven-install-plugin / publishToMavenLocal -> `export` (73)
                      maven-deploy-plugin, distributionManagement to a repository of your own /
                          maven-publish to one -> `release` with jenesis.deploy.uri and
                          jenesis.deploy.token, which puts the staged Maven tree there
                          with its checksums and maven-metadata.xml, unsigned, a SNAPSHOT under
                          a unique timestamped name (74); altDeploymentRepository -> the same
                          setting on the command line
                      deploy to Maven Central, central-publishing, maven-gpg-plugin / maven-publish
                          to Central, signing
                          -> `release` with a jreleaser.yml in the configuration folder, or a
                          jreleaser.properties there naming it as configuration=<path, relative to
                          it>, which runs
                          JReleaser in the build's JVM in the project's root, with the
                          version the build was given as -Djenesis.project.version, which a
                          pom.xml build passes to `release` and `pin` alike, as no POM's version
                          reaches it, unless jreleaser.yml names one, and
                          the staged trees as template properties (74), the other values of
                          jreleaser.properties, and with --dry-run,
                          publishing nothing, unless the command line passes
                          -Djenesis.release.publish=true; JReleaser releases versions without
                          -SNAPSHOT, as the staged tree names a snapshot as Maven installs one,
                          without the timestamp its Maven deployers read, so a snapshot goes
                          through jenesis.deploy.uri, to Central's snapshot repository as to any
                          other
                      distributionManagement <relocation> / a relocated publication -> kept: a
                          module that relocates its artifact is staged as its POM alone, the
                          relocation in it, and fails the build if it holds sources (3)
                      maven.deploy.skip, maven.install.skip / a skipped publication task
                          -> stage=false in the module's packaging.properties, which keeps it out of
                          `export` and `release` alike
                      flatten-maven-plugin with flattenDependencyMode=all / a published POM of
                          the resolved closure -> none: a published POM names what the module
                          declares, at the versions resolved, so a consumer's own resolution decides
                          the rest; drop the plugin
                      maven-toolchains-plugin / java toolchains -> jenesis.toolchain.version (7)
                      a test run on another JDK, as Surefire's jvm or a Test task's javaLauncher
                          -> on a JDK of 25 or newer, the whole build again with
                          -Djenesis.toolchain.version=<feature>, as a CI matrix leg; the module's
                          release stays what it declares. An older JDK has no counterpart: Jenesis
                          runs on 25 or newer and refuses an older toolchain, so the tests run on
                          the build's JDK against the release the module compiles for
                      <profiles> chosen with -P or a property / properties and conventions
                          -> jenesis-<profile>.properties; those activated by <jdk> or
                          <activeByDefault> are read from the POM (8)
                      a Gradle build cache -> jenesis.cache.uri (58)

                    ## No built-in

                      resource filtering, ${...} in resources -> none; move the filtered files out of
                          the resource folder, which is copied whole, and write them from a plugin at
                          binary/compiled, as a file in both would fail the jar as a duplicate entry
                      quarkus-maven-plugin, a framework that transforms the application at build time
                          -> none; a plugin at the package hook point forks the framework's own
                          bootstrap over the jar, and the tests are handed the model its test
                          support reads; @QuarkusTest runs on the class path only
                      war, ear -> none; a war module is skipped with a [SKIPPED] line
                      maven-invoker-plugin, an integration-test project under src/it -> none;
                          one that needs no @...@ filtering becomes a module of the reactor,
                          with coordinates of its own, the others stay with the old build
                      spring-boot-devtools, kept out by repackage's excludeDevtools or Gradle's
                          developmentOnly -> none; declare it in a pom.xml profile activated by a
                          property, as <property><name>!noDevtools</name></property>, which Maven
                          activates by default and Jenesis does not read, so no bundle, launcher jar
                          or published POM carries it
                      apache-rat-plugin -> none; the release audit of one foundation's policy is
                          a plugin's
                      GWT, Android, Angular or Node front ends, Asciidoctor -> none; the tool keeps
                          to general-purpose functions, so a framework's or platform's build is a
                          plugin, published as a module and named in jenesis.plugins.properties
                      git-commit-id, buildnumber -> none; pass -Djenesis.project.revision,
                          project.tag and project.tree, which the POM and SBOM record and a
                          plugin reads as scm.revision, scm.tag and scm.tree of the
                          metadata.properties its hook point is handed
                      Spotless beyond formatting Java, Kotlin and Scala and a license header,
                          as misc, markdown or kotlinGradle -> none
                      maven-bundle-plugin, bnd / biz.aQute.bnd, an OSGi manifest -> none; a pom.xml
                          of packaging bundle builds a jar, whose headers a MANIFEST.MF among the
                          resources or a plugin at binary/transform supplies, which is handed the
                          compiled classes bnd analyses, as binary/compiled is not, and whose
                          manifest.mf is merged into the compiler's
                      gradle-module-metadata -> none; the POM is the published metadata
                      maven-release-plugin, axion-release -> none; -Djenesis.project.version,
                          then `release`
                      a version a Maven extension supplies (nisse, jgitver) -> none; set
                          jenesis.project.version, which the siblings' dependencies take as well
                      os-maven-plugin, os.detected.name/arch/classifier -> none; a profile per
                          platform in the pom.xml, activated by <os> with <family> and <arch>,
                          sets the three to the values the extension computes, as linux,
                          x86_64 and linux-x86_64 for <family>linux</family><arch>amd64</arch>,
                          so Maven, still running the extension, reads the same; a JVM property
                          or a jenesis.properties line never fills a pom.xml's ${...}
                      any other plugin -> write one: skill/extend

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "target" -> """
                    # Jenesis - Read target/

                    ## Read target/, never delete it

                    Outputs live under %{target}:

                      build/<step path>/output/      what a step produced (jars, *.properties)
                      build/<step path>/supplement/  argument files and intermediates
                      stage/<layout>/output/         the tree `stage` builds and `export` publishes

                    Never delete target/ and never pass -Djenesis.executor.rebuild=true for a clean
                    slate: each step is keyed by its inputs, so a build re-runs exactly what changed
                    and wiping only forces repeated work. A folder ending in `~` is a running step's
                    staging area, renamed into place on success. When the step fails, it stays with
                    what the step wrote and a .jenesis.failed marker beside its output/ and
                    supplement/, until the step runs again. A tool that fails prints the last 200
                    lines of its output and of its error (jenesis.process.tail), out of how many,
                    naming the file under supplement/ that holds all of them, which for a failed
                    test run names each failed test as the runner printed it. One build at a time
                    per target: the root holds an exclusive .jenesis.lock and a second process fails
                    fast.

                    Read the outcome of the latest build from %{target}/.jenesis.events.jsonl rather
                    than from the progress lines, whose [EVENTS] line names the file: one JSON object
                    per line, each led by its `status`. A step's is executed, skipped, failed, loaded
                    or stored beside its `step` path, a module's resolved or failed beside its
                    `module` path; a step and a module carry their `start` in seconds since the
                    build began, which places them on its timeline beside how many `seconds` they
                    took; a failure carries its `error` class and `message`, an executed or
                    skipped step the `folder` holding its output, and a failed step the `folder` ending
                    in `~` that holds what it wrote before it failed, such as a tool's reports and its
                    argument files. The first line is `started`, with the `target` folder and the
                    `directory` the build ran in, which a relative path in a step's command line,
                    such as the one a failure says to execute, resolves against. The last line is
                    `completed` or `failed`, with how many steps executed, skipped and failed; a file
                    without that last line is a build that is still running or was killed.

                    Every build is a `run`, named on the `started` line. An executed step is this
                    run's; a skipped step names the earlier `run` that produced its output, so its
                    output, and a test step's report under supplement/, came from that run and not
                    this one. The run that produced a step is in local.properties beside the step's
                    output/, with `cached=true` when it came from a build cache. That file never
                    reaches a checksum or a build cache.

                    ## A module builds in phases

                    Each module of a project builds in phases, each a module of its own beside the
                    others under compose/module and named <phase>-<module>: build (dependencies,
                    compilation, the jar, and the tests of a test module), verify (check, format,
                    compliance, the validators of binary/validate, and artifact), document and source
                    (the javadoc and sources jars, only with jenesis.project.documentation and
                    jenesis.project.sources) and package. A phase starts once what it reads is built:
                    a module's build waits for the builds of the modules it depends on and for nothing
                    else, so the checks of one module never hold up another, while the build still
                    fails once a check failed. -Djenesis.project.await=verify makes a build wait for
                    the checks of the modules it depends on as well. +<module> selects a module's
                    build.

                    ## Turn a folder into a selector

                    target/build/ mirrors the build graph, so any folder under it is a selector: drop
                    the `target/` prefix and any trailing `/output` or `/supplement`.

                      target/build/maven/compose/module/build-<m>/produce/assemble/binary/artifacts/output
                      -> build/maven/compose/module/build-<m>/produce/assemble/binary/artifacts

                    ## Read per-module state from the properties files

                    Each per-module step writes these into its output folder. Read them rather than
                    inventing a side channel; the schemas are constants on the writing step.

                      metadata.properties   project, artifact, version, name, description, url,
                                            inceptionYear, license.<id>.{name,url,distribution},
                                            developer.<id>.{name,email,id,url,organization,
                                            organizationUrl,roles,timezone}
                                            (id: the POM's <id>, the key when absent, none when empty;
                                            roles: comma-separated), organization.{name,url},
                                            copyright, manufacturer.{name,url}, publisher,
                                            scm.{connection,developerConnection,url,tag,revision,tree},
                                            issueManagement.{system,url}, ciManagement.{system,url}. Project-level
                                            overrides live in the file that
                                            -Djenesis.project.metadata=<path> names, or
                                            project.properties at the root when it names none;
                                            in a module-info.java build, a module's own
                                            META-INF/build.jenesis/project.properties wins over both;
                                            in a pom.xml build, what a module's own POM declares
                                            wins over that file, a list such as its licenses or
                                            developers as a whole, and the version, tag, revision
                                            and tree of the command line win over both.
                      module.properties     graph state: path, sources (below path, in a pom.xml build),
                                            module, test, main
                      identity.properties   <repository>/<coordinate> -> path or empty
                      requires.properties   <group>/<scope>/<repository>/<coordinate> -> empty, or
                                            <algo>/<hex> when pinned
                      versions.properties   <group>/<repository>/<coordinate> -> <version>[ <algo>/<hex>]
                      boms.properties       bom/<...> -> [<version>[ <algo>/<hex>]] references and
                                            entry/<...> -> expanded entries; merged below versions
                      signatures.properties <algo>/<hex>, or Sigstore/<host>/<path> for an identity,
                                            -> space-separated coordinate tokens, each optionally
                                            ending in /* for a whole groupId
                      exclusions.properties <group>/<scope>/<repository>/<coordinate> -> comma-separated
                                            <groupId>/<artifactId>
                      optionals.properties  <group>/<scope>/<repository>/<coordinate> -> empty, for a
                                            dependency the generated POM marks <optional>
                      inventory.properties  what staging reads: artifacts, sources, documentation,
                                            pom, runtime, prefixed
                      divergence.properties written by pin/divergence: <group>/<repository>/<coordinate>
                                            -> the versions it is pinned at and the modules holding
                                            each, for every coordinate pinned at more than one

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "selectors" -> """
                    # Jenesis - Address the graph

                      build stage export pin dependencies ide metadata configuration properties help skill prompt
                          Top-level entry points; ide[/idea|/vscode|/eclipse] drills into one tool,
                          skill/<page> prints one page of this briefing, and prompt/migrate the
                          task a user hands an agent to move a build here.
                      +<module>         module subgraph inside `build` (not stage/export/pin).
                                        <module> is the source folder holding its pom.xml or
                                        module-info.java; nested, foo/bar is written +foo+bar.
                                        In a pom.xml build a module's tests are a module of
                                        their own, which +<module> does not reach: name
                                        build/maven/compose/module/build-testmodule-<module>,
                                        or run the whole build with
                                        -Djenesis.test.filter=<module>/<regex>.
                      +<module>/<step>  one step in it, e.g. +foo+bar/compile/dependencies/resolved
                      pin/module-<path> one module's pins. `pin` is an entry point of its own and
                                        always rewrites every module, so `pin +foo` runs both and
                                        narrows nothing. <path> is the module's source folder with
                                        + for /, as in the folder under target/: foo/bar is
                                        pin/module-foo+bar.
                      :                 one path segment, e.g. build/:/java
                      ::                any depth, e.g. ::/test. Lenient: a typo matches nothing
                                        silently, so confirm a selector ran what you meant.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "tags" -> """
                    # Jenesis - Configure a module with @jenesis tags

                    Tags are read from the module's documentation comment in either form: the
                    traditional /** ... */ and the Markdown /// of JEP 467. `pin` writes back in
                    whichever form the comment already uses, and creates a /** ... */ when there is
                    no comment at all.

                    Token grammar, shared below: `<group>/<repo>/<coordinate>`, where a bare
                    `<module>` abbreviates `<group>/module/<module>`, `<groupId>/<artifactId>`
                    abbreviates `<group>/maven/<groupId>/<artifactId>`, and `maven/<coordinate>`
                    abbreviates `<group>/maven/<coordinate>` - the form a type or a classifier needs,
                    as a pin of a classifier artifact a POM brings in writes
                    maven/<groupId>/<artifactId>/jar/<classifier>, because a longer token without
                    the repository reads its groupId as a group, which is refused. A trailing `(<token>,...)`
                    guard applies a line only on a matching platform, with an unguarded line for the
                    same coordinate as fallback. Parentheses rather than brackets, because a
                    bracketed word is a link in a Markdown documentation comment and javadoc fails
                    on one it cannot resolve. alias, exclude and override are MODULAR_TO_MAVEN only.

                      @jenesis.release <V>   Java release target (default: the running JDK's, which a
                                             [RELEASE] line names); <V>-preview also
                                             enables its preview features, to compile and to run
                      @jenesis.main <class>  main class
                      @jenesis.plugin [<group>] <token>
                          Resolve a compiler plugin or a tool apart from this module's paths, in the
                          group named first: plugin, the default, is javac's annotation processor
                          path, javac its plugins such as Error Prone, and any other name a group a
                          plugin reads, named <group>:<purpose> after the plugin's group - its name
                          for an official plugin, as kotlin:tool and scala:tool the plugins of those
                          languages' compilers, and plugin.<name> for one jenesis.plugins.properties
                          names, as plugin.audit:tool.
                          A version is written into the token, as maven/<groupId>/<artifactId>/<ver>,
                          or pinned as <group>/maven/<groupId>/<artifactId>; else the newest release
                          resolves. A module of the project resolves from the build, in any group.
                          MAVEN modules declare the same lines in a <!--jenesis.plugin ...--> comment,
                          where a processor without a version takes <dependencyManagement>'s, and a
                          module of the project its own. One coordinate resolves in one group only.
                      @jenesis.test [<module>|abstract]
                          Test variant of <module>, one tag per module: a further module its tests
                          need is a requires. `abstract` supplies infrastructure only: declares
                          no tests, runs none, is staged only with the test modules.
                      @jenesis.pin <token> <ver> [<algo>/<hex>] [(<guard>)]
                          Pin a version and optionally a content checksum. A module name pins a
                          module that is required by that name; one a dependency's POM brings in is
                          pinned by <groupId>/<artifactId>. A version written :<classifier>[:<ver>]
                          selects the variant of the module published under that classifier, in
                          place of its jar on every path, in both modular layouts and for an aliased
                          name as well; a module of this build is selected as :<classifier> alone,
                          resolving to the variant its classifiers.properties declares, and fails
                          naming the profile or the file to set where it declares none.
                      @jenesis.signature <algo>/<hex> | Sigstore/<host>/<path> <token>... | [<group>/]signature-<name>.properties
                          Declare the OpenPGP key that signs these coordinates' artifacts - the
                          fingerprint first, because one key normally signs many. A Maven token may
                          end in /* to cover every artifact of one groupId. Carries no version: one
                          line covers every release that key signs, so it stays put across version
                          bumps. Nothing writes these lines: a key is checked against the upstream
                          project's published KEYS and added by hand, and a genuine key rotation is
                          accepted by adding the new fingerprint rather than deleting the old one.
                          Verification runs during dependency resolution, right after an artifact is
                          downloaded, and only when jenesis.dependency.signature asks for it:
                          declared verifies every coordinate a line covers, strict additionally
                          rejects one that no line covers or that publishes no signature, and the
                          default none verifies nothing, so a consumer who trusts the pins needs no
                          gpg at all. A coordinate's POM is verified with its artifact and must carry
                          the same signer, which closes the gap that POMs are read but never pinned.
                          The algorithm unsigned names no key, because there is none to name, and
                          its value says what to do when one turns up: unsigned/missing accepts a
                          coordinate that publishes no signature and fails once one appears, so an
                          upstream that starts signing is discovered rather than missed, and
                          unsigned/ignored never looks. Either lets strict hold everywhere else.
                          Key material comes from the local gpg keyring and is never fetched; an
                          unknown key is reported rather than retrieved. A lone
                          signature-<name>.properties token instead reads `<algo>/<hex>=<token>...`
                          lines from a local file in jenesis.project.signatures, the way
                          @jenesis.bom names a local pin-<name>.properties, so one vetted list
                          serves many modules; a list is never resolved from a repository, since one
                          that had to be downloaded would itself need verifying. A declaration never
                          switches verification on by itself, and when it is switched on it forks
                          gpgv, which must then be installed and on the PATH: a Java implementation
                          would have to be resolved from the repository being verified, and a
                          verifier downloaded on trust verifies nothing. gpgv reads a keyring file
                          and nothing else, so no home directory, agent or trust database takes
                          part; the build assembles that keyring from the declared fingerprints
                          alone, asking the repository registered under the algorithm a line
                          names: OpenPGP resolves through OpenPgpRepository, which asks the
                          jenesis.openpgp.uri servers for the fingerprint over HKP and holds what
                          it fetched in jenesis.openpgp.local. Those servers are asked in turn
                          because they speak one protocol; a key store that does not is registered
                          as its own repository rather than added to that list. A key the keyring holds is therefore a key a
                          line declares, and NO_PUBKEY means no line covers the signer. Registering
                          another repository under that name replaces where keys come from, so a
                          key store that is not an HTTP key server needs no change here. Fetching by fingerprint is not
                          trust in the server: the comparison is against the declared fingerprint,
                          so a server can withhold a key but never substitute one. The verifier is an ordinary
                          forked tool, so jenesis.print.gpgv shows each invocation,
                          jenesis.print.signatures names what was covered, and
                          jenesis.openpgp.command names a different binary.
                          Sigstore/<host>/<path> declares an identity rather than a key, for a
                          coordinate whose repository publishes a .sigstore.json beside the artifact:
                          nothing is fetched to check it, because the bundle carries the signing
                          certificate and the transparency log entry that records when it signed, and
                          the certificate is trusted for that recorded moment rather than for today -
                          it was valid for ten minutes and is long expired by the time anyone reads
                          it. The path is a prefix of the identity the certificate names, so a
                          declaration covers what lies below it and narrows a segment at a time:
                          Sigstore/github.com/acme covers every repository of an owner,
                          Sigstore/github.com/acme/lib one repository, and a workflow file may follow.
                          The ref a release was built from is never written, because that is the part
                          that moves, which is what lets one line cover every future release the way
                          a fingerprint does. The host names the issuer that must have authenticated
                          the identity, through jenesis.sigstore.issuers. What a bundle is checked
                          against is a sigstore-trusted-root.json read from jenesis.project.signatures
                          beside the key lists, and never resolved from a repository, for the reason a
                          key list is not: a trust root downloaded on trust verifies nothing.
                          Both forms may cover one coordinate, and each is then verified against
                          whatever it publishes; a coordinate covered only by an identity forks no
                          gpg at all, since the whole check is JDK cryptography in process.
                      @jenesis.alias <module> <groupId>/<artifactId>[/<type>]
                          Require a Maven artifact under a stable module name, so a non-modular jar
                          needs no derived automatic name. It names no classifier: a pin of the
                          artifact, @jenesis.pin <groupId>/<artifactId> :<classifier>[:<version>],
                          selects one. Carries no version: a pin or BOM entry
                          states it and is the place for a checksum; failing that the version the
                          closure already resolves is kept, and only a coordinate nothing else pulls
                          in is negotiated as LATEST. It also names the artifact a `requires` takes,
                          replacing the module index lookup, which is the way to pin down a name
                          several artifacts declare: alias it to the one artifact meant and no
                          other build of the same library can land instead. Aliasing a name the
                          target already declares is allowed and does exactly that, so an alias need
                          not be dropped when its target grows a module name. The jar's manifest
                          hands the alias to its consumers. An alias applies only where its target
                          is resolved, so one for an optional, a `requires static` or an excluded
                          target asks nothing of a module without it. MAVEN modules declare the
                          same lines in a project-level <!--jenesis.alias <module>
                          <groupId>/<artifactId>--> comment, a local parent's included, which names
                          the jar for a module-info.java the build compiles in META-INF/versions/<N>
                          in each module that resolves the target; one naming a dependency of test
                          scope reaches the tests alone.
                      @jenesis.exclude <module> <groupId>/<artifactId>...
                          Drop transitive dependencies of <module>, each with the subtree it pulled
                          in, from the compile path, runtime path and generated pom alike. Repeated
                          lines add up. Excluding from a module that is not required is an error.
                          It is scoped to the path through <module>, as in Maven, so a coordinate
                          reached by two paths needs an exclusion on each. A sibling project module
                          is one such path, and a consumer meets the coordinate again through the
                          sibling's POM and excludes it there as well.
                      @jenesis.override <module> <carrier>...
                          Replace a module with the modules already carrying its packages, for a
                          dependency that shades another module, as a server that bundles the API
                          it implements does. Jenesis substitutes an empty module requiring the carriers
                          transitively and drops every resolved artifact declaring the overridden
                          module, so the packages appear once. Reaches consumers through the
                          Jenesis-Overrides manifest header. A carrier nothing declares is an error.
                      @jenesis.layer <name> api <module> | <name> provider <token> | <name> native <token>
                          Keep a dependency private: resolve it, and its whole closure, into a run-time
                          ModuleLayer of its own rather than onto this module's path. Two versions of one
                          library then coexist with no package relocated - what shading is used for, without
                          rewriting a class file. `api` names the one module this module and the layer share;
                          `provider` names what the layer isolates, a module or a <repository>/<coordinate>
                          such as maven/<groupId>/<artifactId>, resolving it in the group
                          layer:<name>, which pins, verifies and reports like any other group; `native`
                          passes this module's native access on to a module of the layer, which this
                          module therefore needs itself. Every line names which kind it declares, so
                          none is the bare one.
                          The API module and everything it reaches are shared, so producer and consumer
                          exchange the very same classes and a service crosses as a plain interface call. A
                          dependency the API module reaches is exposed by it and cannot be isolated behind
                          it, which the build says rather than leaving to a LinkageError later.
                          The declaring module requires build.jenesis.launcher and asks for the layer by
                          name - Launcher.instance(MethodHandles.lookup(), "<name>", Contract.class) - so
                          its own consumers declare nothing and need not know. Discovery runs to a
                          fixpoint, so a module inside a layer may declare one of its own; each layer
                          is a child of its caller's, and a test JVM is handed jlayer.modulepath.<name>
                          like any deployment. That is a
                          jlayer.* key rather than a jenesis.* one: it configures no build, it is read by
                          the application a build produced. The JVM lets any code overwrite a system
                          property at any time, so code that runs before the layer is defined can place
                          its own jars in another module's layer; a layer inside an executable jar
                          (launcher=true) is read from the jar instead.
                          A layer splits a module path and a class path as the application does: what
                          carries a module identity - a module-info, an Automatic-Module-Name, or a name
                          given in modules.properties - is resolved, and the long tail a legacy library
                          drags is the layer's own class path, which only its automatic modules read. A
                          layer holding no module at all is refused. A layer's jars are stored among the
                          application's rather than apart, each dependency
                          named once with its version, so a jar both need is stored once and loaded twice. It may not require what it isolates: an isolated module
                          is off its path, which javac reports on its own. The declaration reaches consumers
                          through the Jenesis-Layer manifest header. bundle and launcher both ship a layer;
                          native=true rejects one, because a layer is defined while the JVM runs.
                      @jenesis.bom <token> [<ver> [<algo>/<hex>]] [(<guard>)]
                          Import managed versions. A bare <module> names a BOM properties file in the
                          module repository, floating latest without a version; <groupId>/<artifactId>
                          names a Maven BOM whose <dependencyManagement> is imported with nested
                          import-scoped BOMs flattened, and takes no checksum because pom bytes are
                          not stable across repositories; [<group>/]pin-<name>.properties reads a
                          local file from jenesis.project.boms. Local @jenesis.pin lines override BOM
                          entries, and the last declared BOM wins a conflict.
                          A key in that file is <module>, <groupId>/<artifactId>, or a full
                          <repository>/<coordinate>, which is the form a type or a classifier needs:
                          maven/<groupId>/<native-library>/jar/linux-x86_64, because
                          without the repository the groupId is read as one. An entry manages a
                          version wherever a closure reaches that coordinate, including modules that
                          never name it, so give every entry the checksum its version resolves to.
                      @jenesis.attach <token> [<arguments...>]
                          Attach a library as a -javaagent to this module's Execute run and its test
                          runs. The token carries no version: it comes from a declared dependency, a
                          pin or a BOM, else floats latest. Everything after the token is passed
                          verbatim as agent options. MAVEN modules declare the same lines in a
                          project-level <!--jenesis.attach ... --> comment, where a test-scoped match
                          attaches to test runs only and &#45;&#45; escapes a double dash.
                      @jenesis.native <token>...
                          Grant native access (--enable-native-access) to the modules named, this
                          one included only when it names itself, on this module's Execute run, its
                          test runs and what it packages; a grant adds no dependency. A grant is
                          never inherited: the names are recorded in the jar's Jenesis-Native-Access
                          manifest attribute, so a module that runs this one learns what to grant
                          itself. jenesis.dependency.native=warn reports a name the running module
                          does not grant, strict fails the build on it. A module in a layer is
                          granted by the module that declares the layer, with @jenesis.layer <name>
                          native, and never named here: what a granted module passes on to its own
                          layers is granted with it, so a module that runs a library with a layer
                          grants the library alone. MAVEN modules declare tokens in a
                          <!--jenesis.native ... --> comment.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "tools" -> """
                    # Jenesis - Activate a tool

                    A file in one of the module's configuration locations activates the feature; its
                    contents configure it. In the modular layouts that is the META-INF/build.jenesis/
                    folder of the module's sources, in the maven layout <module>/build.jenesis/ for both
                    halves of a pom, src/main/build.jenesis/ for its main code and
                    src/test/build.jenesis/ for its tests, and in every layout build.jenesis/ at the
                    project root, which reaches every module. For each file name the first location
                    that holds one configures the tool alone, and nothing merges: a folder named after
                    an active profile inside any location comes first, then the module's own
                    locations, then the root's. So a module's process-javac.properties replaces the
                    root's rather than adding to it, and repeats the lines of the root's it still
                    needs. The root has no location for the tests or the main code alone, since a
                    module's halves are configured where the module is: a file that differs for the
                    tests sits in each src/test/build.jenesis/, or in each test module's own folder.
                    Generators read their inputs from META-INF/build.jenesis/ in the
                    sources, which the compiler never copies into the artifact, unless folders=<paths>
                    names other folders, found among the sources and the resources alike; each reads
                    only the file kinds it compiles. In the maven layout only a resource directory
                    ships what such a folder holds.

                      packaging.properties      jmod/jlink/bundle/launcher/native booleans,
                                                jpackage=<type>[,<type>...] packaging and staging each,
                                                a deb with dpkg-deb alone, no fakeroot,
                                                docker=<image> with docker.label.<name>=<value> lines,
                                                docker.jpackage=app-image|deb|rpm to put that jpackage
                                                package into the image instead of the jars, built
                                                and staged only if jpackage lists it too,
                                                stage=false to build and test the module but keep
                                                its jar, POM, sources and documentation out of the
                                                staged repositories, so neither export nor release
                                                ships them
                      classifiers.properties    <classifier>=<folder>[,<folder>...] per line, each a
                                                variant of the module's jar holding its files and
                                                the files of the folders, named from the module's
                                                folder and kept inside the project, and from the
                                                output of the module's plugins, as a plugin at
                                                binary/compiled writes a native library, a META-INF/
                                                MANIFEST.MF among them merged over the jar's; a file
                                                the jar holds already is refused, <classifier>=
                                                republishes the jar as it is, tests, sources and
                                                javadoc are the build's own; staged beside the jar
                                                and selected by @jenesis.pin <module> :<classifier>
                                                or a pom.xml <classifier>; a platform's variants go
                                                into a profile's folder
                      test.properties           framework=junit-platform|junit4|testng, naming what
                                                this module's tests are written against; absent, it is
                                                inferred from the resolved dependencies;
                                                engines=<id>,-<id> runs the JUnit Platform on those
                                                engines alone, or without one, such as an engine the
                                                test resources register for a test of their own;
                                                scan=<module>|<groupId>/<artifactId>,... runs the test
                                                classes of those jars on the test path as well,
                                                chosen by the module's naming rules and filters
                      sbom.properties           CycloneDX format=json|xml|none; the SBOM is on by
                                                default, -Djenesis.sbom.cyclonedx=false disables it
                      bom.properties            publish the resolved closure as a repository BOM
                                                (Jenesis repository only)
                      licensing.properties      license check: allowed/denied/unknown/override.<coord>
                      vulnerability.properties  OSV check: severity, warn
                      graal.properties          native-image reachability agent during the test run
                      jdeps.properties          jdeps --jdk-internals over the compiled classes
                                                (accept)
                      jdeprscan.properties      jdeprscan --for-removal over the compiled classes
                                                (removal, release)
                      licenseheader.properties  text=<the license header every source starts
                                                with>, as a block comment, matched and written
                                                exactly as it stands; without text, SPDX lines
                                                naming the declared license and copyright
                      spdx.properties           extend the license alias/category tables
                      classpath.properties      make a module's jar serve the class path too: a
                                                META-INF/services file per provides clause, checking
                                                each provider can be created there, and
                                                Enable-Native-Access for a module granting itself
                      process-<tool>.properties extra arguments for a forked JDK tool (javac, javadoc,
                                                jar, jlink, jpackage, ...), one flag per key and its
                                                argument as the value, `-parameters=` for a bare one
                                                and `-Xlint\\:all=` where the flag holds a : or =,
                                                a line of the value per repetition of the flag and
                                                a tab between the arguments of one that takes
                                                several, `-linkoffline=<url>\\t<folder>`,
                                                but `-Dkey=value` for a system property,
                                                refusing one the module's declaration sets already,
                                                as javac's --release; a --source or --target of
                                                javac's, or an --add-exports, --add-reads or
                                                --patch-module of a JDK module, all of which javac
                                                refuses beside --release, has the release passed as
                                                --source and --target instead, where the file names
                                                neither, so the module is checked against the API of
                                                the JDK the build runs on rather than of its release,
                                                as Maven compiles a source and target without a
                                                release - a guarded call of a newer API compiles
                                                for an older release this way;
                                                process-test.properties targets the test JVM, merged
                                                over process-java.properties, which Execute, a
                                                bundle's and a Docker context's argument files
                                                carry as well, and an
                                                executable jar its --add-reads, --add-exports,
                                                --add-opens and --enable-native-access lines alone;
                                                a plugin reads only what its plugin-<name>.properties
                                                binds, the lines of its process-<tool>.properties
                                                being options of the JVM it runs in
                      environment-<tool>.properties
                                                variables for a program the build forks (java, test,
                                                pitest, native-image), which otherwise sees only
                                                PATH, HOME, LANG and the platform's own, and
                                                TERM=dumb, COLUMNS=80 and LINES=24, with its
                                                standard input closed; NAME=value sets one, a bare
                                                NAME passes on the build's own

                    In both files a value @<key> or @<key>/<default> is the setting
                    -Djenesis.variable.<key>, part of the key of every step it reaches. What a tool
                    prints is kept in supplement/ as UTF-8, and a forked JVM is told to print in it
                    (-Dstdout.encoding and -Dstderr.encoding) unless process-<tool>.properties names
                    another.

                    The linters of the sources - Checkstyle, PMD, detekt, ktlint, Scalastyle, scalafmt
                    and CodeNarc - and of the classes - SpotBugs and forbiddenapis - each activate on
                    a <tool>.properties in a configuration folder (checkstyle.properties,
                    pmd.properties, detekt.properties, ktlint.properties, scalastyle.properties,
                    scalafmt.properties, codenarc.properties, spotbugs.properties,
                    forbiddenapis.properties), which name the tool's own file as
                    configuration=<path, relative to them> - required, save for SpotBugs, whose
                    exclusion filter is optional. Where no such properties are found, the tool's one
                    conventional file in a configuration folder (checkstyle.xml, pmd.xml, detekt.yml,
                    .editorconfig, scalastyle-config.xml, .scalafmt.conf, codenarc.xml,
                    spotbugs-exclude.xml) activates it alone and is handed on as if named so. The
                    build resolves the tool's
                    plugin from jenesis-plugins on its own, as it does the JDK's
                    checks (jdeps.properties, jdeprscan.properties). A linter reports its findings;
                    jenesis.strict.<tool>=true fails the build on them instead, and
                    jenesis.check.<tool>=false skips the linter (jenesis.validate.<tool>=false for
                    SpotBugs, forbiddenapis and the JDK's checks). Whether a
                    [FINDINGS] line with their number and its report is printed is
                    jenesis.print.findings, true by default.
                    A linter that cannot load its configuration
                    fails the build either way. Checkstyle's ${config_loc} is the
                    folder its configuration is bound into, and every other ${<property>} it names is
                    a line of checkstyle.properties, or Checkstyle fails to load it. A jar of custom checks or filters joins Checkstyle's
                    class path when @jenesis.plugin checkstyle:tool maven/<groupId>/<artifactId> names
                    it, in a pom.xml <!--jenesis.plugin checkstyle:tool maven/<groupId>/<artifactId>-->,
                    which a local parent's POM hands to every module; it resolves in the checkstyle:tool
                    group, where @jenesis.pin checkstyle:tool/maven/<groupId>/<artifactId> <version>
                    pins it.

                    The formatters verify a module's sources and fail the build on a source they would
                    change; -Djenesis.format.rewrite=true lets them rewrite it instead. A
                    googlejavaformat.properties, palantir.properties or eclipse.properties switches
                    on that Java formatter, refused beside another, a ktlint.properties
                    ktlint, a ktfmt.properties ktfmt, a scalafmt.properties scalafmt, and a
                    licenseheader.properties the header every source starts with; without the
                    properties of ktlint or scalafmt, an .editorconfig or .scalafmt.conf of a
                    configuration folder activates it. An openrewrite.properties runs every OpenRewrite
                    recipe of the rewrite.yml it names as configuration=, or the recipes it names as
                    recipes=<recipe>[,...], and without one a rewrite.yml of a configuration folder
                    runs its recipes, before the Java
                    formatter and with the module's dependencies as its class path, printing the diff
                    it would apply; a recipe library joins OpenRewrite by @jenesis.plugin
                    openrewrite:tool maven/<groupId>/<artifactId>.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "settings" -> """
                    # Jenesis - Override a build with -Djenesis.* settings

                    Run `configuration` for every setting with the value in force, one per line and
                    built to grep:

                      java build/jenesis/Make.java configuration                    every setting
                      java build/jenesis/Make.java configuration | grep test        one area
                      java build/jenesis/Make.java configuration | grep -F "[set]"  what this
                                                                                    project changed

                    Each line reads `jenesis.<key>=<value> [set|default|unset] <what it does>`, so
                    the catalogue and the state of the build come out together. jenesis.properties at
                    the project root sets the same keys, written in full as `jenesis.test.tag=-slow`,
                    under your own
                    ~/.jenesis/jenesis.properties and a -D, and refuses the keys the catalogue marks
                    as the command line's or that file's; `properties` prints only the ones that
                    are set.

                    Two namespaces, split by who reads them. `jenesis.make.*` belongs to the entry
                    point: where the project is (root), which profiles to layer (profiles), where
                    the user-global file lives (global), and how the engine is compiled and reused
                    (compile, classes, daemon, aot). `jenesis.toolchain.*` is read there too, to pick the
                    JDK the build runs on. `jenesis.project.*` belongs to the build itself.
                    Only -Djenesis.make.root can say where the project is - a properties file cannot,
                    because the root is what locates that file. Likewise only -Djenesis.make.global
                    moves the user-global file, so a project never chooses your own settings.

                    Reach for these without looking them up:

                      -Djenesis.print.process          stream a tool's command line and output as it
                                                       runs - the first move when a step fails or
                                                       will not finish; narrow it with
                                                       print.<command>, as print.javac
                      -Djenesis.dependency.pin=strict  fail the build on any unpinned artifact
                      -Djenesis.test.filter=<regex>    run one test class or method; <module>/<regex>
                                                       runs it in that module alone
                      -Djenesis.process.concurrency=2  run at most two compilers, tools and test
                                                       JVMs at once rather than one per processor -
                                                       with many test modules on little memory,
                                                       beside an -Xmx in process-test.properties
                      -Djenesis.executor.aggregate     name every failed step at the end rather
                                                       than the first, as Maven's -fae summary
                                                       does; a step that depends on none of them
                                                       runs either way
                      -Djenesis.executor.rebuild       wipe target/ - avoid it, see skill/engine

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "execute" -> """
                    # Jenesis - Run what was built

                      java build/jenesis/Execute.java [-J<option>...] [args...]
                      jenesis-exec [-J<option>...] [args...]

                    Execute builds, finds the module declaring a main class and launches it on the
                    resolved runtime paths. Each leading -J<option> goes to the program's JVM, after
                    what process-java.properties gives it, and the first other argument starts the
                    program's own. Where more than one declares a main, name it with
                    -Djenesis.execute.module=<source folder> (nested: server/ui or server+ui) and
                    -Djenesis.execute.main=<fqcn>. Wrap the program alone in Docker with
                    -Djenesis.execute.docker=true, plus .docker.image, .docker.readable,
                    .docker.writable and .docker.env. Execute is its own entry point: build
                    selectors do not apply to it, nor its properties to a plain build.

                    ## When the JVM will not start

                    The JVM checks the module graph before any code runs, the program's and the
                    tests' alike, and names what it refused. Read its first line and its Caused by:

                      Module <m> not found, required by <n>
                                           nothing resolved holds <m>: a requires without a
                                           dependency, a scope that leaves it out at run time, or a
                                           jar that names no module, which @jenesis.alias names
                      Unable to derive module descriptor for <jar>, Caused by ... Invalid module name
                                           the name derived from the file is no Java name, as one
                                           holding abstract: name it with @jenesis.alias
                      Unable to derive module descriptor for <jar>, Caused by ... found in top-level
                      directory (unnamed package not allowed in module)
                                           the jar names a module but holds a class outside any
                                           package, so it can be no module at all: exclude it with
                                           @jenesis.exclude where nothing needs it at run time, ask
                                           its maintainers to drop the module name, or keep the
                                           code that uses it out of the module build
                      Package <p> in both module <a> and module <b>
                      Module <m> reads package <p> from both <a> and <b>
                                           a split package: exclude the jar that duplicates it with
                                           @jenesis.exclude, or replace a module whose packages
                                           others carry with @jenesis.override
                      ... cannot access class ... because module <a> does not export <p> to module <b>
                                           an internal package: --add-exports=<a>/<p>=<b> in
                                           process-java.properties, or process-test.properties
                      Unable to make ... accessible: module <a> does not "opens <p>" to module <b>
                                           reflection into a package: opens <p> in the
                                           module-info.java of <a> where it is yours, or
                                           --add-opens=<a>/<p>=<b> in the process file
                      ... has been compiled by a more recent version of the Java Runtime
                                           a class newer than the JVM: run on that release, with
                                           -Djenesis.toolchain.version=<n> where n is 25 or newer,
                                           or compile for this one
                      Preview features are not enabled for ...
                                           declare the release as <n>-preview, as
                                           @jenesis.release <n>-preview
                      ServiceConfigurationError: <service>: module <m> does not declare `uses`
                                           uses <service>; in the module-info.java of <m>
                      A restricted method ... has been called
                                           native access: @jenesis.native names the module

                    A jar the JVM refuses is no jar the build can repair: the fix is a tag, a process
                    file, or the dependency itself.

                    To run a build, or a published program, inside your own JVM rather than as a
                    process of its own, the same three commands are java.util.spi.ToolProvider
                    services - jenesis-make, jenesis-exec and jpx:

                      ToolProvider.findFirst("jenesis-make").orElseThrow()
                              .run(out, err, "-Djenesis.project.version=1.0.0", "build");

                    Settings come first, as -Djenesis.* arguments, and configure that one run rather
                    than the JVM, so two runs in one program never clash; everything after them is
                    what the command line would take. A setting that replaces the process a build
                    runs in - toolchain.version, project.docker, execute.docker - is refused by name
                    there, and so is a -J option, as the JDK's own tools refuse one; demo-81-tools-api
                    shows the whole contract.

                    Every command line here, the commands and the tools alike, reads @<file> as the
                    arguments that file holds - settings and selectors, # to the end of a line being
                    a comment and quotes holding what would otherwise split - and @@<text> as an
                    argument that starts with an @. A file is not expanded again from within a file,
                    which is how the JDK's own tools read one.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "pinning" -> """
                    # Jenesis - Pin dependencies

                    Pin external dependencies so every machine resolves the same artifacts, and offer
                    to pin whenever you add or change one. `pin` records resolved versions and
                    checksums back into pom.xml (<dependencyManagement> with <!--Checksum/<algo>/<hex>-->
                    and a <!--jenesis.pin ... --> comment) or module-info.java (@jenesis.pin tags),
                    idempotently, refreshing only the lines matching the local platform. An entry whose
                    version is a property keeps it, and its checksum names the version it was taken of
                    (<!--Checksum/SHA-256/<hex> 2.2.20-->), so it applies to no other: pin again after
                    the property changes. It covers the
                    whole project; to pin one module, name its step (pin/module-foo+bar) rather than
                    adding +<module>. A pom.xml's main and test halves share one <dependencyManagement>,
                    so where they resolve a coordinate at different versions its entry takes the
                    version the pom declares itself, which the other half then resolves as well.
                    -Djenesis.pin.file=<path> writes the closure of every module's main group to that
                    properties file instead of the declarations, in the grammar @jenesis.bom reads,
                    which is how a local bill of materials is refreshed rather than hand-edited. Only
                    a module-info.java reads it back, by @jenesis.bom
                    pin-<name>.properties: a tool's closure, as Checkstyle's with the jars
                    @jenesis.plugin checkstyle:tool adds, stays a @jenesis.pin <name>:tool/... line that `pin`
                    without the file writes, and a pom.xml build keeps every pin in its POMs, a
                    -SNAPSHOT from the local repository among them, whose checksum strict pinning
                    then holds until that snapshot is installed anew. Each
                    module resolves alone, so nothing makes them agree: pin/divergence reports every
                    coordinate the tree pins at more than one version, which is the signal that a
                    shared version table is overdue. Enforce
                    coverage with -Djenesis.dependency.pin=strict; refresh with
                    -Djenesis.dependency.pin=ignore and the `pin` selector. A checksum says the bytes
                    did not change since they were vetted, not who produced them; @jenesis.signature
                    declares the OpenPGP key that signs a coordinate, and -Djenesis.dependency.signature
                    checks the detached signature with a local gpg as each artifact is downloaded, so
                    the run that establishes a pin is the run that proves who produced it. The same
                    tag declares a Sigstore identity instead, as Sigstore/<host>/<path>, for a
                    coordinate that publishes a .sigstore.json: a certificate that named the workflow
                    which built it and lived ten minutes, trusted for the moment a transparency log
                    recorded rather than for today, checked in process against a
                    sigstore-trusted-root.json held beside the key lists.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "plugins" -> """
                    # Jenesis - Add to the stock build with plugins

                    The plugins of jenesis/jenesis-plugins need no line: the build resolves each by its
                    module name where its <name>.properties is found in a configuration folder -
                    checkstyle.properties, xjc.properties, jacoco.properties, asciidoctor.properties,
                    jreleaser.properties and the like - or, for a language, where a .kt, .scala or
                    .groovy source is. The properties name the tool's own file as
                    configuration=<path, relative to them>, and fail the build naming the key where
                    they leave it out; only where no properties are found does the tool's one
                    conventional file in a configuration folder - checkstyle.xml, pmd.xml, rewrite.yml,
                    jreleaser.yml and the like - switch it on alone, handed on as if named so.
                    Asciidoctor needs its properties, as they name the folder of documents. The
                    file's other values reach the plugin, so
                    plugin-<name>.properties is for no plugin of theirs. An official plugin is switched
                    off by jenesis.<part>.<name>=false, named after the part of the build it
                    belongs to (jenesis.check.pmd=false, jenesis.generate.xjc=false,
                    jenesis.language.kotlin=false, jenesis.release.jreleaser=false, ...), and a plugin
                    jenesis.plugins.properties names by jenesis.plugin.<name>=false, each a boolean,
                    true by default, so a plugin of the project's own shares no setting with an
                    official one, whatever it is named.

                    To add to the stock build, name plugins in jenesis.plugins.properties beside
                    jenesis.properties, one line each: <name>+<hook point>=<module name>, a coordinate
                    as =module/<module name> or =maven/<groupId>/<artifactId>, or =./<folder> for a
                    plugin compiled from source, where the hook point is a module of the build (check,
                    binary/generated, artifact, package, ...) or left out for the module build itself, and
                    =<module>@<provider> selects the provider annotated @BuildModuleName. A plugin
                    runs in a module only where plugin-<name>.properties is found in its configuration
                    locations; its BuildModuleFactory creates it from that file's values, where @<key> or
                    @<key>/<default> stands for jenesis.variable.<key> and @@ for a literal @. A key @<input>[/<target>]=<path> binds a file or folder of the
                    project, relative to the module, into an input the plugin reads as ../inputs/<input>,
                    placed at <target> inside it; one input takes a key per target, and @@<key> is the
                    value @<key>. A path may be @<key> as well; a folder must stay within the project,
                    while a file may lie outside it, so one configuration serves several. A plugin adds to its module and
                    replaces nothing but the compiled classes a binary/transform plugin rewrites; a build
                    that changes what the stock steps do is an entry point of its own. A plugin runs the
                    project's code, as its tests do, so build an untrusted project with
                    -Djenesis.project.docker=true.

                    Seven hook points run a plugin once for the whole project, each as plugin.<name>,
                    beside an official plugin of the same name. preprocess runs as
                    build/preprocess/custom/plugin.<name> before any module is built, sees only the inputs it
                    binds and hands the build nothing, so it can only stop it. postprocess/transform
                    and postprocess/inspect run over every module built, as
                    build/postprocess/transform/plugin.<name> and build/postprocess/inspect/plugin.<name>,
                    before anything is staged. release runs as release/custom/plugin.<name> beside the stock steps
                    of its goal, and is handed everything staged; export has no hook point, as it
                    only installs on this machine what was staged. A plugin of release runs only
                    where a configuration folder holds its plugin-<name>.properties, takes its values
                    from that file, and is handed root.properties, naming the project's root as
                    root and each staged tree as stage.<tree>, beside metadata.properties, the
                    project's metadata as the build resolved it, its version included. stage/transform runs after the stock staging, and
                    what it writes under a folder named after a staged tree joins that tree, so export
                    and release ship it; stage/inspect checks the result before either runs. plugin
                    runs as plugin/<name> only when that selector is named, depends on nothing and is
                    handed only what it binds. Switch an expensive one off with
                    -Djenesis.plugin.<name>=false or in a profile, and every plugin the file names
                    with -Djenesis.project.plugins=false. Any other such plugin
                    reads its values as <name>.<key> from jenesis.plugins.arguments.properties, and
                    from a jenesis.plugins.arguments-<profile>.properties per active profile, and pin
                    writes its pins to jenesis.plugins.pin.properties. A releaser reads
                    jenesis.release.publish and publishes nothing without it, and pin runs last, after
                    a dry release, so it pins a releaser's tool as plugin.<name>:tool/maven/<groupId>/<artifactId>
                    with the rest. A transform adds files to a
                    module by naming them in an inventory.properties of its own under any
                    <module>.<key>, such as <module>.attachment.<classifier> or <module>.report.<name>,
                    and what belongs to no module in a project/ folder that stage copies as it stands
                    into stage/project; an inspection fails the build by throwing and changes nothing
                    it was handed.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "extend" -> """
                    # Jenesis - Write a plugin for what nothing built in does

                    Write one only when skill/registry has no line for the job. A plugin adds steps
                    to the stock build and replaces none; skill/plugins wires it in. The smallest
                    whole example is demo-62-internal-module, the same plugin published is
                    demo-63-external-module, demo-64-byte-buddy rewrites compiled classes with
                    Byte Buddy, and demo-65-project-plugins uses every project-wide hook point.

                    ## The shape

                      plugin/module-info.java    module demo.plugin {
                                                     requires build.jenesis;
                                                     provides build.jenesis.BuildModuleFactory
                                                             with demo.plugin.ToolFactory;
                                                 }
                      plugin/.jenesis.skip       keeps the project from building plugin/ as a module
                      jenesis.plugins.properties tool+binary/generated=./plugin
                      build.jenesis/plugin-tool.properties
                                                 switches it on in a module, and carries its values

                    `requires build.jenesis` resolves a published build.jenesis, as any module the
                    plugin requires, pinned as plugin.<name>/module/build.jenesis: not the vendored
                    build/jenesis that runs the build, which reaches the plugin's own copy through a
                    bridge. Read the API of the version that pin names, from its sources jar, and pin
                    a newer one where the plugin needs what only that one has.

                    The provider implements BuildModuleFactory, whose create is handed the values of
                    plugin-<name>.properties, the build's Environment, the build's process permits, the
                    group plugin.<name> its own closure is pinned in, and its repositories and resolvers, and
                    returns a BuildExecutorModule. A value is resolved already, @<key> or
                    @<key>/<default> to jenesis.variable.<key>; a file the plugin reads is an input it
                    is handed, never a path among its values.
                    That module adds its steps in accept, with
                    executor.addStep("<name>", step, inherited.sequencedKeySet()) to be handed what
                    the hook point reads; it resolves a tool in <group>:tool and anything else in
                    <group>:<purpose> - a step resolving in a group of another name fails - through the
                    repositories and resolvers it was handed, and runs it within the permits it was
                    handed. A module offering several
                    factories marks each @BuildModuleName("<name>") and the plugins file picks one as
                    =<module>@<name>.
                    The layer a plugin is loaded into is kept for the next build in the same JVM,
                    the daemon's included, while its jars stay the same, so a factory keeps no static
                    state that must not outlive one build.

                    ## A step is a pure function of folders

                    Each argument is a predecessor's output folder: skip one whose removed() is true,
                    read the conventional folders - sources/, resources/, classes/, artifacts/, the
                    constants on BuildStep - and write below context.next() alone. Make the step a
                    record whose components are every value that should run it again: its serialized
                    form is its cache key, together with a digest of the plugin's own jars, so a
                    change to a value or to the plugin's code re-runs it and nothing else does. A
                    file it reads is bound rather than named: @<input>=<path> in
                    plugin-<name>.properties hands it over as the argument ../inputs/<input>, whose
                    checksum then decides whether the step runs.

                    The arguments are a SequencedMap<String, BuildStepArgument>, keyed by the
                    predecessor's path; argument.folder() is its output and argument.files() each
                    file's checksum. Beside the source folders, a module's hook point is handed its
                    metadata.properties (version, name, ...) and its module.properties, whose `test`
                    tells the test half of a pom from the main one, so a step that belongs to one of
                    them returns early on the other. No argument is named for either: find each
                    with BuildStep.locate over the arguments' folders, as BuildStep.locate(folders,
                    BuildStep.METADATA). The resolved dependencies arrive as a folder too:
                    Dependencies.select(folder, "main", "compile") lists the jars of one group and
                    scope, Dependencies.all(folder) every one. What a step at binary/compiled writes
                    as classes/ and a manifest.mf is merged into the module's jar beside what javac
                    compiled, which it never sees or replaces: a class both write fails the build.
                    A step at binary/transform is handed the module's compiled classes as its first
                    argument and the module's inputs after it; what it writes below classes/, or as a
                    manifest.mf, replaces the file of that name, every other one passes on as it was,
                    and several such plugins run in the order jenesis.plugins.properties names them,
                    each handed what the one before it wrote. A plugin that writes a manifest carries
                    over what still holds of the one it was handed, as Multi-Release while it keeps
                    the versioned classes, since only it knows what its transformation changed.

                    ## Pick the hook point by what the step produces

                      binary/generated       sources/ that the module compiles with its own, the
                                             place of a code generator; javadoc and the sources
                                             jar carry them too, and the resources/ it writes,
                                             as a service file, join the module's jar
                      check, format          a verdict on the sources, beside Checkstyle and the
                                             formatters, in the module's verify phase
                      compliance             a verdict on the resolved dependencies, in verify
                      language               a language: the file extensions it compiles, its
                                             compiler, placed before or after javac, and its
                                             documentation tool, from a LanguageFactory; it runs
                                             wherever its extensions are found, so it needs no
                                             plugin-<name>.properties but reads its values there
                      binary/compiled        a compiler of its own, beside javac
                      binary/transform       classes/ rewritten from what the compilers wrote, the
                                             place of a bytecode enhancer
                      binary/validate        a verdict on the compiled classes, beside jdeps and
                                             jdeprscan, in verify
                      artifact               a step over the jar, as japicmp and revapi, in verify
                      observed               a step over the tests' run, as PIT
                      observe                an engine attached to the tests' JVM and a report
                                             over what it recorded, from an ObservabilityFactory
                      test                   a framework the tests run on, from a
                                             TestFrameworkFactory, consulted before the built-in
                                             ones in the order the file names it
                      package                packages/, staged in stage/packages
                      documentation          beside javadoc, in the module's document phase
                      postprocess/transform  files for every module's inventory, or project/
                      stage/transform        files joining a staged tree
                      release                delivering what was staged
                      plugin                 a task that runs only when named, like an exec goal

                    A file the tests need, as a model a framework builds for them or a bundle they
                    install, is a resource of the test module: a plugin at the test module's
                    binary/compiled writes it below classes/, where only the tests' jar holds it, with
                    its plugin-<name>.properties in src/test/build.jenesis/ or the test module's own
                    configuration folder, and the tests read it with getResource. A framework that
                    wants a file path in a system property, as @QuarkusTest wants its application
                    model, is handed one by a LauncherSessionListener of the test module, declared as
                    a service of org.junit.platform.launcher.LauncherSessionListener: it copies the
                    resource to a temporary file and sets the property before the first test runs.
                    process-test.properties names no path below target/, which is the build's own.

                    A verdict is a throw: a step that finds a violation fails the build with a message
                    that names the file and the fix.

                    ## Run a tool

                    Prefer a tool's Java API to a process: require it in the plugin's module-info.java
                    and it resolves by module name into the plugin's own layer, pinned as
                    plugin.<name>/module/<module>. The plugin's module-info.java takes every tag a
                    project's does, and none of the project's own apply to it: a tool the module
                    index does not serve, as one named by an Automatic-Module-Name alone, is
                    required through @jenesis.alias <module> <groupId>/<artifactId>, with
                    @jenesis.exclude and @jenesis.pin as in a project. A plugin compiled from a folder,
                    or loaded under a modular layout, makes every jar of that layer a module of its own,
                    so a dependency without a module name, or two jars sharing a package, cannot load:
                    name it with @jenesis.alias, exclude it, or pin a version of it that names its
                    module; a published plugin its line names by its module otherwise loads such a
                    jar on a class path beneath its layer, which its automatic modules read. A plugin its
                    line names as maven/<groupId>/<artifactId> resolves its POM's closure instead,
                    pinned as plugin.<name>/maven/<groupId>/<artifactId>, and loads all of it on a
                    class path, as jpx runs a Maven coordinate, its factory found by its
                    META-INF/services entry; a modular layout refuses it. Configure the tool in that
                    code, too: where a Maven or Gradle plugin discovers the tool's own extensions and takes their settings as XML,
                    as Byte Buddy's build plugin does, the provider constructs them in Java and reads
                    only plain values from plugin-<name>.properties, as demo-64 does with Byte Buddy. A JDK tool is forked by extending ProcessBuildStep,
                    which also reads process-<tool>.properties; one that runs a program extends
                    EnvironmentalProcessBuildStep for environment-<tool>.properties. A tool's main
                    method, as java -cp <jars> <class or Launcher.java> <arguments>, runs through
                    ProcessHandler.OfProcess.ofJavaMain() in a JVM of its own, or, in the daemon or
                    under -Djenesis.process.mode=daemon, in one kept warm for the next call with the
                    same jars, wherever each module resolved them. Write nothing
                    outside context.next(): a step that must, as the export step does, overrides
                    shouldRun to say it always runs. context.next() is a temporary folder, renamed
                    when the step succeeds or filled from the cache, so never write its absolute
                    path into a file: write a path relative to the folder that reads it.

                    A tool that cannot be a module at all, as one with a class in the unnamed
                    package, is forked instead, and the project resolves it rather than the plugin:
                    @jenesis.plugin plugin.<name>:<purpose> maven/<groupId>/<artifactId> in its
                    module-info.java, or <!--jenesis.plugin plugin.<name>:<purpose> maven/<groupId>/<artifactId>-->
                    in its pom.xml, pinned as plugin.<name>:<purpose>/maven/<groupId>/<artifactId>, as the
                    plugin's group is plugin.<name>, apart from an official plugin of its name. A module's
                    hook point hands it over among the resolved dependencies, the input whose key ends in
                    /dependencies/artifacts, where Dependencies.select(folder, group + ":<purpose>", "plugin")
                    lists the jars for a java -cp. Hand a step only the inputs it reads, since each
                    is part of its key: a generator handed sources/ runs again on every edit. The
                    keys are relative paths, as ../../../../../../../dependencies/artifacts, so
                    filter inherited.sequencedKeySet() with endsWith.

                    ## Prove it

                    Build a demo project that uses the plugin and assert on what it wrote - a file in
                    the step's output under target/build/, or the failure it reports - then change
                    one value in plugin-<name>.properties and confirm the step runs again.

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "demos" -> """
                    # Jenesis - Copy a demo

                    81 demos under `demo/`, each self-contained, runnable and minimal, ordered so the
                    sequence doubles as a tutorial; `demo/README.md` indexes them. Find the one
                    matching the task and copy its shape rather than inventing configuration.

                    If this project vendors Jenesis as a git submodule, resolve the `build/jenesis`
                    symlink and the demos sit under the root it points into, conventionally
                    `build/.upstream/demo/`; a submodule the installer added checks out only
                    `sources/`, and `git -C build/.upstream sparse-checkout add demo` fetches them.
                    Otherwise read them at https://github.com/jenesis/jenesis/tree/main/demo.

                      Project shapes     01 java-pom, 02 java-modular, 03 java-pom-multi,
                                         04 java-modular-multi, 05 java-pom-model-4-1-0 (Maven 4's POM model)
                      Starting a build   06 startup (what launching costs, the daemon, the AOT cache),
                                         07 toolchain (the JDK the build runs on), 08 profiles
                      Runnable output    09, 10 java-*-executable (jpackage), 11 bundle (jars for a
                                         stock JRE)
                      Compiler control   12 java-multi-release,
                                         13 javac-arguments (process-javac.properties),
                                         14 annotations (an annotation processor via @jenesis.plugin),
                                         15 error-prone (a javac plugin),
                                         16 java-preview (@jenesis.release <V>-preview)
                      Generated sources  17 data-formats (xjc, protoc, avro),
                                         18 service-contracts (wsimport, OpenAPI),
                                         19 antlr (a grammar)
                      Dependencies       20 maven-exclusions, 21 bom, 22 module-alias,
                                         23 module-layout (forcing MODULAR), 24 module-override,
                                         25, 26 module-layers (a private dependency),
                                         27 class-path (a modular jar's services on the class path),
                                         28, 29 platform-guard (classified and per-platform variants)
                      Trusting them      30 pinning (versions and checksums), 31 openpgp (a declared
                                         key), 32 sigstore (a declared identity, no key at all),
                                         33 sbom, 34 compliance (licenses), 35 vulnerabilities (OSV),
                                         36 reproducible (a build compared with a second one,
                                         step by step, with a fingerprint over all of them)
                      Quality and tests  37 java-quality,
                                         38 test-framework (what the tests are written against),
                                         39 test-selection (incremental),
                                         40 test-runs (the tests of a scanned jar, run again
                                         with other flags by another module),
                                         41 code-coverage (JaCoCo, JFR, async-profiler),
                                         42 pitest (mutation),
                                         43 jmh (benchmark harness),
                                         44 jcstress (concurrency stress tests, from the class path),
                                         45 api-compatibility (japicmp, revapi)
                      Other languages    46 kotlin, 47 kotlin-quality, 48 kotlin-plugin,
                                         49 ksp (a Kotlin symbol processor), 50 scala,
                                         51 scala-quality, 52 groovy, 53 groovy-quality
                      At run time        54 agents (@jenesis.attach),
                                         55 native-access (@jenesis.native),
                                         56 native-access-layer (passed on to a layer),
                                         57 native-image (GraalVM)
                      Operating it       58 build-cache, 59 docker-isolation
                      Extending it       60 custom-assembler, 61 custom-jmod, 62 internal-module,
                                         63 external-module,
                                         64 byte-buddy (Byte Buddy's plugins over the compiled classes),
                                         65 project-plugins (hooks from a first check to release),
                                         66 classifiers (variants a module of the build publishes,
                                         selected by a sibling),
                                         67 jni (a C library a plugin compiles against the headers),
                                         68 custom-maven, 69 custom-modular,
                                         70 custom-build (no Project at all)
                      Shipping it        71 asciidoctor (AsciiDoc documents converted for the whole
                                         project and staged beside its modules),
                                         72 code-signing (jarsigner), 73 export (into the local repositories),
                                         74 publishing (Maven Central),
                                         75 maven-plugin (a Maven plugin and its descriptor),
                                         76 gradle-plugin (a Gradle plugin and its marker)
                      Resolving it       77 module-convention (resolving what you published),
                                         78, 79 discovery (a dependency resolved from its
                                         publisher's own domain, as a module or by coordinate)
                      Running it         80 jpx (run a released program without building),
                                         81 tools-api (a build inside another program's JVM)

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                case "engine" -> """
                    # Jenesis - Change the engine

                    ## Bump serialVersionUID after editing a build step

                    A step is keyed by the digest of its serialized form plus every predecessor's
                    checksums. Project sources are always detected, but editing a step's *code* does
                    not change its serialized form, so its output stays cached. Bump that class's
                    `serialVersionUID` to force it to re-run; prefer that over executor.rebuild.

                    ## When stuck, read the source

                    Every public type lives under `sources/build/jenesis/` and is short enough to read
                    end to end; `tests/` documents the public API by example.

                      https://jenesis.build/tool          documentation, including the full reference
                      https://github.com/jenesis/jenesis  source, issues and releases

                    The overview and the other pages: java build/jenesis/Make.java skill/start
                    """;
                default -> throw new IllegalStateException("No skill page " + page);
            }).replace("%{target}", target.toAbsolutePath().normalize().toString()));
        }
    }

    private record PinModule(Path root,
                             String fileName,
                             BiFunction<String, Path, BuildStep> stepFactory,
                             Path file,
                             SequencedSet<Path> provided,
                             HashDigestFunction hashFunction,
                             Consumer<String> printing,
                             Palette palette,
                             ProjectPlugins plugins)
            implements BuildExecutorModule {

        static PinModule ofEnvironment(Environment environment,
                                       Path root,
                                       String fileName,
                                       BiFunction<String, Path, BuildStep> stepFactory,
                                       HashDigestFunction hashFunction) {
            String file = environment.value("pin.file");
            List<String> entries = environment.entries("pin.provided");
            SequencedSet<Path> provided = new LinkedHashSet<>();
            if (entries != null) {
                for (String entry : entries) {
                    provided.add(root.resolve(entry).normalize());
                }
            }
            return new PinModule(root,
                                 fileName,
                                 stepFactory,
                                 file == null ? null : root.resolve(file).normalize(),
                                 provided,
                                 hashFunction,
                                 environment.flag("print.divergence") ? environment.out() : null,
                                 Palette.ofEnvironment(environment),
                                 new ProjectPlugins());
        }

        PinModule file(Path file) {
            return new PinModule(root, fileName, stepFactory, file, provided, hashFunction, printing, palette, plugins);
        }

        PinModule provided(SequencedSet<Path> provided) {
            return new PinModule(root, fileName, stepFactory, file, provided, hashFunction, printing, palette, plugins);
        }

        PinModule plugins(ProjectPlugins plugins) {
            return new PinModule(root, fileName, stepFactory, file, provided, hashFunction, printing, palette, plugins);
        }

        @Override
        public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) throws IOException {
            String prefix = BuildExecutorModule.PREVIOUS + RELEASE + "/";
            SequencedSet<String> built = new LinkedHashSet<>(), released = new LinkedHashSet<>();
            for (String key : inherited.sequencedKeySet()) {
                (key.startsWith(prefix) ? released : built).add(key);
            }
            SequencedSet<String> paths = new LinkedHashSet<>();
            for (Path folder : inherited.values()) {
                Path inventoryFile = folder.resolve(Inventory.INVENTORY);
                if (!Files.isRegularFile(inventoryFile)) {
                    continue;
                }
                SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
                for (String key : inventory.stringPropertyNames()) {
                    if (key.endsWith(".path")
                            && inventory.value(key.substring(0, key.length() - ".path".length()) + ".packaging") == null) {
                        paths.add(inventory.getProperty(key));
                    }
                }
            }
            if (file == null) {
                for (String path : paths) {
                    Path file = root.resolve(path).resolve(fileName);
                    if (!Files.isRegularFile(file)) {
                        continue;
                    }
                    buildExecutor.addStep("module-" + BuildExecutorModule.encodePath(path),
                            stepFactory.apply(path, file),
                            built);
                }
            } else {
                buildExecutor.addStep("file",
                        new Pins(paths, file, provided, hashFunction),
                        built);
            }
            buildExecutor.addStep("divergence",
                    new Divergence(paths, printing, palette),
                    built);
            if (!plugins.resolutions().isEmpty()) {
                SequencedSet<String> tooled = new LinkedHashSet<>(released);
                built.stream()
                        .filter(key -> Stream.of(ProjectPlugins.PREPROCESS, ProjectPlugins.POSTPROCESS)
                                .anyMatch(hook -> key.startsWith(BuildExecutorModule.PREVIOUS + BUILD + "/" + hook + "/")))
                        .forEach(tooled::add);
                buildExecutor.addModule("plugins", (pinned, given) -> {
                    pinned.addModule(ProjectPlugins.RESOLVED, plugins.resolution());
                    SequencedSet<String> inputs = new LinkedHashSet<>(List.of(ProjectPlugins.RESOLVED));
                    inputs.addAll(given.sequencedKeySet());
                    pinned.addStep("file", new PluginPins(plugins.pins(),
                            new LinkedHashSet<>(plugins.resolutions().sequencedKeySet()),
                            hashFunction), inputs);
                }, tooled);
            }
        }
    }

    private record PluginPins(Path file,
                              SequencedSet<String> plugins,
                              HashDigestFunction hashFunction) implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, String> pins = new TreeMap<>();
            Set<String> resolvedTools = new HashSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path resolved = argument.folder().resolve(BuildStep.DEPENDENCIES);
                if (!Files.isRegularFile(resolved)) {
                    continue;
                }
                SequencedProperties dependencies = SequencedProperties.ofFiles(resolved);
                for (String key : dependencies.stringPropertyNames()) {
                    String[] segments = key.split("/", 3);
                    int version = segments.length < 3 ? -1 : segments[2].lastIndexOf('/');
                    int colon = segments[0].indexOf(':');
                    String plugin = colon < 0 ? segments[0] : segments[0].substring(0, colon);
                    if (version <= 0 || !plugins.contains(plugin)) {
                        continue;
                    }
                    if (colon >= 0) {
                        resolvedTools.add(plugin);
                    }
                    String value = dependencies.getProperty(key).trim();
                    int space = value.indexOf(' ');
                    Path jar = argument.folder().resolve(space < 0 ? value : value.substring(0, space));
                    String checksum = Files.isRegularFile(jar)
                            ? hashFunction.encodedHash(jar)
                            : space < 0 ? null : value.substring(space + 1).trim();
                    pins.putIfAbsent(segments[0] + "/" + segments[2].substring(0, version),
                            segments[2].substring(version + 1) + (checksum == null ? "" : " " + checksum));
                }
            }
            if (Files.isRegularFile(file)) {
                SequencedProperties existing = SequencedProperties.ofFiles(file);
                for (String key : existing.stringPropertyNames()) {
                    String group = key.substring(0, Math.max(key.indexOf('/'), 0));
                    int colon = group.indexOf(':');
                    if (colon >= 0 && plugins.contains(group.substring(0, colon))
                            && !resolvedTools.contains(group.substring(0, colon))) {
                        pins.putIfAbsent(key, existing.getProperty(key));
                    }
                }
            }
            SequencedProperties written = new SequencedProperties();
            pins.forEach(written::setProperty);
            written.store(file);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record Pins(SequencedSet<String> paths,
                        Path file,
                        SequencedSet<Path> provided,
                        HashDigestFunction hashFunction)
            implements BuildStep {

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return true;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, String> entries = new TreeMap<>();
            for (String path : paths) {
                Set<String> internal = new LinkedHashSet<>();
                for (String identity : Inventory.identities(arguments.values())) {
                    internal.add(identity);
                    int first = identity.indexOf('/'), last = identity.lastIndexOf('/');
                    if (first > 0 && last > first) {
                        internal.add(identity.substring(0, last));
                    }
                }
                for (Map.Entry<String, Inventory.Dependency> dependency
                        : Inventory.closure(arguments.values(), path).entrySet()) {
                    String group = dependency.getValue().group();
                    String key = dependency.getKey().substring(group.length() + 1);
                    if (!group.equals("main") || internal.contains(key)) {
                        continue;
                    }
                    int lastSlash = key.lastIndexOf('/'), firstSlash = key.indexOf('/');
                    if (lastSlash <= 0 || lastSlash == firstSlash) {
                        continue;
                    }
                    String coordinate = key.substring(0, lastSlash);
                    String value = key.substring(lastSlash + 1);
                    Inventory.Dependency resolved = dependency.getValue();
                    String checksum = resolved.jar() != null && Files.isRegularFile(resolved.jar())
                            ? hashFunction.encodedHash(resolved.jar())
                            : resolved.checksum();
                    if (checksum != null && !checksum.isEmpty()) {
                        value += " " + checksum;
                    }
                    String entry;
                    if (coordinate.startsWith("module/")) {
                        String module = coordinate.substring("module/".length());
                        int dash = module.indexOf('-');
                        if (dash >= 0) {
                            value = ":" + module.substring(dash + 1) + ":" + value;
                            module = module.substring(0, dash);
                        }
                        entry = module;
                    } else {
                        String maven = coordinate.startsWith("maven/")
                                ? coordinate.substring("maven/".length())
                                : null;
                        entry = maven != null
                                && maven.indexOf('/') > 0
                                && maven.indexOf('/') == maven.lastIndexOf('/')
                                ? maven
                                : coordinate;
                    }
                    entries.putIfAbsent(entry, value);
                }
            }
            for (Path other : provided) {
                if (!Files.isRegularFile(other)) {
                    throw new IllegalStateException("No such file in jenesis.pin.provided: " + other);
                }
                SequencedProperties covered = SequencedProperties.ofFiles(other);
                for (String key : covered.stringPropertyNames()) {
                    String value = covered.getProperty(key).trim();
                    int space = value.indexOf(' ');
                    if (space < 0) {
                        continue;
                    }
                    if (value.equals(entries.get(key))) {
                        entries.remove(key);
                    }
                }
            }
            SequencedProperties pins = new SequencedProperties();
            entries.forEach(pins::setProperty);
            Path parent = file.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            pins.store(file);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private static final class Divergence implements BuildStep {

        private final SequencedSet<String> paths;
        private final transient Consumer<String> printing;
        private final transient Palette palette;

        private Divergence(SequencedSet<String> paths, Consumer<String> printing, Palette palette) {
            this.paths = paths;
            this.printing = printing;
            this.palette = palette;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            SequencedMap<String, SequencedMap<String, SequencedSet<String>>> versions = new TreeMap<>();
            for (String path : paths) {
                for (Map.Entry<String, Inventory.Dependency> entry
                        : Inventory.closure(arguments.values(), path).entrySet()) {
                    String key = entry.getKey();
                    int lastSlash = key.lastIndexOf('/');
                    if (lastSlash <= 0 || lastSlash == key.indexOf('/')) {
                        continue;
                    }
                    versions.computeIfAbsent(key.substring(0, lastSlash), _ -> new TreeMap<>())
                            .computeIfAbsent(key.substring(lastSlash + 1), _ -> new TreeSet<>())
                            .add(path.isEmpty() ? "." : path);
                }
            }
            SequencedProperties diverged = new SequencedProperties();
            versions.forEach((coordinate, byVersion) -> {
                if (byVersion.size() < 2) {
                    return;
                }
                List<String> rendered = new ArrayList<>();
                byVersion.forEach((version, modules) -> rendered.add(version + " (" + String.join(" ", modules) + ")"));
                diverged.setProperty(coordinate, String.join(", ", rendered));
                if (printing == null) {
                    return;
                }
                printing.accept("%s%-11s%s %s is pinned at %s".formatted(
                        palette.warning(),
                        "[DIVERGED]",
                        palette.reset(),
                        coordinate,
                        String.join(", ", rendered)));
            });
            diverged.store(context.next().resolve("divergence.properties"));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    private record PomAwareAssembler(MultiProjectAssembler<? super ProjectModuleDescriptor> base,
                                     String manifests,
                                     String prefix,
                                     boolean embed) implements MultiProjectAssembler<ProjectModuleDescriptor> {

        @Override
        public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers) throws IOException {
            ProjectModuleDescriptor nested = descriptor.toInherited();
            SequencedSet<String> synthetics = new LinkedHashSet<>(nested.synthetics());
            if (embed) {
                synthetics.add(BuildExecutorModule.PREVIOUS + "describe/pom");
            }
            SequencedSet<String> described = new LinkedHashSet<>();
            Stream.of(descriptor.sources(), descriptor.manifests(), descriptor.coordinates(), descriptor.artifacts())
                    .forEach(described::addAll);
            AssemblyDescriptor assembly = base.apply(nested.synthetics(synthetics), repositories, resolvers);
            return assembly.nest(assembly.build(), "assemble", delegate -> (sub, inherited) -> {
                sub.addModule("describe",
                        (describe, describeInherited) -> {
                            describe.addStep("pom",
                                    new Pom().embedded(embed),
                                    describeInherited.sequencedKeySet().stream());
                            if (manifests != null) {
                                describe.addStep("identity", new MavenIdentity(prefix, manifests), "pom", manifests);
                            }
                        },
                        inherited.sequencedKeySet().stream().filter(described::contains));
                sub.addModule("assemble", delegate, Stream.concat(inherited.sequencedKeySet().stream(),
                        embed ? Stream.of("describe/pom") : Stream.empty()));
            });
        }
    }

    private record AwaitingAssembler(MultiProjectAssembler<? super ProjectModuleDescriptor> base,
                                     List<String> phases) implements MultiProjectAssembler<ProjectModuleDescriptor> {

        @Override
        public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers) throws IOException {
            AssemblyDescriptor assembly = base.apply(descriptor, repositories, resolvers);
            if (phases == null) {
                return assembly;
            }
            AssemblyRequirement awaiting = AssemblyRequirement.of();
            for (String phase : phases) {
                awaiting = awaiting.upstream(phase);
            }
            return assembly.andThen(AssemblyDescriptor.BUILD, (_, _) -> { }, awaiting);
        }
    }

    private record BomAwareAssembler(MultiProjectAssembler<? super ProjectModuleDescriptor> base,
                                     HashDigestFunction hashFunction) implements MultiProjectAssembler<ProjectModuleDescriptor> {

        @Override
        public AssemblyDescriptor apply(ProjectModuleDescriptor descriptor,
                                        Map<String, Repository> repositories,
                                        Map<String, Resolver> resolvers) throws IOException {
            AssemblyDescriptor assembly = base.apply(descriptor, repositories, resolvers);
            if (BuildStep.locate(descriptor.configuration(), "bom.properties") == null) {
                return assembly;
            }
            return assembly.andThen(AssemblyDescriptor.BUILD,
                    (sub, inherited) -> sub.addStep("bom", new Bom(hashFunction), inherited.sequencedKeySet().stream()),
                    AssemblyRequirement.of());
        }
    }

    private record MavenIdentity(String prefix, String manifests) implements BuildStep {

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path pomFile = arguments.get("pom").folder().resolve("pom.xml");
            Path folder = arguments.get(manifests).folder();
            SequencedProperties metadata = SequencedProperties.ofFiles(folder.resolve(BuildStep.METADATA));
            String groupId = metadata.getProperty("project");
            String artifactId = metadata.getProperty("artifact");
            String version = metadata.getProperty("version", "0-SNAPSHOT");
            String module = SequencedProperties.ofFiles(folder.resolve(BuildStep.MODULE)).getProperty("module");
            String pom = context.next().relativize(pomFile).toString().replace(File.separatorChar, '/');
            SequencedProperties identity = new SequencedProperties();
            identity.setProperty("maven/" + groupId + "/" + artifactId + "/" + version, "");
            identity.setProperty("maven/" + groupId + "/" + artifactId + "/pom/" + version, pom);
            identity.setProperty(prefix + "/" + module + ":pom", pom);
            identity.store(context.next().resolve(BuildStep.IDENTITY));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
    }

    public Project(Path root) {
        this(root, new InferredMultiProjectAssembler(), Environment.NONE);
    }

    private Project(Path root, MultiProjectAssembler<? super ProjectModuleDescriptor> assembler, Environment environment) {
        Path resolved = resolvedRoot(root);
        SequencedSet<Path> configuration = Collections.unmodifiableSequencedSet(
                new LinkedHashSet<>(List.of(resolved.resolve("build.jenesis"))));
        this(resolved,
                resolved.resolve("target").normalize(),
                null,
                resolved.resolve(".jenesis").resolve("artifacts").normalize(),
                null,
                configuration,
                configuration,
                configuration,
                Collections.emptyNavigableSet(),
                null,
                new HashDigestFunction("SHA-256"),
                Layout.AUTO,
                true,
                false,
                false,
                null,
                null,
                null,
                null,
                null,
                Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(BUILD))),
                assembler,
                BuildExecutor.Configuration::new,
                Map.of(),
                Map.of(),
                new ProjectPlugins(),
                environment);
    }

    private static Path resolvedRoot(Path root) {
        if (!root.isAbsolute()) {
            return root;
        }
        Path absoluteCwd = Path.of("").toAbsolutePath().normalize();
        Path absoluteRoot = root.normalize();
        if (!absoluteRoot.startsWith(absoluteCwd)) {
            return root;
        }
        Path relative = absoluteCwd.relativize(absoluteRoot);
        return relative.toString().isEmpty() ? Path.of(".") : relative;
    }

    private static String required(Path properties, String key, String example) {
        String value;
        try {
            value = SequencedProperties.ofFiles(properties).value(key);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read " + properties, e);
        }
        if (value == null) {
            throw new IllegalArgumentException(properties + " names no " + key + " - add " + key + "=" + example
                    + ", relative to " + properties.getFileName());
        }
        return value;
    }

    public static Project ofEnvironment(Environment environment, Path root) {
        String layoutName = environment.getProperty("project.layout");
        Layout layout = layoutName == null ? null : switch (layoutName.toLowerCase(Locale.ROOT)) {
            case "auto" -> Layout.AUTO;
            case "maven" -> Layout.MAVEN;
            case "modular" -> Layout.MODULAR;
            case "modular_to_maven" -> Layout.MODULAR_TO_MAVEN;
            default -> throw new IllegalArgumentException(
                    "Unknown layout: " + layoutName + " (expected auto, maven, modular, or modular_to_maven)");
        };
        InferredMultiProjectAssembler assembler = InferredMultiProjectAssembler.ofEnvironment(environment);
        ProjectPlugins projectPlugins = new ProjectPlugins();
        Path file = root.resolve("jenesis.plugins.properties");
        if (Files.isRegularFile(file)) {
            SequencedMap<String, BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule>> plugins = new LinkedHashMap<>();
            SequencedMap<String, SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>>> hooks = new LinkedHashMap<>();
            for (String hook : List.of(ProjectPlugins.PREPROCESS,
                    ProjectPlugins.POSTPROCESS + "/" + ProjectPlugins.TRANSFORM,
                    ProjectPlugins.POSTPROCESS + "/" + ProjectPlugins.INSPECT,
                    STAGE + "/" + ProjectPlugins.TRANSFORM,
                    STAGE + "/" + ProjectPlugins.INSPECT,
                    RELEASE,
                    PLUGIN)) {
                hooks.put(hook, new LinkedHashMap<>());
            }
            SequencedMap<String, BuildExecutorModule> resolutions = new LinkedHashMap<>();
            SequencedSet<String> modulePlugins = new LinkedHashSet<>();
            SequencedProperties declared;
            try {
                declared = SequencedProperties.ofFiles(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + file, e);
            }
            boolean switchedOn = environment.flag("project.plugins", true);
            Map<String, Repository> pluginRepositories = ProjectPlugins.repositories(Map.of(), environment);
            Map<String, Resolver> pluginResolvers = ProjectPlugins.resolvers(Map.of(), environment);
            declared.forEachProperty((key, value) -> {
                String name = key.indexOf('+') == -1 ? key : key.substring(0, key.indexOf('+'));
                String hook = key.indexOf('+') == -1 ? "" : key.substring(key.indexOf('+') + 1);
                boolean projectWide = hooks.containsKey(hook);
                if (!projectWide) {
                    modulePlugins.add(name);
                }
                boolean enabled = switchedOn && environment.flag(ProjectPlugins.declared(name), true);
                if (!projectWide && !enabled) {
                    return;
                }
                String group = ProjectPlugins.declared(name);
                int at = value.lastIndexOf('@');
                String location = (at == -1 ? value : value.substring(0, at)).trim();
                String provider = at == -1 ? null : value.substring(at + 1).trim();
                if (location.isEmpty() || provider != null && provider.isEmpty()) {
                    throw new IllegalArgumentException("The plugin " + key + " in " + file + " names "
                            + (value.isBlank() ? "nothing" : value.trim())
                            + " - name the module that provides it, or its folder as ./<folder> for one compiled"
                            + " from source, followed by @<name> to select the provider annotated with that"
                            + " @BuildModuleName");
                }
                boolean folder = location.startsWith("./");
                int slash = location.indexOf('/');
                if (!folder && (slash == -1
                        ? !SourceVersion.isName(location)
                        : slash == location.length() - 1
                        || !pluginRepositories.containsKey(Resolver.base(location.substring(0, slash)))
                        || !pluginResolvers.containsKey(Resolver.base(location.substring(0, slash))))) {
                    throw new IllegalArgumentException("The plugin " + key + " in " + file + " names " + location
                            + ", which is neither a module name, nor a coordinate of a repository of "
                            + new TreeSet<>(pluginRepositories.keySet()) + ", nor a folder of the project - name the"
                            + " module that provides it, a coordinate as module/<name> or"
                            + " maven/<groupId>/<artifactId>, or its folder as ./<folder>");
                }
                if (layout == Layout.MODULAR
                        && slash != -1
                        && Resolver.base(location.substring(0, slash)).equals("maven")) {
                    throw new IllegalArgumentException("The plugin " + key + " in " + file + " names " + location
                            + ", a Maven coordinate, which loads on a class path, but the modular layout loads"
                            + " every plugin as modules - name the plugin by its module instead");
                }
                InternalModule internal = folder
                        ? InternalModule.ofEnvironment(environment,
                                        "module",
                                        group,
                                        Bind.contained(root, root.resolve(location), "The plugin " + key + " in " + file),
                                        pluginRepositories,
                                        pluginResolvers)
                                .buildModuleName(provider)
                        : null;
                ExternalModule external;
                if (folder) {
                    external = null;
                } else {
                    ExternalModule resolved = ExternalModule.ofEnvironment(environment,
                                    slash == -1 ? "module/" + location : location,
                                    group,
                                    pluginRepositories,
                                    pluginResolvers)
                            .buildModuleName(provider);
                    external = layout == Layout.MODULAR ? resolved.pathPlacement(PathPlacement.MODULE_PATH) : resolved;
                }
                BuildExecutorModule resolution = folder ? internal.resolution() : external.resolution();
                BiFunction<Path, SequencedMap<String, String>, BuildExecutorModule> plugin = (base, given) -> folder
                        ? internal.configured(base == null ? root : base, root, given)
                        : external.configured(base == null ? root : base, root, given);
                if (!projectWide) {
                    plugins.put(key, plugin);
                    return;
                }
                if (name.isEmpty() || name.contains("/") || name.contains(".") || resolutions.putIfAbsent(group, resolution) != null) {
                    throw new IllegalArgumentException("Cannot add the plugin " + key + " in " + file + " - a plugin"
                            + " of " + String.join(", ", hooks.sequencedKeySet()) + " takes a name of its"
                            + " own, holding no /, . or +");
                }
                if (enabled) {
                    hooks.get(hook).put(group, values -> plugin.apply(root, values));
                }
            });
            for (String name : modulePlugins) {
                if (resolutions.containsKey(ProjectPlugins.declared(name))) {
                    throw new IllegalArgumentException("The plugin " + name + " in " + file + " is named both for"
                            + " a hook point of a module and for " + String.join(", ", hooks.sequencedKeySet()) + " - a plugin"
                            + " of those takes a name of its own");
                }
            }
            assembler = assembler.plugins(plugins);
            projectPlugins = new ProjectPlugins(root.resolve("jenesis.plugins.pin.properties"),
                    root.resolve("jenesis.plugins.arguments.properties"),
                    hooks.get(ProjectPlugins.PREPROCESS),
                    hooks.get(ProjectPlugins.POSTPROCESS + "/" + ProjectPlugins.TRANSFORM),
                    hooks.get(ProjectPlugins.POSTPROCESS + "/" + ProjectPlugins.INSPECT),
                    hooks.get(STAGE + "/" + ProjectPlugins.TRANSFORM),
                    hooks.get(STAGE + "/" + ProjectPlugins.INSPECT),
                    hooks.get(RELEASE),
                    hooks.get(PLUGIN),
                    resolutions);
        }
        List<String> included = environment.entries("project.resources");
        if (included != null) {
            SequencedMap<Path, Path> resources = new LinkedHashMap<>();
            for (String entry : included) {
                int colon = entry.indexOf(':');
                String origin = "The resource " + entry + " of jenesis.project.resources";
                if (colon <= 0 || colon == entry.length() - 1) {
                    throw new IllegalArgumentException(origin + " does not name a file and where to place it - write"
                            + " <path>:<target>");
                }
                Path source = Bind.contained(root, root.resolve(entry.substring(0, colon).trim()), origin);
                if (!Files.exists(source)) {
                    throw new IllegalArgumentException(origin + " names " + source + ", which does not exist");
                }
                Path target = Path.of(entry.substring(colon + 1).trim()).normalize();
                if (target.getRoot() != null || target.startsWith("..") || target.toString().isEmpty()) {
                    throw new IllegalArgumentException(origin + " places its file at " + target + " - name a relative"
                            + " path among the resources, without ..");
                }
                if (resources.putIfAbsent(target, source) != null) {
                    throw new IllegalArgumentException(origin + " places its file at " + target + ", where another"
                            + " resource of jenesis.project.resources is placed already");
                }
            }
            assembler = assembler.resources(resources);
        }
        Project project = new Project(root, assembler, environment).plugins(projectPlugins);
        String configuration = environment.getProperty("project.configuration");
        if (configuration != null) {
            project = project.configuration(locations(environment, configuration, project).toArray(Path[]::new));
            project = project.boms(project.configuration().toArray(Path[]::new));
            project = project.signatures(project.configuration().toArray(Path[]::new));
        }
        ExternalModule releasing = ExternalModule.official(environment, ProjectPlugins.JRELEASER, null, Map.of(), Map.of());
        boolean releasable = environment.flag("release." + ProjectPlugins.JRELEASER, true);
        Path jreleaser = releasable
                ? BuildStep.locate(project.configuration(), ProjectPlugins.JRELEASER + ".properties")
                : null;
        Path jreleaserConfiguration = releasable && jreleaser == null
                ? ProjectPlugins.jreleaser(project.configuration())
                : null;
        if (jreleaser != null || jreleaserConfiguration != null) {
            if (jreleaser != null) {
                ExternalModule.named(jreleaser, ExternalModule.CONFIGURATION, required(jreleaser,
                        ExternalModule.CONFIGURATION,
                        "../jreleaser.yml, the JReleaser configuration"));
            }
            Path released = project.root();
            SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> releasers = new LinkedHashMap<>(project.plugins.releasers());
            releasers.put(ProjectPlugins.JRELEASER, values -> {
                SequencedMap<String, String> given = new LinkedHashMap<>(values);
                String named = given.remove(ExternalModule.CONFIGURATION);
                given.put("@" + ProjectPlugins.JRELEASER, (jreleaser == null
                        ? jreleaserConfiguration
                        : ExternalModule.named(jreleaser, ExternalModule.CONFIGURATION, named == null ? "" : named)).toString());
                return releasing.configured(released, released, given);
            });
            SequencedMap<String, BuildExecutorModule> resolutions = new LinkedHashMap<>(project.plugins.resolutions());
            resolutions.put(ProjectPlugins.JRELEASER, releasing.resolution());
            project = project.plugins(project.plugins.pins(project.root().resolve("jenesis.plugins.pin.properties"))
                    .arguments(project.root().resolve("jenesis.plugins.arguments.properties"))
                    .releasers(releasers)
                    .resolutions(resolutions));
        }
        ExternalModule documenting = ExternalModule.official(environment, ProjectPlugins.ASCIIDOCTOR, null, Map.of(), Map.of());
        Path asciidoctor = environment.flag("documentation." + ProjectPlugins.ASCIIDOCTOR, true)
                ? BuildStep.locate(project.configuration(), ProjectPlugins.ASCIIDOCTOR + ".properties")
                : null;
        if (asciidoctor != null) {
            SequencedMap<String, String> given = new LinkedHashMap<>();
            try {
                SequencedProperties.ofFiles(asciidoctor).forEachProperty(given::put);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + asciidoctor, e);
            }
            String documents = required(asciidoctor, ProjectPlugins.DOCUMENTS, "../docs, the folder of AsciiDoc documents");
            given.remove(ProjectPlugins.DOCUMENTS);
            given.put("@" + ProjectPlugins.DOCS, asciidoctor.resolveSibling(documents.trim()).normalize().toString());
            Path documented = project.root();
            SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> transforms = new LinkedHashMap<>(project.plugins.transforms());
            transforms.put(ProjectPlugins.ASCIIDOCTOR, values -> {
                if (!values.isEmpty()) {
                    throw new IllegalArgumentException("The plugin " + ProjectPlugins.ASCIIDOCTOR + " takes its values"
                            + " from " + asciidoctor + ", not from jenesis.plugins.arguments.properties - write "
                            + String.join(", ", values.sequencedKeySet()) + " there");
                }
                return documenting.configured(documented, documented, given);
            });
            SequencedMap<String, BuildExecutorModule> resolutions = new LinkedHashMap<>(project.plugins.resolutions());
            resolutions.put(ProjectPlugins.ASCIIDOCTOR, documenting.resolution());
            project = project.plugins(project.plugins.pins(project.root().resolve("jenesis.plugins.pin.properties"))
                    .arguments(project.root().resolve("jenesis.plugins.arguments.properties"))
                    .transforms(transforms)
                    .resolutions(resolutions));
        }
        ExternalModule marking = ExternalModule.official(environment, ProjectPlugins.GRADLE_PLUGIN, null, Map.of(), Map.of());
        Path gradlePlugin = environment.flag("artifact." + ProjectPlugins.GRADLE_PLUGIN, true)
                ? BuildStep.locate(project.configuration(), ProjectPlugins.GRADLE_PLUGIN + ".properties")
                : null;
        if (gradlePlugin != null) {
            SequencedMap<String, String> given = new LinkedHashMap<>();
            try {
                SequencedProperties.ofFiles(gradlePlugin).forEachProperty(given::put);
            } catch (IOException e) {
                throw new UncheckedIOException("Cannot read " + gradlePlugin, e);
            }
            Path marked = project.root();
            SequencedMap<String, Function<SequencedMap<String, String>, BuildExecutorModule>> supplements = new LinkedHashMap<>(project.plugins.supplements());
            supplements.put(ProjectPlugins.GRADLE_PLUGIN, values -> {
                if (!values.isEmpty()) {
                    throw new IllegalArgumentException("The plugin " + ProjectPlugins.GRADLE_PLUGIN + " takes its values"
                            + " from " + gradlePlugin + ", not from jenesis.plugins.arguments.properties - write "
                            + String.join(", ", values.sequencedKeySet()) + " there");
                }
                return marking.configured(marked, marked, given);
            });
            SequencedMap<String, BuildExecutorModule> resolutions = new LinkedHashMap<>(project.plugins.resolutions());
            resolutions.put(ProjectPlugins.GRADLE_PLUGIN, marking.resolution());
            project = project.plugins(project.plugins.pins(project.root().resolve("jenesis.plugins.pin.properties"))
                    .arguments(project.root().resolve("jenesis.plugins.arguments.properties"))
                    .supplements(supplements)
                    .resolutions(resolutions));
        }
        String boms = environment.getProperty("project.boms");
        if (boms != null) {
            project = project.boms(locations(environment, boms, project).toArray(Path[]::new));
        }
        String signatures = environment.getProperty("project.signatures");
        if (signatures != null) {
            project = project.signatures(locations(environment, signatures, project).toArray(Path[]::new));
        }
        String target = environment.getProperty("project.target");
        if (target != null) {
            project = project.target(project.root().resolve(target).normalize());
        }
        String reproduce = environment.getProperty("project.reproduce");
        if (reproduce != null) {
            project = project.reproduce(project.root().resolve(reproduce).normalize());
        }
        String artifacts = environment.getProperty("project.artifacts");
        if (artifacts != null) {
            project = project.artifacts(project.root().resolve(artifacts).normalize());
        }
        String cache = environment.getProperty("project.cache");
        if (cache != null) {
            if (cache.contains("://")) {
                throw new IllegalArgumentException("jenesis.project.cache is a filesystem path, not a URI"
                        + " (use jenesis.cache.uri for a URI): " + cache);
            }
            project = project.cache(new BuildExecutorFileCache(project.root().resolve(cache.isEmpty()
                    ? Path.of(".jenesis", "cache")
                    : Path.of(cache))));
        }
        if (layout != null) {
            project = project.layout(layout);
        }
        String metadata = environment.getProperty("project.metadata");
        if (metadata != null) {
            project = project.metadata(Arrays.stream(metadata.split(","))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(Path::of)
                    .toArray(Path[]::new));
        }
        String digest = environment.getProperty("project.digest");
        if (digest != null) {
            project = project.hashFunction(new HashDigestFunction(digest));
        }
        Boolean sources = environment.flagOrNull("project.sources");
        if (sources != null) {
            project = project.sources(sources);
        }
        Boolean documentation = environment.flagOrNull("project.documentation");
        if (documentation != null) {
            project = project.documentation(documentation);
        }
        String version = environment.getProperty("project.version");
        if (version != null) {
            try {
                ModuleDescriptor.Version.parse(version);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("jenesis.project.version is " + version + ", which is no module"
                        + " version, but javac stamps it into what it compiles as --module-version: a module"
                        + " version starts with a digit, as 1.2.0 or 1.2.0-3-gbd7698f do - set one of that form",
                        e);
            }
            project = project.version(version);
        }
        String tag = environment.getProperty("project.tag");
        if (tag != null) {
            project = project.tag(tag);
        }
        String revision = environment.getProperty("project.revision");
        if (revision != null) {
            project = project.revision(revision);
        }
        String tree = environment.getProperty("project.tree");
        if (tree != null) {
            project = project.tree(tree);
        }
        BuildExecutor.Configuration executor = BuildExecutor.Configuration.ofEnvironment(environment);
        return project.pinning(Pinning.ofEnvironment(environment)).configurator(() -> executor);
    }

    private static SequencedSet<Path> locations(Environment environment, String text, Project project) {
        SequencedSet<Path> target = new LinkedHashSet<>();
        locations(environment, text, project.root(), project.configuration(), new HashSet<>(), target);
        return target;
    }

    private static void locations(Environment environment,
                                  String text,
                                  Path root,
                                  SequencedSet<Path> defaults,
                                  Set<String> visited,
                                  SequencedSet<Path> target) {
        for (String entry : text.split(",")) {
            String candidate = entry.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            if (candidate.startsWith("@")) {
                String name = candidate.substring(1);
                if (name.isEmpty()) {
                    target.addAll(defaults);
                } else {
                    String value = environment.getProperty(name, System.getenv(name));
                    if (value == null) {
                        throw new IllegalStateException("Unresolved location reference: @" + name);
                    }
                    if (!visited.add(name)) {
                        throw new IllegalStateException("Circular location reference: @" + name);
                    }
                    locations(environment, value, root, defaults, visited, target);
                    visited.remove(name);
                }
            } else {
                target.add(root.resolve(Path.of(candidate)));
            }
        }
    }

    public Project root(Path root) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project configuration(Path... configuration) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                new LinkedHashSet<>(List.of(configuration)),
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project signatures(Path... signatures) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                new LinkedHashSet<>(List.of(signatures)),
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project boms(Path... boms) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                new LinkedHashSet<>(List.of(boms)),
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project profiles(Path... profiles) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                new LinkedHashSet<>(List.of(profiles)),
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project target(Path target) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project reproduce(Path reproduce) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project artifacts(Path artifacts) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project cache(BuildExecutorCache cache) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project hashFunction(HashDigestFunction hashFunction) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project layout(Layout layout) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project tests(boolean tests) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project sources(boolean sources) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project documentation(boolean documentation) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project pinning(Pinning pinning) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project metadata(Path... metadata) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                new LinkedHashSet<>(List.of(metadata)),
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project version(String version) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project tag(String tag) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project revision(String revision) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project tree(String tree) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project defaultTarget(String... defaultTarget) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                Collections.unmodifiableSequencedSet(new LinkedHashSet<>(List.of(defaultTarget))),
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project assembler(MultiProjectAssembler<? super ProjectModuleDescriptor> assembler) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project repositories(Map<String, Repository> repositories) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project resolvers(Map<String, Resolver> resolvers) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    private ProjectPlugins toolPlugins() {
        return plugins.tools(ProjectPlugins.repositories(repositories, environment),
                ProjectPlugins.resolvers(resolvers, environment));
    }

    public Project plugins(ProjectPlugins plugins) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public Project configurator(Supplier<BuildExecutor.Configuration> configurator) {
        return new Project(root,
                target,
                reproduce,
                artifacts,
                metadata,
                configuration,
                boms,
                signatures,
                profiles,
                cache,
                hashFunction,
                layout,
                tests,
                sources,
                documentation,
                pinning,
                version,
                tag,
                revision,
                tree,
                defaultTarget,
                assembler,
                configurator,
                repositories,
                resolvers,
                plugins,
                environment);
    }

    public SequencedMap<String, Path> build(String... selectors) throws IOException {
        if (reproduce == null) {
            return build(BuildExecutorCallback.nop(), selectors);
        }
        Path first = target.toAbsolutePath().normalize(), second = reproduce.toAbsolutePath().normalize();
        if (first.startsWith(second) || second.startsWith(first)) {
            throw new IllegalArgumentException("jenesis.project.reproduce names " + reproduce + ", which overlaps the"
                    + " target folder " + target + " - name a folder beside it, such as target-reproduced");
        }
        if (Files.exists(second)) {
            boolean empty;
            try (Stream<Path> files = Files.isDirectory(second) ? Files.list(second) : Stream.of(second)) {
                empty = files.findAny().isEmpty();
            }
            if (!empty) {
                throw new IllegalArgumentException("jenesis.project.reproduce names " + reproduce + ", which is not an"
                        + " empty folder - delete it or name an absent one, as the second build must run every step");
            }
        }
        Path marker = Files.createDirectories(reproduce).resolve(BuildExecutor.SKIP_MARKER);
        if (!Files.exists(marker)) {
            Files.createFile(marker);
        }
        SequencedSet<String> firstSteps = new ConcurrentSkipListSet<>(), secondSteps = new ConcurrentSkipListSet<>();
        SequencedMap<String, Path> built = build((identity, _) -> (_, throwable) -> {
            if (identity != null && throwable == null) {
                firstSteps.add(identity);
            }
        }, selectors);
        environment.out().accept("Building again into " + reproduce + ", without a build cache, to check that the"
                + " build reproduces");
        BuildExecutor.Configuration configuration = configurator.get().cache(null);
        target(reproduce).reproduce(null).cache(null).configurator(() -> configuration)
                .build((identity, _) -> (_, throwable) -> {
                    if (identity != null && throwable == null) {
                        secondSteps.add(identity);
                    }
                }, selectors);
        if (new Reproduction(target, firstSteps, reproduce, secondSteps).verify(environment.out(),
                environment.flag("print.reproduce", true))) {
            Files.walkFileTree(reproduce, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path directory, IOException exception) throws IOException {
                    if (exception != null) {
                        throw exception;
                    }
                    Files.delete(directory);
                    return FileVisitResult.CONTINUE;
                }
            });
        }
        return built;
    }

    private SequencedMap<String, Path> build(BuildExecutorCallback callback, String... selectors) throws IOException {
        BuildExecutor.Configuration configuration = configurator.get();
        if (cache != null) {
            BuildExecutorCache configured = configuration.cache();
            configuration = configuration.cache(configured == null
                    ? cache
                    : new BuildExecutorLayeredCache(cache, configured));
        }
        BuildExecutor executor = configuration.of(target, callback);
        Function<String, String> resolver = layout.apply(executor, this, assembler);
        try (Closeable _ = ProcessHandler.scope()) {
            return executor.execute(Arrays.stream(selectors.length == 0 ? defaultTarget.toArray(String[]::new) : selectors)
                    .map(selector -> selector.startsWith("+") ? resolver.apply(selector.substring(1)) : selector)
                    .toArray(String[]::new));
        }
    }

    private static Path skipped(Path folder) throws IOException {
        Files.createDirectories(folder);
        try {
            Files.createFile(folder.resolve(BuildExecutor.SKIP_MARKER));
        } catch (FileAlreadyExistsException _) {
        }
        return folder;
    }

    private void watch(String... selectors) throws IOException {
        Path absoluteRoot = root().toAbsolutePath().normalize();
        Set<Path> excluded = new LinkedHashSet<>();
        excluded.add(target().toAbsolutePath().normalize());
        if (artifacts() != null) {
            excluded.add(artifacts().toAbsolutePath().normalize());
        }
        new ProjectWatch(absoluteRoot, excluded, 200L, environment.out()).watch(() -> {
            try {
                build(selectors);
            } catch (Exception e) {
                environment.out().accept("Build failed: " + e);
            }
        });
    }

    private static final String CATALOGUE = """
                project.target|target|Folder the build writes its outputs to; a file a project provides names only a folder inside the project
                project.reproduce||Build the selection a second time into this folder, which must be absent or empty, consulting and filling no build cache, then compare what every step wrote in both builds, printing a fingerprint over every step's output: a clean comparison deletes the folder, while a difference keeps it, without failing the build, and names the steps that wrote a different output from the same input first, and the steps handed that output after them, as jenesis.print.reproduce asks; read by Make.java and jenesis-make, not by Execute.java; a file a project provides names only a folder inside the project
                project.await||Phases of the modules a module depends on that its build waits for, comma-separated, such as verify for their checks, so that a module builds only once the modules it depends on passed them; by default a build waits for their builds alone, and every phase runs as soon as what it reads is built
                project.artifacts||Folder resolved dependencies and repository metadata are cached in; a file a project provides names only a folder inside the project
                project.layout|auto|auto|maven|modular|modular_to_maven; auto reads the project, and modular also loads every jar of a plugin jenesis.plugins.properties names by its module as a module, where the others load a jar without a module name on the plugin's class path, and refuses one named by a Maven coordinate
                project.sources|false|Assemble a sources jar for every module
                project.documentation|false|Assemble a javadoc jar for every module, a test module only where jenesis.stage.tests stages it
                documentation.empty|false|Archive that javadoc jar holding nothing but an INTENTIONALLY_EMPTY file instead of rendering the documentation, for a repository such as Maven Central that requires the jar but not its content
                project.version||Version stamped onto every produced artifact, a module version that starts with a digit; unset leaves modules unversioned and POMs at 0-SNAPSHOT, and an empty value is refused
                project.tag||SCM tag recorded in the generated POM and SBOM; empty for none
                project.revision||Source revision, such as a commit id, recorded in the SBOM; empty for none
                project.tree||Git tree id of the release, recorded in the SBOM as a SWHID; empty for none
                project.digest|SHA-256|Algorithm for pin and dependency checksums
                dependency.signature|none|Signatures verified after download: none|declared|strict
                openpgp.command|gpgv|Binary forked to verify detached OpenPGP signatures; a name is looked up on the PATH, a path is used as given; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                openpgp.expiry|signing|An expired signing key: ignored accepts it, signing accepts what it signed before expiring, current rejects it
                sigstore.uri||URI of the Sigstore trust root; default: the published root of the public instance, carried as source; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                sigstore.issuers|github.com=token.actions.githubusercontent.com|Comma-separated <host>=<issuer> pairs, both named without a scheme, for identity hosts whose OpenID Connect issuer is not the host itself; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                project.metadata||Comma-separated metadata files; unset reads project.properties at the project root when it exists, empty reads none
                project.configuration|build.jenesis|Comma-separated folders searched for tool configuration files; @ splices the default, and @<name> splices what jenesis.<name> or the environment variable <name> holds
                project.boms||Comma-separated locations of local pin-<name>.properties; default: the configuration folders
                project.signatures||Comma-separated locations of local signature-<name>.properties; default: the configuration folders
                project.watch|false|Rebuild the selected target whenever a source file changes
                project.resources||Comma-separated <path>:<target> pairs of project files or folders placed among the resources of every module, as NOTICE:META-INF/NOTICE,LICENSE:META-INF/LICENSE
                project.plugins|true|false leaves out every plugin that jenesis.plugins.properties names, while pin still pins those of the whole project
                documentation.asciidoctor|true|Asciidoctor, activated by an asciidoctor.properties of the configuration folder naming the folder of documents as documents=, whose documents it converts and stages with the project's files
                project.cache||Project-local disk cache, layered in front of a remote; empty means .jenesis/cache; a file a project provides names only a folder inside the project
                project.docker|false|Run the whole build inside a container
                project.image||Image for that container
                project.readable||Extra read-only container mounts, host[:container],...
                project.writable||Extra writable container mounts
                project.env||Host environment variables to forward, name[=value],...
                make.root|.|Folder Make looks for the project in; only settable on the command line
                make.profiles||Comma-separated profiles layered over jenesis.properties
                make.global||Folder holding the user-global .jenesis/jenesis.properties; default: the home folder; only settable on the command line
                make.provided||Settings that the files a project provides supplied, comma-separated and named without the jenesis. prefix; derived by Make and settable in no file, so that a repository or cache URL a project named is never sent a credential
                make.mode|compiled|How the engine runs: source runs it from the build sources, compiled compiles them once and runs from those classes, aot runs those classes in a JVM that loads them from an ahead-of-time cache in aot.folder, trained on the first build that finds none and reused by every later one, and daemon hands the build to a reused JVM, which --stop as the only selector shuts down; aot applies where the engine runs compiled, as the installed CLI runs it, and in source mode caches only the tools, whose JVM is running before it could be handed a cache; aot and daemon set process.mode to the same unless it is set
                make.classes|.jenesis/classes|Where those classes land, relative to the root; a file a project provides names only a folder inside the project
                daemon.idle|10800|Seconds an idle daemon waits before exiting
                daemon.options|-Xmx2g|JVM options for the daemon process itself, whitespace separated; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                aot.folder|.jenesis/aot|The folder of every ahead-of-time cache, the engine's and the tools', relative to the project root; aot.properties in it records the engine and the JVM the folder was started for and when, and a build that finds either changed or the folder older than aot.lifetime clears it at once; a file the project provides names only a location inside the project
                aot.lifetime||ISO-8601 age after which the folder of caches is cleared and every cache in it trained again, as PT12H or P7D; empty keeps it until the engine or the JVM changes
                toolchain.version||JDK the build runs on, as 25, 25.0.3 or 25-temurin: the numbers match as a prefix, every word must be one of the vendor and version words in the JDK's release file, and a pre-release matches only when its word is named; Make and Execute relaunch on a match when the running JVM is none
                toolchain.searchpath|@|Comma-separated JDK folders searched for toolchain.version, absolute or under ~, * standing for any one folder name; @ splices this system's usual JDK locations and empty only checks the running JVM; settable only on the command line or in ~/.jenesis/jenesis.properties
                toolchain.installer||Program run with the requested version as its last argument when no JDK under toolchain.searchpath matches, such as jenesis-jdk from the SDK; a name is looked up on the absolute folders of the PATH, a path must be absolute or start with ~; settable only on the command line or in ~/.jenesis/jenesis.properties, and an empty value names none (env JENESIS_TOOLCHAIN_INSTALLER, which the jenesis command sets to its own jenesis-jdk for the run it starts where SDKMAN, mise or Scoop installed it)
                executor.concurrency|0|Run at most this many build steps at once; 0 is unbounded, while process.concurrency still bounds the tools they run
                executor.timeout|PT0S|ISO-8601 timeout per step; PT0S is no timeout
                executor.digest|MD5|Algorithm behind the content and step hashes that drive the cache
                executor.rebuild|false|Wipe target/ before building; prefer letting the cache decide
                executor.aggregate|false|Collect independent step failures into one report
                executor.events|true|Write each step's outcome of the latest build as one JSON object per line to .jenesis.events.jsonl in the target folder, replaced by every build
                process.concurrency|(processor count)|Run at most this many tool runs at once - a compiler, a JDK tool, a forked JVM such as a test run; 0 is unbounded
                process.factory|tool|tool|fork; fork runs a JDK tool in a process of its own
                process.mode|fork|How the main method of a tool that a plugin starts through ProcessHandler.OfProcess.ofJavaMain() runs, and javac where a -J option forks it: fork starts a JVM for every call that process.shared does not hand to a shared JVM, so that a build without the daemon leaves no JVM running; aot starts that JVM from an ahead-of-time cache of the tool in aot.folder, which its first run trains and every later one reads, one per JVM, options and jars, with the jars linked into it; daemon runs it in one JVM that runs its compilations side by side, as it runs the calls a plugin marks shared, kept for the next call with the same jars on its class path, wherever a module resolved them, and the same options and environment, idle ones ending after ten minutes; make.mode=aot and make.mode=daemon set it to the same unless it is set
                process.shared|true|Run the calls a plugin marks shared, a compiler above all, and javac where a -J option forks it, side by side in one JVM per JVM, options, jars and environment, ended when the build ends, under process.mode fork and aot as under daemon, which keeps it for the next build; false starts a JVM for every such call
                process.tail|200|Lines of a failed tool's output and of its error printed with the failure, the last ones, beside how many there were and the file under supplement/ that holds all of them; 0 prints every line
                legal.notices|META-INF/NOTICE,META-INF/LICENSE,META-INF/license/,META-INF/licenses/,LICENSE,about.html|Comma-separated jar entries taken as legal notices into a jmod, a linked or packaged image and beside a native image, from the module's jar at the root and from each runtime dependency's jar in a folder named after it; names match regardless of case and also with an extension, as META-INF/LICENSE.txt, and an entry ending in / takes the folder below it
                archive.timestamp|1980-02-01T00:00:00Z|ISO-8601 date-time with an offset recorded on every entry of the jars, jmods and zips the build writes; empty keeps the times the tools record and makes the archives unreproducible; set explicitly, it is also the creation time a generated Docker image is labelled with
                palette.colors|ansi|ansi colours what the build prints with ANSI escape sequences; none prints plain text
                print.progress|true|The build progress lines
                print.process|false|Stream each external tool's command line and output as it runs
                print.<command>||The same for one tool only, as print.javac or print.tests
                print.command|false|Each external tool command line, without its output
                print.findings|true|A line for each linter that found something, with the number of findings and where its report is
                print.checksum|false|Each step's input and output checksums
                print.fetch|false|Each artifact downloaded from a repository
                print.cache|false|Each step served from or written to the build cache
                print.signatures|false|Each verified dependency with its signer, and each one no declaration covers
                print.pins|false|Each pin a refresh kept although no closure resolved it
                print.divergence|false|Each coordinate the project pins at more than one version
                print.aliases|false|Each module alias whose target already declares that name
                print.docker|true|The image notice when a build or run is containerized
                print.reproduce|true|The steps that did not reproduce when jenesis.project.reproduce builds twice, beside the summary line
                dependency.pin||strict|versions|ignore; unset keeps existing pins and tolerates missing ones
                dependency.native|ignore|ignore|warn|strict: what to do when a module runs a jar whose Jenesis-Native-Access names a module it does not grant with @jenesis.native; warn reports it, strict fails the build
                resolver.maven|maven|maven|closest|latest|release|stable|fail|managed: which version a Maven coordinate resolves to; stable skips pre-release qualifiers, fail rejects a coordinate two dependencies require at different versions, managed rejects that and any version only a dependency's POM names
                resolver.module|first|first|ignore|fail|managed: what to do with the versions a module-info records; fail rejects two requires that record different versions, managed rejects that and any module only another module's requires names
                pin.file||Write the whole project's pins of the main group to this properties file instead of the module declarations, which a module-info.java imports with @jenesis.bom and a pom.xml never reads; a file a project provides names only a folder inside the project
                pin.provided||Comma-separated pin files whose entries this one leaves out, where the version and hash are the same
                pin.concurrency|(processor count)|Rewrite at most this many modules' pins at once; 0 is unbounded
                pin.checksum|true|Record content checksums in the pins that the pin selector writes
                pin.bom|keep|keep|flatten: whether pinning keeps BOM references or resolves them away
                pin.retain|groups|groups|all|none: which pins no closure resolved a refresh keeps - those of groups it did not resolve, all, or none
                platform.<token>||true adds a platform token and false removes one, selecting guarded pins
                variable.<key>||What a value @<key> or @<key>/<default> in a process-<tool>.properties, environment-<tool>.properties or plugin-<name>.properties stands for, and so part of the key of every step it reaches; @@ writes a literal @
                plugin.<name>|true|false leaves out the plugin <name> that jenesis.plugins.properties names, which resolves and runs as plugin.<name> beside an official plugin of its name; an official plugin is switched by the part of the build it belongs to instead, as listed below
                repository.insecure|false|Allow plaintext http:// repositories, and over https accept a certificate that does not verify, as a self-signed one; only the command line or ~/.jenesis/jenesis.properties may allow it, never a file a project provides
                repository.retries|2|Retries after a failed fetch; 0 disables
                repository.backoff|125|Initial retry backoff in milliseconds, doubling per attempt
                repository.connect|10000|Connect timeout for a repository fetch, in milliseconds
                repository.read|30000|Read timeout for a repository fetch, in milliseconds
                repository.offline|false|Download nothing: every module, artifact, checksum and key comes from the pinned artifacts, the local Maven repository or the local module folder, Maven metadata an earlier resolution stored beside the pinned artifacts answers a version range at the versions it named then, a fetch that would need the network fails the build naming what was missing, no domain is asked for its discovery file, whose answer an earlier build kept is read whatever its age, a locally cached Maven file is trusted without its remote checksum, the remote build cache at jenesis.cache.uri is skipped, and a vulnerability lookup or a release fails the build
                repository.discovery|false|Ask the domain a module or Maven group is named after, before the module remotes and before the Maven remotes, how it is published: for net.bytebuddy.agent, as a module or a group, the file at https://<domain>/.well-known/java-repository.properties for bytebuddy.net and, only where it publishes none, for agent.bytebuddy.net, a java.util.Properties file in UTF-8; the first file found speaks for every name below its domain, so a key it does not hold is absent rather than asked of a subdomain, unless it says delegate=true, which lets the files of its subdomains answer first and keeps its own entries for a key none of them holds. A module is answered by module=<location>, where its files are, and by moduletomaven=<groupId>:<artifactId>[:<extension>[:<classifier>]], the Maven artifact it is published as, resolved through the Maven remotes and maven= entries; the modular layout asks module= first and the others moduletomaven= first, a key that does not answer leaving the request to the other; a group is answered by maven=<location>. A coordinate may name the module as {module} and the labels of its name below the file's domain, joined by dashes after a leading one, as {-suffix}, and without either applies only to the module whose own domain holds it. A key may name what it is for as <kind>[<name>], an artifact ID for maven= and a module name for the others, or as <kind>[<prefix>*], the exact name being chosen over the longest prefix and either over the key for all, and the keys beside it then follow its selector, as maven[<name>].latest; a coordinate a selected key names applies to that module wherever it sits below the domain. <kind>.since=<version> serves only that version and later ones, ordered as Maven orders versions, and <kind>.suffixes=<suffix>[,<suffix>...] only a version whose qualifier after its first dash starts with one of those words, ignoring case, none naming a version without one; a version an entry does not admit is left to the remotes, as is a request naming no version where the entry restricts versions. A location without placeholders is a root, a Jenesis module service for module= and a Maven repository for maven=, whose Maven metadata, limited to the versions the entry admits, is merged with that of the remotes; one with placeholders is a template naming each file, {module} and {-suffix} for module=, {groupId}, {groupPath} and {artifactId} for maven=, and {version}, {-classifier} and {type} for both, where a template without {-classifier} or {type} serves only the plain jar, a file is validated against the .sha512, .sha256 or .sha1 beside it, and no Maven metadata is read, so only a named version resolves, unless <kind>.latest=<link> beside a template naming {version} names a link to the newest version: a request naming no version sends it a HEAD request, follows no redirect, and reads the version from a Jenesis-ModuleVersion or Jenesis-MavenVersion header or else from where the link redirects, matched against the template up to the end of the path segment holding {version}, as GitHub's releases/latest/download/<name> redirects to the newest release, and a maven= template then answers Maven metadata naming that version; a link ending in /maven-metadata.xml is read as that file instead, its release among the versions the key serves being the newest, and a maven= template answers its versions, limited to those it serves, as its metadata. sources=<template> names the archive of the sources of a version, its {version} filled in beside the placeholders of maven= and module=, which the SBOM lists as the source-distribution reference of each dependency the file answers for. Of a key named twice the last one counts, as java.util.Properties reads it; a placeholder a key does not know, a module= that is no location, a moduletomaven= that is no coordinate, a location not read over https or a module jar that does not declare the name it was asked for in its module-info or as its Automatic-Module-Name fails the build, an unknown key is ignored, and a file that cannot be fetched counts as absent, a TLS handshake that is reset, refused by an alert or spoken in another protocol included, save where the certificate it is served with does not verify or does not name its host, which fails the build. What a domain answered is kept for jenesis.discovery.ttl hours
                discovery.timeout|5000|Connect and read timeout, in milliseconds, for the file a domain publishes for discovery, which is asked once rather than with the retries of jenesis.repository.retries; a domain that does not answer in time counts as absent, and a value that is not positive is refused
                discovery.ttl|24|Hours a domain's answer is kept in the local module folder, under well-known/ in jenesis.module.local or ~/.jenesis, so that a later build reads it rather than asking again: the file the domain publishes, or that it answered 404 or 410 for none, while a fetch that failed otherwise, as by a timeout, a reset or a server error, is kept as nothing and a certificate that does not verify fails the build; jenesis.repository.offline reads what is kept whatever its age, a local module folder that cannot be written, as a container mounts it, is read alone, and 0 keeps nothing; no switch asks a domain again early, so delete well-known/ for that; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                maven.uri||Maven remotes, comma-separated and queried left to right; a |<groupId> suffix, repeatable, asks a remote only for that group and the groups below it, and @<name> splices the chain that jenesis.<name> or the environment variable <name> holds; an empty value names no remote at all, so only discovery and the artifacts a build stored answer (env MAVEN_REPOSITORY_URI)
                maven.local||Local Maven cache folder (env MAVEN_REPOSITORY_LOCAL); only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                maven.token||Authorization header value for the Maven remotes, sent as given, so it names its scheme, as Bearer <token> or Basic <credentials> (env MAVEN_REPOSITORY_TOKEN); only the command line, ~/.jenesis/jenesis.properties or the environment may name one; a token the environment provides is sent only to the remotes the environment names, a remote a project's own files named is never sent one, and neither is the built-in public repository
                maven.embed|true|Carry the POM and a pom.properties in the jar under META-INF/maven/<groupId>/<artifactId>/, as Maven does, where the layout publishes to Maven
                maven.sources||Folders beside src/main/java and src/test/java that a pom.xml module compiles too, comma-separated names such as kotlin,groovy, each read as src/main/<name> and src/test/<name> where it exists
                maven.segments|2|Leading dot-separated segments of a module name that form its Maven groupId, when a module is published or resolved by the coordinate convention; a shorter name becomes the groupId in full
                module.uri||Jenesis module remotes, likewise, where a |<module> suffix asks a remote only for that module and the modules whose name it prefixes, and a maven:[<segments>:]<uri> entry reads a remote as a Maven repository by the publishing convention, taking that many leading segments of a module name as its groupId where it names a count and jenesis.maven.segments where it does not, and a mapped:<uri or @>:<list>[;<list>...] entry reads a module only from the Maven repository at that URI, or the one jenesis.maven.uri configures for @, at the <groupId>/<artifactId>[/<type>[/<classifier>]] that one of the properties lists, each a URI or an absolute path, maps it to; an empty value names no remote at all, so only discovery, the local module folder and the artifacts a build stored answer (env JENESIS_REPOSITORY_URI)
                module.local||Local module cache folder (env JENESIS_REPOSITORY_LOCAL), which also keeps the answers of discovery under well-known/; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                module.token||Authorization header value for the module remote, sent as given (env JENESIS_REPOSITORY_TOKEN); likewise, and only the first remote of the chain is sent it, so a fallback mirror never sees it
                module.source|service|Who resolves a module's coordinates: service asks the repository at jenesis.module.uri, git reads the published index itself and fetches from jenesis.maven.uri
                module.index||Location of that published index when git resolves it, a folder of per-module TSV files (env JENESIS_INDEX_URI); default: the jenesis-modules data on GitHub
                module.prerelease||Accept a pre-release when asking the module index for a module's newest version
                module.speculative||Accept a version the module index has not recorded but guesses exists
                openpgp.uri|keyserver.ubuntu.com, keys.openpgp.org|HKP key server roots, likewise, and @<name> splices what jenesis.<name> or the environment variable <name> holds; a server speaking another protocol is another repository (env OPENPGP_REPOSITORY_URI)
                openpgp.local|.jenesis/keys|Local key cache folder, one file per fingerprint (env OPENPGP_REPOSITORY_LOCAL); a file a project provides names only a folder inside the project
                cache.uri||Build cache: a file:// folder, or an http(s):// cache server; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                cache.project||Project name sent to a cache server (env JENESIS_CACHE_PROJECT)
                cache.key||Access key sent to a cache server (env JENESIS_CACHE_KEY); only the command line, ~/.jenesis/jenesis.properties or the environment may name one, and a cache server a project's own files named is not sent it
                cache.connect|PT1S|Connect timeout for a cache server
                cache.read|PT10S|Read timeout for a cache server
                cache.insecure|false|Permit the cache key over plaintext http off loopback, and over https accept a certificate that does not verify; likewise yours alone to allow
                test.skip|false|Skip executing tests, still resolving what running them needs
                test.filter||Comma-separated [<module>/]<classRegex>[#<method>] entries restricting which tests run, in place of the default naming and of its JUnit 4 check that a class holds tests; an entry naming a module applies to its tests alone, and a test module no entry reaches runs none
                test.exclude||Comma-separated [<module>/]<classRegex> entries, each matched against the whole class name, leaving out of a run the test classes they match among those the default naming or jenesis.test.filter selects; an entry naming a module applies to its tests alone, and a test module whose every selected class is left out runs none
                test.tag||Comma-separated alternatives, a test running when it matches any of them: a tag, several joined by + for the tests carrying all of them, and -<tag> for the tests not carrying it, as -container or release+-soak,-container, with nothing to quote on a command line and translated for the test framework; a run remembers what it covered until the tests' inputs change, so a later selection runs only the tests no earlier one ran
                test.force|false|Execute tests even where a previous run already covered them
                test.jars|true|Run the tests against the packaged test jar; false runs them against the module's classes and resources folders instead, as Maven and Gradle do, so a test can read its own resources as files, while the modules it depends on stay jars; a module tested on the module path refuses false
                test.incremental||Run only the tests a change can reach: true, or the setting named with no value, detects changes with MD5, the name of another message digest with that one, and false runs every test
                test.parallel|false|Let the engine execute the matched tests concurrently
                test.reporting|false|Write a test run's reports into the module's reports/tests folder - the legacy JUnit XML and the open-test-reporting XML - and name that folder when the run fails; without it the JUnit Platform writes none
                stage.tests|false|Stage test-variant artifacts alongside the main ones
                tree.format|full|full|compact: what the dependencies selector prints
                tree.merge|true|One tree per module whose every node names the scopes it applies to; false prints one tree per module and scope
                tree.internal|false|List the project's own modules among the resolved dependencies and count them in the license summary
                tree.tests|true|Include test-variant modules in that output
                tree.tools|false|Show apart, under a heading naming each, the groups a module resolves for its build rather than for itself - a linter, a formatter, a plugin, an annotation processor - and count them in the license summary
                execute.module||Module to run, named by its source folder (server/ui or server+ui)
                execute.main||Main class to run, overriding the module's @jenesis.main
                execute.docker|false|Run the launched program in a container, independently of the build
                execute.image||Image for that container
                execute.readable||Extra read-only container mounts
                execute.writable||Extra writable container mounts
                execute.env||Host environment variables to forward
                sbom.cyclonedx|true|Emit a CycloneDX SBOM; sbom.properties selects its format
                graalvm.license||Licence the SBOM beside a native image records for the GraalVM that compiled it, as an SPDX identifier or a name; empty records none
                compliance.license|true|Run the license check a licensing.properties activates
                compliance.vulnerability|true|Run the vulnerability check a vulnerability.properties activates
                check.checkstyle|true|Checkstyle, activated by a checkstyle.properties naming its checkstyle.xml as configuration= or, where none is found, by a checkstyle.xml of a configuration folder; false leaves it out
                strict.checkstyle|false|Checkstyle fails the build when it exits reporting a violation, where it otherwise records its findings
                check.pmd|true|PMD, activated by a pmd.properties naming its pmd.xml as configuration= or, where none is found, by a pmd.xml of a configuration folder; false leaves it out
                strict.pmd|false|PMD fails the build when it exits reporting a violation, where it otherwise records its findings
                check.detekt|true|detekt, activated by a detekt.properties naming its detekt.yml as configuration= or, where none is found, by a detekt.yml of a configuration folder; false leaves it out
                strict.detekt|false|detekt fails the build when it exits reporting a violation, where it otherwise records its findings
                check.ktlint|true|ktlint linting the Kotlin sources, activated by a ktlint.properties naming its .editorconfig as configuration= or, where none is found, by a .editorconfig of a configuration folder; false leaves the linting out, as jenesis.format.ktlint=false does the formatting
                strict.ktlint|false|ktlint linting fails the build when it exits reporting a violation, where it otherwise records its findings
                check.scalastyle|true|Scalastyle, activated by a scalastyle.properties naming its scalastyle-config.xml as configuration= or, where none is found, by a scalastyle-config.xml of a configuration folder; false leaves it out
                strict.scalastyle|false|Scalastyle fails the build when it exits reporting a violation, where it otherwise records its findings
                check.scalafmt|true|scalafmt checking the Scala sources, activated by a scalafmt.properties naming its .scalafmt.conf as configuration= or, where none is found, by a .scalafmt.conf of a configuration folder; false leaves the check out, as jenesis.format.scalafmt=false does the formatting
                strict.scalafmt|false|scalafmt checking fails the build on a source it would format differently, where it otherwise lists such a source
                check.codenarc|true|CodeNarc, activated by a codenarc.properties naming its codenarc.xml as configuration= or, where none is found, by a codenarc.xml of a configuration folder; false leaves it out
                strict.codenarc|false|CodeNarc fails the build on any finding, where it otherwise records its findings
                validate.spotbugs|true|SpotBugs, activated by a spotbugs.properties, which may name an exclusion filter as configuration=, or, where none is found, by a spotbugs-exclude.xml of a configuration folder; false leaves it out
                strict.spotbugs|false|SpotBugs fails the build on any finding, where it otherwise records its findings
                validate.forbiddenapis|true|forbiddenapis, activated by a forbiddenapis.properties; false leaves it out
                strict.forbiddenapis|false|forbiddenapis fails the build on any finding, where it otherwise records its findings
                validate.jdeps|true|jdeps --jdk-internals, activated by a jdeps.properties; false leaves it out
                strict.jdeps|false|jdeps fails the build on any JDK internal API the classes use, where it otherwise records them
                validate.jdeprscan|true|jdeprscan, activated by a jdeprscan.properties; false leaves it out
                strict.jdeprscan|false|jdeprscan fails the build on any use of an API deprecated for removal, where it otherwise records them
                format.openrewrite|true|OpenRewrite, activated by an openrewrite.properties naming a rewrite.yml as configuration=, whose recipes it runs, or recipes as recipes=, or, where none is found, by a rewrite.yml of a configuration folder; it runs before the Java formatter and reads the module's dependencies
                format.googlejavaformat|true|google-java-format, activated by a googlejavaformat.properties whose lines are its options; refused beside another Java formatter
                format.palantir|true|palantir-java-format, activated by a palantir.properties whose lines are its options; refused beside another Java formatter
                format.eclipse|true|The Eclipse formatter, activated by an eclipse.properties, which may name the profile Eclipse exports as configuration=, or, where none is found, by an eclipse-formatter.xml of a configuration folder; refused beside another Java formatter
                format.ktlint|true|ktlint formatting the Kotlin sources, activated by what activates its linting; false leaves the formatting out while ktlint still lints, as where ktfmt formats them
                format.ktfmt|true|ktfmt formatting, activated by a ktfmt.properties, which names its style and what it does to the imports; refused beside ktlint formatting the same sources
                format.scalafmt|true|scalafmt formatting the Scala sources, activated by what activates its check; false leaves the formatting out while scalafmt still checks them
                format.license|true|The license header check, activated by a licenseheader.properties, whose text every Java, Kotlin, Scala and Groovy source of a module must start with as a block comment, exactly as it stands, or, without text, as SPDX lines naming the license and copyright the project declares
                format.rewrite|false|Let the formatters rewrite sources instead of verifying them
                generate.classpath|true|META-INF/services entries generated from a module-info's provides, and Enable-Native-Access for a module granting itself, so its jar also serves the class path, activated by a classpath.properties
                abi.mode||The ABI a module's classes offer, written as abi.properties with one hash per class file: modular for the public and protected members of the packages a module exports, classpath for every package with its package-private members as well; a module whose dependencies changed only below their ABI is not compiled again, save a test module against the module it tests; empty writes none and compiles every dependent again
                generate.xjc|true|JAXB generation, activated by an xjc.properties
                generate.protoc|true|protoc generation, activated by a protoc.properties
                generate.avro|true|Avro generation, activated by an avro.properties
                generate.wsimport|true|JAX-WS generation, activated by a wsimport.properties
                generate.openapi|true|OpenAPI generation, activated by an openapi.properties
                generate.antlr|true|ANTLR generation, activated by an antlr.properties
                generate.ksp|true|Kotlin Symbol Processing over the module's sources, activated by a ksp.properties, with the processors named by @jenesis.plugin ksp:processor <coordinate>
                observe.native|true|native-image reachability agent, activated by a graal.properties
                observe.jfr|true|Java Flight Recorder in the test JVM, activated by a jfr.properties whose lines are the options of the recording
                observe.jacoco|true|JaCoCo coverage, activated by a jacoco.properties
                observe.asyncprofiler|true|async-profiler in the test JVM, activated by an asyncprofiler.properties whose lines are the options of the profiler
                observe.pitest|true|PIT mutation testing, activated by a pitest.properties
                artifact.japicmp|true|japicmp API comparison, activated by a japicmp.properties
                artifact.revapi|true|revapi API comparison, activated by a revapi.properties
                artifact.mavenplugin|true|Maven plugin descriptor, activated by a mavenplugin.properties, written into the module's classes from the mojos they declare by Maven's own plugin tools
                artifact.gradleplugin|true|Gradle plugin markers, activated by a gradleplugin.properties of the project's configuration folder, staged for every plugin a staged jar declares in META-INF/gradle-plugins
                strict.revapi|false|revapi fails the build on any difference that reaches its severity, where it otherwise records them
                pmd.priority|5|The lowest rule priority PMD runs, from 1, the highest, to 5, the lowest, as maven-pmd-plugin's minimumPriority
                language.kotlin|true|Kotlin, the official plugin build.jenesis.plugin.kotlin, resolved where a module holds a .kt source; false leaves it out, and such a source then fails the build
                language.scala|true|Scala, the official plugin build.jenesis.plugin.scala, resolved where a module holds a .scala source; false leaves it out, and such a source then fails the build
                language.groovy|true|Groovy, the official plugin build.jenesis.plugin.groovy, resolved where a module holds a .groovy source; false leaves it out, and such a source then fails the build
                kotlin.incremental|false|Compile Kotlin incrementally through the Kotlin Build Tools API of Kotlin 2.3 or newer, resolved as org.jetbrains.kotlin:kotlin-build-tools-impl, which recompiles only what a change reaches, in a JVM of its own per compile or in one kept warm under process.mode=daemon; the -J and -D lines of process-kotlinc.properties configure that JVM
                scala.incremental|false|Compile Scala incrementally with Zinc, resolved as org.scala-sbt:zinc_2.13 in the group scala:zinc and driving the compiler through org.scala-lang:scala3-sbt-bridge at the compiler's version, which recompiles only what a change reaches, in a JVM of its own per compile or in one kept warm under process.mode=daemon; the lines of process-scalac.properties configure that JVM
                jarsigner.keystore||Key store jarsigner signs the produced jar with, in place of the unsigned one; a release machine supplies it, and a build that names any jarsigner setting without it fails rather than shipping unsigned; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                jarsigner.alias||Name of the key within that store; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                jarsigner.storepass||Where that store's password is read from: env <variable>, or file <path>; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                jarsigner.keypass||The same for the key's own password, where it has one of its own; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                jarsigner.storetype||Type of the store, as jarsigner names it: PKCS12, JKS, ...; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                jarsigner.tsa||Timestamp authority to stamp the signature with, so it outlives the certificate; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                jarsigner.arguments||Further jarsigner arguments, whitespace separated; only the command line or ~/.jenesis/jenesis.properties may set it, never a file a project provides
                release.uri||Jenesis module repository a release puts each staged module's jar into, one put per module at module/<module>/<version>/<module>.jar, so every module needs a version; the https address of a Jenesis Repository's jenesis repository, as jenesis.module.uri names it; a java repository takes Maven publishes only and refuses the put (env JENESIS_RELEASE_URI)
                release.publish|false|Publish what a release puts and runs - the jenesis.release.uri and jenesis.deploy.uri puts and the plugins of the hook point release, JReleaser's without --dry-run; without it, release and every pin run the release dry, checking what was staged and naming what it would put; only the command line may set it
                release.jreleaser|true|JReleaser, activated by a jreleaser.properties of the configuration folder naming its jreleaser.yml as configuration=, with the other values of that file, or, where none is found, by a jreleaser.yml, .yaml, .toml or .json of a configuration folder
                release.token||Authorization header value for that repository, sent as given (env JENESIS_RELEASE_TOKEN); only the command line, ~/.jenesis/jenesis.properties or the environment may name one, and it is never sent to a repository a project's own files named
                deploy.uri||Maven repository a release puts the staged Maven tree into, every file at <group path>/<artifactId>/<version>/<file> with its .md5, .sha1, .sha256 and .sha512, then the artifact's maven-metadata.xml merged with the one the repository holds; a SNAPSHOT version is put under a unique timestamped name beside its own maven-metadata.xml, as Maven deploys one; nothing is signed, so a release to Maven Central goes through the JReleaser plugin, which signs it and uses Central's publishing service, while its snapshot repository takes a SNAPSHOT from here as any repository does (env MAVEN_RELEASE_URI)
                deploy.token||Authorization header value for that repository, sent as given, so it names its scheme, as Bearer <token> or Basic <credentials> (env MAVEN_RELEASE_TOKEN); only the command line, ~/.jenesis/jenesis.properties or the environment may name one, and it is never sent to a repository a project's own files named
                """;

    private static SortedMap<String, String> settings(Environment environment) {
        SortedMap<String, String> settings = new TreeMap<>();
        environment.keys().forEach((key, value) -> settings.put("jenesis." + key, value));
        return settings;
    }

    private static void printConfiguration(Environment environment) {
        for (String line : CATALOGUE.lines().toList()) {
            String[] entry = line.split("\\|", 3);
            String name = "jenesis." + entry[0];
            String value = environment.getProperty(entry[0]);
            String assignment = name + "=" + (value == null ? entry[1] : value);
            String state = value != null ? "[set]" : entry[1].isEmpty() ? "[unset]" : "[default]";
            String prefix = assignment + " ".repeat(Math.max(1, 46 - assignment.length())) + state;
            environment.out().accept(prefix + " ".repeat(Math.max(1, 56 - prefix.length())) + entry[2]);
        }
    }

    SequencedMap<String, Path> doMain(String... selectors) throws IOException, InterruptedException {
        if (selectors.length == 1 && selectors[0].equals(CONFIGURATION)) {
            printConfiguration(environment);
            return Collections.emptyNavigableMap();
        }
        if (selectors.length == 1 && selectors[0].equals(PROPERTIES)) {
            settings(environment).forEach((name, value) -> environment.out().accept(name + "=" + value));
            return Collections.emptyNavigableMap();
        }
        if (environment.flag("project.docker")) {
            SortedMap<String, String> properties = new TreeMap<>(settings(environment));
            properties.keySet().removeAll(Make.CONTAINED);
            String image = environment.getProperty("project.image");
            Path root = this.root().toAbsolutePath().normalize();
            DockerizedJava docker = image == null ? new DockerizedJava(root) : new DockerizedJava(root, image);
            for (Path path : Stream.of(this.target(), this.reproduce(), this.artifacts())
                    .filter(Objects::nonNull)
                    .toList()) {
                Path absolute = (path.isAbsolute() ? path : root.resolve(path)).normalize();
                if (!absolute.startsWith(root)) {
                    docker = docker.mount(absolute, absolute.toString(), false);
                }
            }
            SequencedSet<Path> locations = new LinkedHashSet<>();
            locations.addAll(this.configuration());
            locations.addAll(this.boms());
            for (Path path : this.metadata() == null ? List.<Path>of() : this.metadata()) {
                Path parent = (path.isAbsolute() ? path : root.resolve(path)).normalize().getParent();
                if (parent != null) {
                    locations.add(parent);
                }
            }
            for (Path location : locations) {
                Path absolute = (location.isAbsolute() ? location : root.resolve(location)).normalize();
                if (!absolute.startsWith(root) && Files.isDirectory(absolute)) {
                    docker = docker.mount(absolute, absolute.toString(), true);
                }
            }
            String cacheOverride = environment.getProperty("project.cache");
            if (cacheOverride != null && !cacheOverride.contains("://")) {
                Path cache = root.resolve(cacheOverride.isEmpty()
                        ? Path.of(".jenesis", "cache")
                        : Path.of(cacheOverride)).normalize();
                if (!cache.startsWith(root)) {
                    docker = docker.mount(Files.createDirectories(cache), cache.toString(), false);
                }
            }
            String cacheUri = environment.getProperty("cache.uri");
            if (cacheUri != null && cacheUri.startsWith("file:")) {
                Path cache = Path.of(URI.create(cacheUri)).toAbsolutePath().normalize();
                if (!cache.startsWith(root)) {
                    docker = docker.mount(Files.createDirectories(cache), cache.toString(), false);
                }
            }
            docker = docker.mounts(environment.getProperty("project.readable"), root, true);
            docker = docker.mounts(environment.getProperty("project.writable"), root, false);
            docker = docker.envs(environment.getProperty("project.env"));
            String mavenRepositoryUri = environment.getProperty("maven.uri", System.getenv("MAVEN_REPOSITORY_URI"));
            if (mavenRepositoryUri != null) {
                docker = docker.env("MAVEN_REPOSITORY_URI", mavenRepositoryUri);
            }
            String jenesisRepositoryUri = environment.getProperty("module.uri", System.getenv("JENESIS_REPOSITORY_URI"));
            if (jenesisRepositoryUri != null) {
                docker = docker.env("JENESIS_REPOSITORY_URI", jenesisRepositoryUri);
            }
            String mavenRepositoryLocal = environment.getProperty("maven.local", System.getenv("MAVEN_REPOSITORY_LOCAL"));
            Path mavenLocal = (mavenRepositoryLocal == null
                               ? Path.of(System.getProperty("user.home"), ".m2", "repository")
                               : Path.of(mavenRepositoryLocal)).toAbsolutePath().normalize();
            if (Files.isDirectory(mavenLocal)) {
                docker = docker.mount(mavenLocal, mavenLocal.toString(), true);
                docker = docker.env("MAVEN_REPOSITORY_LOCAL", mavenLocal.toString());
            }
            String openPgpRepositoryUri = environment.getProperty("openpgp.uri", System.getenv("OPENPGP_REPOSITORY_URI"));
            if (openPgpRepositoryUri != null) {
                docker = docker.env("OPENPGP_REPOSITORY_URI", openPgpRepositoryUri);
            }
            String openPgpRepositoryLocal = environment.getProperty("openpgp.local", System.getenv("OPENPGP_REPOSITORY_LOCAL"));
            if (openPgpRepositoryLocal != null) {
                Path openPgpLocal = Path.of(openPgpRepositoryLocal).toAbsolutePath().normalize();
                if (Files.isDirectory(openPgpLocal) && !openPgpLocal.startsWith(root)) {
                    docker = docker.mount(openPgpLocal, openPgpLocal.toString(), true);
                }
                docker = docker.env("OPENPGP_REPOSITORY_LOCAL", openPgpLocal.toString());
            }
            String jenesisRepositoryLocal = environment.getProperty("module.local", System.getenv("JENESIS_REPOSITORY_LOCAL"));
            Path jenesisLocal = (jenesisRepositoryLocal == null
                                 ? Path.of(System.getProperty("user.home"), ".jenesis")
                                 : Path.of(jenesisRepositoryLocal)).toAbsolutePath().normalize();
            if (Files.isDirectory(jenesisLocal)) {
                docker = docker.mount(jenesisLocal, jenesisLocal.toString(), true);
                docker = docker.env("JENESIS_REPOSITORY_LOCAL", jenesisLocal.toString());
            }
            if (environment.flag("print.docker", true)) {
                environment.out().accept("Launching build within Docker image: " + docker.image());
            }
            int code = docker.execute("build/jenesis/Make.java", properties, selectors);
            if (code != 0) {
                System.exit(code);
            }
            return Collections.emptyNavigableMap();
        }
        if (environment.flag("project.watch")) {
            watch(selectors);
            return Collections.emptyNavigableMap();
        }
        return this.build(selectors);
    }

    public static SequencedMap<String, Path> perform(Map<String, String> keys,
                                                     Path root,
                                                     SequencedSet<Path> profiles,
                                                     String... selectors) {
        return perform(new Environment(keys), root, profiles, selectors);
    }

    public static SequencedMap<String, Path> perform(Environment environment,
                                                     Path root,
                                                     SequencedSet<Path> profiles,
                                                     String... selectors) {
        try {
            return ofEnvironment(environment, root).profiles(profiles.toArray(Path[]::new)).doMain(selectors);
        } catch (Throwable t) {
            report(t, environment);
            return null;
        }
    }

    public static int run(Map<String, String> keys,
                          String mainClass,
                          Path root,
                          SequencedSet<Path> profiles,
                          String... selectors) {
        return run(new Environment(keys), mainClass, root, profiles, selectors);
    }

    public static int run(Environment environment,
                          String mainClass,
                          Path root,
                          SequencedSet<Path> profiles,
                          String... selectors) {
        if (mainClass.equals(Project.class.getName())) {
            return perform(environment, root, profiles, selectors) == null ? 1 : 0;
        }
        try {
            if (mainClass.equals(Execution.class.getName())) {
                return Execution.run(environment, root, profiles, selectors);
            }
            MethodHandle main = mainMethod(Class.forName(mainClass, true, Project.class.getClassLoader()));
            if (main.type().parameterCount() == 0) {
                main.invoke();
            } else {
                main.invoke(selectors);
            }
            return 0;
        } catch (Throwable t) {
            report(t, environment);
            return 1;
        }
    }

    private static MethodHandle mainMethod(Class<?> type) throws Throwable {
        Project.class.getModule().addReads(type.getModule());
        MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(type, MethodHandles.lookup());
        for (MethodType signature : List.of(MethodType.methodType(void.class, String[].class),
                MethodType.methodType(void.class))) {
            MethodHandle main;
            try {
                main = lookup.findStatic(type, "main", signature);
            } catch (NoSuchMethodException | IllegalAccessException _) {
                try {
                    main = lookup.findVirtual(type, "main", signature);
                } catch (NoSuchMethodException | IllegalAccessException _) {
                    continue;
                }
            }
            int modifiers = lookup.revealDirect(main).getModifiers();
            if (Modifier.isPrivate(modifiers)) {
                continue;
            } else if (Modifier.isStatic(modifiers)) {
                return main;
            } else if (Modifier.isAbstract(type.getModifiers())) {
                throw new IllegalArgumentException("The main class " + type.getName() + " is abstract or an interface"
                        + " but its main method is not static: make the class concrete, or the method static");
            }
            MethodHandle constructor;
            try {
                constructor = lookup.findConstructor(type, MethodType.methodType(void.class));
            } catch (NoSuchMethodException _) {
                constructor = null;
            }
            if (constructor == null || Modifier.isPrivate(lookup.revealDirect(constructor).getModifiers())) {
                throw new IllegalArgumentException("The main class " + type.getName() + " has an instance main method"
                        + " but no non-private constructor without parameters: declare one, declare the class static"
                        + " if it is an inner class, or make the main method static");
            }
            return main.bindTo(constructor.invoke());
        }
        throw new IllegalArgumentException("The main class " + type.getName() + " declares or inherits no main"
                + " method: declare a non-private void main(String[] args) or void main(), static or not");
    }

    private static void report(Throwable t, Environment environment) {
        if (t instanceof InterruptedException) {
            Thread.currentThread().interrupt();
        }
        StringWriter trace = new StringWriter();
        t.printStackTrace(new PrintWriter(trace));
        trace.toString().lines().forEach(environment.err());
        environment.err().accept("");
        environment.err().accept("The build failed with the error above; `help` explains how to run Jenesis.");
    }
}

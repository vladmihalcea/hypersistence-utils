package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Checksum;
import build.jenesis.ChecksumStatus;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import build.jenesis.module.ModuleInfoParser;
import java.util.jar.Attributes;

public class Javac extends ProcessBuildStep {

    public static final String HEADERS = "headers/";
    private static final String PROCESSOR = "javax.annotation.processing.Processor",
            PROCESSOR_SERVICE = "META-INF/services/" + PROCESSOR;
    private static final Pattern VERSIONED = Pattern.compile("META-INF/versions/(\\d+)/.+");
    private static final Set<String> SYSTEM_MODULE_OPTIONS = Set.of("--add-exports", "--add-reads", "--patch-module");
    private static final Set<String> SOURCE_OPTIONS = Set.of("--source", "-source");
    private static final Set<String> TARGET_OPTIONS = Set.of("--target", "-target");
    private static final ModuleInfoParser MODULE_INFO_PARSER = new ModuleInfoParser();

    private final boolean includeResources;
    private final PathPlacement pathPlacement;
    private final String group;
    private final boolean headers;

    public Javac(ProcessHandler.Factory factory) {
        this(factory.apply("javac", "bin/javac"),
             true,
             PathPlacement.INFERRED,
             "main",
             false,
             new Terms());
    }

    public static Javac ofEnvironment(Environment environment,
                               ProcessHandler.Factory factory) {
        return new Javac(factory.apply("javac", "bin/javac"),
                true,
                PathPlacement.INFERRED,
                "main",
                false,
                Terms.ofEnvironment(environment, "javac"));
    }

    private Javac(Function<List<String>, ? extends ProcessHandler> factory,
                  boolean includeResources,
                  PathPlacement pathPlacement,
                  String group,
                  boolean headers,
                  Terms terms) {
        super("javac", factory, terms);
        this.includeResources = includeResources;
        this.pathPlacement = pathPlacement;
        this.group = group;
        this.headers = headers;
    }

    public static void writeRelease(Path folder, String release, int feature) throws IOException {
        SequencedProperties properties = new SequencedProperties();
        if (release != null && release.endsWith("-preview")) {
            String number = release.substring(0, release.length() - "-preview".length());
            if (!number.matches("[0-9]+")) {
                throw new IllegalArgumentException("A release with preview features is written <feature>-preview, not "
                        + release);
            }
            if (Integer.parseInt(number) != feature) {
                throw new IllegalStateException("The release " + release + " enables the preview features of Java "
                        + number + ", which only a JDK " + number + " compiles, but the build runs on Java " + feature
                        + " - select one with -Djenesis.toolchain.version=" + number);
            }
            properties.setProperty("--release", number);
            properties.setProperty("--enable-preview", "");
        } else {
            properties.setProperty("--release", release == null || release.isEmpty()
                    ? Integer.toString(feature)
                    : release);
        }
        ProcessBuildStep.store(folder, ProcessBuildStep.PROCESS, "javac", properties);
    }

    @Override
    protected ProcessHandler handler(BuildStepContext context, List<String> commands) throws IOException {
        return commands.stream().anyMatch(option -> option.startsWith("-J"))
                ? ProcessHandler.OfProcess.ofJavaHome("bin/javac").apply(commands)
                        .daemon(terms.daemon())
                        .scoped(terms.shared())
                : super.handler(context, commands);
    }

    public Javac factory(ProcessHandler.Factory factory) {
        return new Javac(factory.apply("javac", "bin/javac"), includeResources, pathPlacement, group, headers, terms);
    }

    public Javac includeResources(boolean includeResources) {
        return new Javac(factory, includeResources, pathPlacement, group, headers, terms);
    }

    public Javac pathPlacement(PathPlacement pathPlacement) {
        return new Javac(factory, includeResources, pathPlacement, group, headers, terms);
    }

    public Javac group(String group) {
        return new Javac(factory, includeResources, pathPlacement, group, headers, terms);
    }

    public Javac headers(boolean headers) {
        return new Javac(factory, includeResources, pathPlacement, group, headers, terms);
    }

    public Javac verbose(BiConsumer<Boolean, String> printing) {
        return new Javac(factory, includeResources, pathPlacement, group, headers, terms.printing(printing));
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        Path resolved = Path.of(Dependencies.RESOLVED);
        SequencedMap<String, BuildStepArgument> compared = new LinkedHashMap<>();
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            BuildStepArgument argument = entry.getValue();
            if (argument.removed()) {
                compared.put(entry.getKey(), argument);
                continue;
            }
            Set<Path> processors = new HashSet<>();
            for (String group : List.of("plugin", "javac")) {
                for (Path jar : Dependencies.select(argument.folder(), group, "plugin")) {
                    processors.add(argument.folder().relativize(jar));
                }
            }
            Map<Path, Checksum> files = new HashMap<>(argument.files());
            argument.files().forEach((path, checksum) -> {
                Checksum abi = path.startsWith(resolved) && path.getFileName().toString().endsWith(".jar")
                        ? argument.files().get(path.resolveSibling(Abi.companion(path.getFileName().toString())))
                        : null;
                if (checksum.status() == ChecksumStatus.ALTERED
                        && abi != null
                        && abi.status() == ChecksumStatus.RETAINED
                        && !processors.contains(path)) {
                    files.put(path, Checksum.of(ChecksumStatus.RETAINED));
                }
            });
            compared.put(entry.getKey(), new BuildStepArgument(argument.folder(), files));
        }
        return hasRelevantChange(compared, Set.of(".java"), Set.of("javac"), includeResources);
    }

    public static boolean hasRelevantChange(SequencedMap<String, BuildStepArgument> arguments,
                                            Set<String> sourceExtensions,
                                            Set<String> tools,
                                            boolean includeResources) throws IOException {
        Path sourcesDir = Path.of(SOURCES);
        Path classesDir = Path.of(CLASSES);
        Path artifactsDir = Path.of(ARTIFACTS);
        Path resolvedDir = Path.of(Dependencies.RESOLVED);
        Path dependencyIndex = Path.of(DEPENDENCIES);
        Path modularIndex = Path.of(Dependencies.MODULAR);
        Path modularDir = Path.of(Dependencies.MODULAR_PATH);
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                return true;
            }
            Set<Path> processFiles = new HashSet<>();
            processFiles.add(Path.of(ProcessBuildStep.PROCESS, ProcessBuildStep.INDEX));
            for (String tool : tools) {
                Path located = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, tool);
                if (located != null) {
                    processFiles.add(argument.folder().relativize(located));
                }
            }
            for (Map.Entry<Path, Checksum> entry : argument.files().entrySet()) {
                if (entry.getValue().status() == ChecksumStatus.RETAINED) {
                    continue;
                }
                Path path = entry.getKey();
                if (processFiles.contains(path)) {
                    return true;
                }
                if (path.startsWith(classesDir)
                        || path.startsWith(artifactsDir)
                        || path.startsWith(resolvedDir)
                        || path.startsWith(dependencyIndex)
                        || path.startsWith(modularIndex)
                        || path.startsWith(modularDir)) {
                    return true;
                }
                if (path.startsWith(sourcesDir)) {
                    Path leaf = path.getFileName();
                    if (leaf != null) {
                        String name = leaf.toString();
                        for (String extension : sourceExtensions) {
                            if (name.endsWith(extension)) {
                                return true;
                            }
                        }
                        if (includeResources) {
                            Path relative = sourcesDir.relativize(path);
                            if (!BuildStep.underMetaInfVersions(relative) && !BuildStep.underBuildJenesis(relative)) {
                                return true;
                            }
                        }
                    }
                }
            }
        }
        return false;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        return super.apply(executor, context, arguments).thenComposeAsync(result -> {
            if (!result.next() || headers) {
                return CompletableFuture.completedStage(result);
            }
            try {
                return compileVersioned(executor, context, arguments).thenApply(_ -> result);
            } catch (IOException e) {
                CompletableFuture<BuildStepResult> failed = new CompletableFuture<>();
                failed.completeExceptionally(e);
                return failed;
            }
        }, executor);
    }

    @Override
    public CompletionStage<List<String>> process(Executor executor,
                                                 BuildStepContext context,
                                                 SequencedMap<String, BuildStepArgument> arguments,
                                                 SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        boolean sourceNamed = names(properties, SOURCE_OPTIONS), targetNamed = names(properties, TARGET_OPTIONS);
        if (sourceNamed || targetNamed || namesSystemModule(prepended(properties))) {
            for (SequencedMap<String, String> folder : properties.values()) {
                String release = folder.remove("--release");
                if (release != null) {
                    if (!sourceNamed) {
                        folder.put("--source", release);
                    }
                    if (!targetNamed) {
                        folder.put("--target", release);
                    }
                }
            }
        }
        Path output = headers ? context.supplement() : context.next(),
                target = Files.createDirectory(output.resolve(CLASSES)),
                generated = Files.createDirectory(output.resolve(SOURCES));
        List<String> files = new ArrayList<>(),
                path = new ArrayList<>(),
                processorPath = new ArrayList<>(),
                siblingClasses = new ArrayList<>(),
                options = new ArrayList<>(List.of("-d", target.toString(), "-s", generated.toString()));
        if (headers) {
            options.addAll(List.of("-h", Files.createDirectory(context.next().resolve(HEADERS)).toString()));
        }
        boolean compilerPlugins = false;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                path.add(jar.toString());
            }
            for (Path jar : Dependencies.select(argument.folder(), "plugin", "plugin")) {
                processorPath.add(jar.toString());
            }
            for (Path jar : Dependencies.select(argument.folder(), "javac", "plugin")) {
                processorPath.add(jar.toString());
                compilerPlugins = true;
            }
            Path sources = argument.folder().resolve(Bind.SOURCES),
                    classes = argument.folder().resolve(CLASSES);
            if (Files.exists(classes)) {
                siblingClasses.add(classes.toString());
            }
            if (Files.exists(sources)) {
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                        Path relative = sources.relativize(dir);
                        if (BuildStep.underBuildJenesis(relative)) {
                            return FileVisitResult.SKIP_SUBTREE;
                        }
                        Files.createDirectories(target.resolve(relative));
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String name = file.toString();
                        Path relative = sources.relativize(file);
                        if (name.endsWith(".java")) {
                            if (versionOf(relative) == null) {
                                files.add(name);
                            }
                        } else if (includeResources && !BuildStep.underMetaInfVersions(relative) && !BuildStep.underBuildJenesis(relative)) {
                            BuildStep.linkOrCopy(target.resolve(relative), file);
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        files.sort(null);
        if (files.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        String moduleInfo = files.stream()
                .filter(file -> file.endsWith(File.separator + "module-info.java"))
                .findFirst()
                .orElse(null);
        boolean module = moduleInfo != null;
        moduleVersion(properties, module);
        if (module) {
            List<String> declared = prepended(properties);
            String release = null;
            for (int index = 0; index < declared.size() - 1; index++) {
                if (declared.get(index).equals("--release") || declared.get(index).equals("--target")) {
                    release = declared.get(index + 1);
                }
            }
            String feature = release != null && release.startsWith("1.") ? release.substring(2) : release;
            if (feature != null && feature.matches("[0-9]+") && Integer.parseInt(feature) < 9) {
                throw new IllegalArgumentException("A module-info.java at the root of the sources is compiled at release "
                        + release + ", below 9, the first release of the Java Module System, so javac cannot compile"
                        + " the descriptor there: move it into META-INF/versions/9/ of the same source folder, as"
                        + " src/main/java/META-INF/versions/9/module-info.java, where it is compiled at release 9 into"
                        + " a multi-release jar, or raise the release to 9 or above");
            }
        }
        PathPlacement pathPlacement = this.pathPlacement.forModuleInfo(module);
        String patchModule = null;
        if (module && !siblingClasses.isEmpty()) {
            patchModule = MODULE_INFO_PARSER.identify(Path.of(moduleInfo)).coordinate();
        } else {
            path.addAll(siblingClasses);
        }
        if (properties.values().stream().noneMatch(folder -> folder.containsKey("--enable-preview"))) {
            for (String entry : path) {
                String preview = PathPlacement.preview(Path.of(entry));
                if (preview != null) {
                    throw new IllegalStateException("Compiling against " + Path.of(entry).getFileName()
                            + ", which uses the preview features of Java " + preview + ", enables them as well:"
                            + " declare the release as " + preview + "-preview, as @jenesis.release " + preview
                            + "-preview or with maven.compiler.enablePreview");
                }
            }
        }
        if (!path.isEmpty() || patchModule != null || !processorPath.isEmpty()) {
            for (String entry : path) {
                if (entry.indexOf(File.pathSeparatorChar) != -1) {
                    throw new IllegalArgumentException(
                            "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                }
            }
            for (String entry : processorPath) {
                if (entry.indexOf(File.pathSeparatorChar) != -1) {
                    throw new IllegalArgumentException(
                            "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                }
            }
            List<String> modulePath = new ArrayList<>(), classPath = new ArrayList<>();
            for (String entry : path) {
                (pathPlacement.test(Path.of(entry)) ? modulePath : classPath).add(entry);
            }
            if (!modulePath.isEmpty()) {
                options.add("--module-path");
                options.add(String.join(File.pathSeparator, modulePath));
            }
            if (!classPath.isEmpty()) {
                options.add("--class-path");
                options.add(String.join(File.pathSeparator, classPath));
            }
            if (patchModule != null) {
                options.add("--patch-module");
                options.add(patchModule + "=" + String.join(File.pathSeparator, siblingClasses));
            }
            options.addAll(processorPath(processorPath, pathPlacement.modular() && !compilerPlugins));
        }
        options.addAll(files);
        return CompletableFuture.completedStage(List.of("@" + argumentFile(context.supplement().resolve("javac.args"),
                options)));
    }

    private static void moduleVersion(SequencedMap<String, SequencedMap<String, String>> properties, boolean module) {
        for (SequencedMap<String, String> folder : properties.values()) {
            String version = module ? folder.get("--module-version") : folder.remove("--module-version");
            if (version != null && module) {
                try {
                    ModuleDescriptor.Version.parse(version);
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException("The module is versioned " + version + ", which is no module"
                            + " version, so javac cannot stamp it into the module-info.class: a module version starts"
                            + " with a digit, as 1.2.0 or 0-HEAD-SNAPSHOT do - set one of that form as the POM's"
                            + " <version> or as -Djenesis.project.version", e);
                }
            }
        }
    }

    private static boolean names(SequencedMap<String, SequencedMap<String, String>> properties, Set<String> options) {
        return properties.values().stream().anyMatch(folder -> folder.keySet().stream().anyMatch(options::contains));
    }

    private static boolean namesSystemModule(List<String> options) {
        for (int index = 0; index < options.size(); index++) {
            String option = options.get(index);
            for (String name : SYSTEM_MODULE_OPTIONS) {
                String value = option.equals(name) && index + 1 < options.size()
                        ? options.get(index + 1)
                        : option.startsWith(name + "=") ? option.substring(name.length() + 1) : null;
                if (value != null && ModuleFinder.ofSystem().find(value.split("[/=]", 2)[0]).isPresent()) {
                    return true;
                }
            }
        }
        return false;
    }

    private static List<String> processorPath(List<String> processorPath, boolean processorModules)
            throws IOException {
        if (processorPath.isEmpty()) {
            return List.of();
        }
        if (processorModules) {
            Set<ModuleReference> references;
            List<String> unresolved;
            try {
                ModuleFinder finder = ModuleFinder.of(processorPath.stream().map(Path::of).toArray(Path[]::new));
                references = finder.findAll();
                Set<String> resolved = ModuleLayer.boot().configuration()
                        .resolveAndBind(ModuleFinder.of(), finder, Set.of())
                        .modules()
                        .stream()
                        .map(ResolvedModule::name)
                        .collect(Collectors.toSet());
                unresolved = references.stream()
                        .map(reference -> reference.descriptor().name())
                        .filter(name -> !resolved.contains(name))
                        .sorted()
                        .toList();
            } catch (FindException | ResolutionException _) {
                references = Set.of();
                unresolved = List.of();
                processorModules = false;
            }
            if (processorModules && !unresolved.isEmpty()) {
                processorModules = false;
                for (ModuleReference reference : references) {
                    if (!reference.descriptor().isAutomatic()
                            && reference.descriptor().provides().stream()
                            .anyMatch(provides -> provides.service().equals(PROCESSOR))
                            && reference.location().isPresent()) {
                        Path location = Path.of(reference.location().get());
                        boolean declared;
                        if (Files.isDirectory(location)) {
                            declared = Files.exists(location.resolve(PROCESSOR_SERVICE));
                        } else {
                            try (JarFile jar = new JarFile(location.toFile())) {
                                declared = jar.getEntry(PROCESSOR_SERVICE) != null;
                            }
                        }
                        if (!declared) {
                            throw new IllegalStateException("The annotation processors run on the class path, as"
                                    + " nothing on the processor module path requires " + String.join(", ", unresolved)
                                    + ", which javac would therefore leave out of the processors' module graph, but "
                                    + reference.descriptor().name() + " declares its processor in its module-info.java"
                                    + " alone, without " + PROCESSOR_SERVICE + ", so the class path cannot find it:"
                                    + " compile with these processors in modules of their own");
                        }
                    }
                }
            }
        }
        return List.of(processorModules ? "--processor-module-path" : "--processor-path",
                String.join(File.pathSeparator, processorPath));
    }

    private CompletionStage<Void> compileVersioned(Executor executor,
                                                   BuildStepContext context,
                                                   SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<Integer, List<String>> versionedFiles = new TreeMap<>();
        SequencedMap<Integer, List<String>> versionedRoots = new TreeMap<>();
        Map<Integer, String> versionedModules = new HashMap<>();
        List<String> dependencyPath = new ArrayList<>(), processorPath = new ArrayList<>();
        List<Path> classRoots = new ArrayList<>();
        boolean compilerPlugins = false;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path classes = argument.folder().resolve(CLASSES);
            if (Files.exists(classes)) {
                dependencyPath.add(classes.toString());
                classRoots.add(classes);
            }
            for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                dependencyPath.add(jar.toString());
            }
            for (Path jar : Dependencies.select(argument.folder(), "plugin", "plugin")) {
                processorPath.add(jar.toString());
            }
            for (Path jar : Dependencies.select(argument.folder(), "javac", "plugin")) {
                processorPath.add(jar.toString());
                compilerPlugins = true;
            }
            Path sources = argument.folder().resolve(Bind.SOURCES);
            if (Files.exists(sources)) {
                Files.walkFileTree(sources, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                        String name = file.toString();
                        if (!name.endsWith(".java")) {
                            return FileVisitResult.CONTINUE;
                        }
                        Integer release = versionOf(sources.relativize(file));
                        if (release != null) {
                            versionedFiles.computeIfAbsent(release, _ -> new ArrayList<>()).add(name);
                            Path versions = sources.resolve("META-INF/versions/" + release);
                            if (versions.relativize(file).equals(Path.of("module-info.java"))) {
                                versionedModules.put(release, MODULE_INFO_PARSER.identify(file).coordinate());
                            }
                            String root = versions.toString();
                            List<String> roots = versionedRoots.computeIfAbsent(release, _ -> new ArrayList<>());
                            if (!roots.contains(root)) {
                                roots.add(root);
                            }
                        }
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
        }
        dependencyPath.sort(null);
        versionedFiles.values().forEach(versioned -> versioned.sort(null));
        versionedRoots.values().forEach(roots -> roots.sort(null));
        if (versionedFiles.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        SequencedMap<String, SequencedMap<String, String>> properties = properties(arguments);
        moduleVersion(properties, !versionedModules.isEmpty());
        boolean named = names(properties, SOURCE_OPTIONS) || names(properties, TARGET_OPTIONS);
        properties.values().forEach(folder -> folder.keySet().removeIf(option -> option.equals("--release")
                || SOURCE_OPTIONS.contains(option)
                || TARGET_OPTIONS.contains(option)));
        List<String> prepended = prepended(properties);
        SequencedMap<String, SequencedMap<String, String>> unprocessed = new LinkedHashMap<>();
        properties.forEach((folder, options) -> {
            SequencedMap<String, String> kept = new LinkedHashMap<>(options);
            kept.keySet().removeIf(option -> option.startsWith("-A") || option.startsWith("-proc:"));
            unprocessed.put(folder, kept);
        });
        List<String> descriptorOptions = new ArrayList<>(prepended(unprocessed));
        descriptorOptions.add("-proc:none");
        boolean sourceAndTarget = named || namesSystemModule(prepended);
        Path mainTarget = context.next().resolve(CLASSES);
        Path moduleInfo = mainTarget.resolve("module-info.class");
        String moduleName;
        if (Files.exists(moduleInfo)) {
            try (InputStream in = Files.newInputStream(moduleInfo)) {
                moduleName = ModuleDescriptor.read(in).name();
            }
        } else {
            moduleName = null;
        }
        boolean plugins = compilerPlugins;
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        for (Map.Entry<Integer, List<String>> entry : versionedFiles.entrySet()) {
            int release = entry.getKey();
            List<String> files = entry.getValue();
            List<String> roots = versionedRoots.get(release);
            List<String> overlays = new ArrayList<>();
            for (Path classes : classRoots) {
                for (int lower = release; lower >= 9; lower--) {
                    Path overlay = classes.resolve("META-INF/versions/" + lower);
                    if (Files.isDirectory(overlay)) {
                        overlays.add(overlay.toString());
                    }
                }
            }
            chain = chain.thenComposeAsync(_ -> {
                try {
                    return runVersioned(executor,
                            context,
                            files.stream().allMatch(file -> Path.of(file).getFileName().toString().equals("module-info.java"))
                                    ? descriptorOptions
                                    : prepended,
                            sourceAndTarget,
                            dependencyPath,
                            processorPath,
                            plugins,
                            moduleName,
                            versionedModules.get(release),
                            mainTarget,
                            roots,
                            overlays,
                            classRoots,
                            release,
                            files);
                } catch (IOException e) {
                    return CompletableFuture.failedFuture(e);
                }
            }, executor);
        }
        return chain.thenComposeAsync(_ -> {
            try {
                if (!hasMultiReleaseManifest(arguments)) {
                    Path manifest = context.next().resolve(Versions.MANIFEST);
                    if (!Files.exists(manifest)) {
                        Files.writeString(manifest, "Manifest-Version: 1.0\r\nMulti-Release: true\r\n");
                    }
                }
                return CompletableFuture.<Void>completedFuture(null);
            } catch (IOException e) {
                return CompletableFuture.<Void>failedFuture(e);
            }
        }, executor);
    }

    public static boolean hasMultiReleaseManifest(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(Versions.MANIFEST);
            if (!Files.exists(candidate)) {
                continue;
            }
            Manifest manifest;
            try (InputStream in = Files.newInputStream(candidate)) {
                manifest = new Manifest(in);
            }
            if (manifest.getMainAttributes().getValue(Attributes.Name.MULTI_RELEASE) != null) {
                return true;
            }
        }
        return false;
    }

    private CompletionStage<Void> runVersioned(Executor executor,
                                               BuildStepContext context,
                                               List<String> prepended,
                                               boolean sourceAndTarget,
                                               List<String> dependencyPath,
                                               List<String> processorPath,
                                               boolean compilerPlugins,
                                               String moduleName,
                                               String versionedModule,
                                               Path mainTarget,
                                               List<String> versionedRoots,
                                               List<String> overlays,
                                               List<Path> siblings,
                                               int release,
                                               List<String> files) throws IOException {
        Path target = Files.createDirectories(context.next()
                .resolve(CLASSES + "META-INF/versions/" + release)),
                generated = Files.createDirectories(context.next()
                        .resolve(SOURCES + "META-INF/versions/" + release));
        List<String> commands = new ArrayList<>(prepended),
                options = new ArrayList<>(List.of("-d", target.toString(), "-s", generated.toString()));
        commands.addAll(sourceAndTarget
                ? List.of("--source", Integer.toString(release), "--target", Integer.toString(release))
                : List.of("--release", Integer.toString(release)));
        List<String> classPath = new ArrayList<>(), modulePath = new ArrayList<>(), patchModule = new ArrayList<>();
        String patched = versionedModule == null ? moduleName : versionedModule;
        if (patched != null) {
            if (versionedModule != null) {
                if (Files.exists(mainTarget)) {
                    patchModule.add(mainTarget.toString());
                }
            } else {
                if (Files.exists(mainTarget)) {
                    modulePath.add(mainTarget.toString());
                }
                patchModule.addAll(versionedRoots);
            }
            patchModule.addAll(overlays);
            List<String> patchedSiblings = siblings.stream().map(Path::toString).toList();
            patchModule.addAll(patchedSiblings);
            PathPlacement pathPlacement = this.pathPlacement.forModuleInfo(true);
            for (String entry : dependencyPath) {
                if (!patchedSiblings.contains(entry)) {
                    (pathPlacement.test(Path.of(entry)) ? modulePath : classPath).add(entry);
                }
            }
        } else {
            classPath.addAll(overlays);
            if (Files.exists(mainTarget)) {
                classPath.add(mainTarget.toString());
            }
            classPath.addAll(dependencyPath);
        }
        for (List<String> entries : List.of(classPath, modulePath, patchModule, processorPath)) {
            for (String entry : entries) {
                if (entry.indexOf(File.pathSeparatorChar) != -1) {
                    throw new IllegalArgumentException(
                            "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                }
            }
        }
        if (!modulePath.isEmpty()) {
            options.add("--module-path");
            options.add(String.join(File.pathSeparator, modulePath));
        }
        if (!classPath.isEmpty()) {
            options.add("--class-path");
            options.add(String.join(File.pathSeparator, classPath));
        }
        if (!patchModule.isEmpty()) {
            options.add("--patch-module");
            options.add(patched + "=" + String.join(File.pathSeparator, patchModule));
        }
        options.addAll(processorPath(processorPath,
                this.pathPlacement.forModuleInfo(patched != null).modular() && !compilerPlugins));
        options.addAll(files);
        commands.add("@" + argumentFile(context.supplement().resolve("javac-" + release + ".args"), options));
        Path output = context.supplement().resolve("output-" + release);
        Path error = context.supplement().resolve("error-" + release);
        ProcessHandler handler = handler(context, commands);
        Files.writeString(context.supplement().resolve("command-" + release), shell(handler.commands()));
        ProcessHandler.Tee tee = tee(executor, handler);
        CompletableFuture<Void> future = new CompletableFuture<>();
        executor.execute(() -> {
            try {
                int exitCode = execute(handler, output, error, tee);
                if (exitCode == 0) {
                    future.complete(null);
                } else {
                    future.completeExceptionally(new IllegalStateException(
                            "Unexpected exit code: " + exitCode + " (multi-release " + release + ")\n"
                                    + "To reproduce, execute:\n "
                                    + shell(handler.commands())
                                    + tail("Output", output)
                                    + tail("Error", error)));
                }
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
        });
        return future;
    }

    public static Integer versionOf(Path relative) {
        String normalized = relative.toString().replace(File.separatorChar, '/');
        Matcher matcher = VERSIONED.matcher(normalized);
        return matcher.matches() ? Integer.parseInt(matcher.group(1)) : null;
    }
}

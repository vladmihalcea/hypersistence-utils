package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class Inventory implements BuildStep {

    public static final String INVENTORY = "inventory.properties", UNSTAGED = "unstaged";

    public static SequencedMap<String, String> agents(Path folder) throws IOException {
        Path file = folder.resolve(INVENTORY);
        if (!Files.isRegularFile(file)) {
            return Collections.emptyNavigableMap();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedMap<String, String> agents = new LinkedHashMap<>();
        for (String prefix : prefixes(properties)) {
            for (int index = 0; properties.getProperty(prefix + ".agent." + index) != null; index++) {
                agents.putIfAbsent(Path.of(properties.getProperty(prefix + ".agent." + index)).getFileName().toString(),
                        properties.getProperty(prefix + ".agent." + index + ".arguments", ""));
            }
        }
        return agents;
    }

    public static SequencedSet<Path> nativeAccess(Path folder) throws IOException {
        Path file = folder.resolve(INVENTORY);
        if (!Files.isRegularFile(file)) {
            return Collections.emptyNavigableSet();
        }
        SequencedProperties properties = SequencedProperties.ofFiles(file);
        SequencedSet<Path> granted = new LinkedHashSet<>();
        for (String prefix : prefixes(properties)) {
            for (Path path : paths(properties, folder, prefix + ".nativeAccess")) {
                granted.add(path.toAbsolutePath().normalize());
            }
        }
        return granted;
    }
    public static final String POM = "pom.xml";

    private final String group;
    private final String module;
    private final NativeAccess nativeAccess;
    private final boolean supplement;
    private final transient Consumer<String> warnings;

    public enum NativeAccess {
        IGNORE, WARN, STRICT
    }

    public Inventory() {
        this("main", null, NativeAccess.IGNORE, false, null);
    }

    public static Inventory ofEnvironment(Environment environment) {
        String mode = environment.value("dependency.native", "ignore");
        try {
            return new Inventory()
                    .nativeAccess(NativeAccess.valueOf(mode.toUpperCase(Locale.ROOT)))
                    .warnings(environment.err());
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.dependency.native '" + mode
                    + "', expected one of: ignore, warn, strict");
        }
    }

    private Inventory(String group, String module, NativeAccess nativeAccess, boolean supplement, Consumer<String> warnings) {
        this.group = group;
        this.module = module;
        this.nativeAccess = nativeAccess;
        this.supplement = supplement;
        this.warnings = warnings;
    }

    public Inventory group(String group) {
        return new Inventory(group, module, nativeAccess, supplement, warnings);
    }

    public Inventory module(String module) {
        return new Inventory(group, module, nativeAccess, supplement, warnings);
    }

    public Inventory nativeAccess(NativeAccess nativeAccess) {
        return new Inventory(group, module, nativeAccess, supplement, warnings);
    }

    public Inventory supplement(boolean supplement) {
        return new Inventory(group, module, nativeAccess, supplement, warnings);
    }

    public Inventory warnings(Consumer<String> warnings) {
        return new Inventory(group, module, nativeAccess, supplement, warnings);
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(MODULE),
                Path.of(UNSTAGED),
                Path.of(ProcessBuildStep.PROCESS),
                Path.of(METADATA),
                Path.of(IDENTITY),
                Path.of(ATTACHMENTS),
                Path.of(NATIVES),
                Path.of(POM),
                Path.of(ARTIFACTS),
                Path.of(Classifiers.CLASSIFIERS),
                Path.of(Classifiers.CLASSIFIED),
                Path.of(Bom.BOM),
                Path.of(SOURCES),
                Path.of(DOCUMENTATION),
                Path.of(DEPENDENCIES),
                Path.of(BOMS),
                Path.of(Dependencies.GRAPH),
                Path.of(Dependencies.LICENSES),
                Path.of(JPackage.PACKAGES),
                Path.of(JMod.JMODS),
                Path.of(JLink.RUNTIME),
                Path.of(Layers.MEMBERSHIP),
                Path.of(LAYERS),
                Path.of(Docker.DOCKER),
                Path.of(NativeImage.NATIVE),
                Path.of(NativeImage.METADATA),
                Path.of(REPORTS)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String path = null;
        String mainClass = null;
        String module = null;
        String tests = null;
        String release = null;
        boolean abstractTest = false, staged = true;
        String version = null;
        String artifact = null;
        Path pomFile = null;
        boolean modular = false;
        Path image = null;
        Path runtimeImage = null;
        Path layers = null;
        Path nativeBinary = null;
        Path metadataImage = null;
        Path dockerContext = null;
        SequencedSet<Path> artifacts = new LinkedHashSet<>();
        SequencedMap<String, Path> classified = new TreeMap<>();
        SequencedSet<Path> processes = new LinkedHashSet<>();
        SequencedSet<Path> bomArtifacts = new LinkedHashSet<>();
        SequencedSet<Path> sources = new LinkedHashSet<>(), generated = new LinkedHashSet<>();
        SequencedSet<Path> documentation = new LinkedHashSet<>();
        SequencedSet<Path> jmods = new LinkedHashSet<>();
        SequencedMap<String, Path> reports = new LinkedHashMap<>();
        SequencedSet<Path> graphs = new LinkedHashSet<>();
        SequencedSet<Path> dependencyLicenses = new LinkedHashSet<>();
        SequencedMap<String, Path> closureJars = new LinkedHashMap<>();
        SequencedMap<String, String> closureScopes = new LinkedHashMap<>();
        SequencedMap<String, String> closureChecksums = new LinkedHashMap<>();
        SequencedMap<String, Path> bomFiles = new LinkedHashMap<>();
        SequencedMap<String, String> bomValues = new LinkedHashMap<>();
        SequencedMap<String, String> attachments = new LinkedHashMap<>();
        SequencedSet<String> natives = new LinkedHashSet<>();
        boolean self = false;
        SequencedMap<String, String> licenses = new LinkedHashMap<>();
        SequencedSet<String> identity = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path folder = argument.folder();
            Path moduleProperties = folder.resolve(MODULE);
            if (Files.isRegularFile(moduleProperties)) {
                SequencedProperties properties = SequencedProperties.ofFiles(moduleProperties);
                if (path == null) {
                    path = properties.getProperty("path");
                }
                if (mainClass == null) {
                    mainClass = properties.getProperty("main");
                }
                if (module == null) {
                    module = properties.getProperty("module");
                }
                if (tests == null) {
                    tests = properties.getProperty("test");
                }
                abstractTest |= properties.flag("abstract");
                self |= properties.flag("native");
                modular |= properties.flag("modular");
            }
            staged &= !Files.exists(folder.resolve(UNSTAGED));
            Path javacProperties = ProcessBuildStep.locate(folder, ProcessBuildStep.PROCESS, "javac");
            if (release == null && javacProperties != null) {
                release = SequencedProperties.ofFiles(javacProperties).value("--release");
            }
            Path javaProperties = ProcessBuildStep.locate(folder, ProcessBuildStep.PROCESS, "java");
            if (javaProperties != null) {
                processes.add(javaProperties);
            }
            Path metadataFile = folder.resolve(METADATA);
            if (Files.isRegularFile(metadataFile)) {
                SequencedProperties properties = SequencedProperties.ofFiles(metadataFile);
                if (version == null) {
                    version = properties.getProperty("version");
                }
                if (artifact == null) {
                    artifact = properties.getProperty("artifact");
                }
                if (licenses.isEmpty()) {
                    for (String key : properties.stringPropertyNames()) {
                        boolean name = key.endsWith(".name");
                        if (key.startsWith("license.") && (name || key.endsWith(".url"))
                                && key.lastIndexOf('.') > "license.".length()) {
                            licenses.merge(key.substring("license.".length(), key.lastIndexOf('.')),
                                    properties.getProperty(key),
                                    (current, candidate) -> name ? candidate : current);
                        }
                    }
                }
            }
            Path identityFile = folder.resolve(IDENTITY);
            if (Files.isRegularFile(identityFile)) {
                identity.addAll(SequencedProperties.ofFiles(identityFile).stringPropertyNames());
            }
            Path pomCandidate = folder.resolve(POM);
            if (pomFile == null && Files.isRegularFile(pomCandidate)) {
                pomFile = pomCandidate;
            }
            collect(folder.resolve(ARTIFACTS), artifacts);
            Path classifiedFile = folder.resolve(Classifiers.CLASSIFIED);
            if (Files.isRegularFile(classifiedFile)) {
                SequencedProperties.ofFiles(classifiedFile).forEachProperty((classifier, variant) ->
                        classified.putIfAbsent(classifier, folder.resolve(variant)));
            }
            collect(folder.resolve(Bom.BOM), bomArtifacts);
            collect(folder.resolve(SOURCES), sources);
            if (holdsSources(folder.resolve(SOURCES))) {
                generated.add(folder.resolve(SOURCES));
            }
            collect(folder.resolve(DOCUMENTATION), documentation);
            Path packages = folder.resolve(JPackage.PACKAGES);
            if (image == null && Files.isDirectory(packages)) {
                image = packages;
            }
            collect(folder.resolve(JMod.JMODS), jmods);
            Path reportsFolder = folder.resolve(REPORTS);
            if (Files.isDirectory(reportsFolder)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(reportsFolder)) {
                    for (Path kind : stream) {
                        if (Files.isDirectory(kind)) {
                            reports.putIfAbsent(kind.getFileName().toString(), kind);
                        }
                    }
                }
            }
            Path graphFile = folder.resolve(Dependencies.GRAPH);
            if (Files.isRegularFile(graphFile)) {
                graphs.add(graphFile);
            }
            Path licensesFile = folder.resolve(Dependencies.LICENSES);
            if (Files.isRegularFile(licensesFile)) {
                dependencyLicenses.add(licensesFile);
            }
            Path layered = folder.resolve(Layers.MEMBERSHIP);
            if (layers == null && Files.isRegularFile(layered)) {
                layers = layered;
            }
            Path runtime = folder.resolve(JLink.RUNTIME);
            if (runtimeImage == null && Files.isDirectory(runtime)) {
                runtimeImage = runtime;
            }
            Path nativeOutput = folder.resolve(NativeImage.NATIVE);
            if (nativeBinary == null && Files.isDirectory(nativeOutput)) {
                nativeBinary = nativeOutput;
            }
            Path metadata = folder.resolve(NativeImage.METADATA);
            if (metadataImage == null && Files.isDirectory(metadata)) {
                metadataImage = metadata;
            }
            Path dockerFolder = folder.resolve(Docker.DOCKER);
            if (dockerContext == null && Files.isDirectory(dockerFolder)) {
                dockerContext = dockerFolder;
            }
            Path attachmentsFile = folder.resolve(ATTACHMENTS);
            if (Files.isRegularFile(attachmentsFile)) {
                SequencedProperties.ofFiles(attachmentsFile).forEachProperty(attachments::putIfAbsent);
            }
            Path nativesFile = folder.resolve(NATIVES);
            if (Files.isRegularFile(nativesFile)) {
                natives.addAll(SequencedProperties.ofFiles(nativesFile).stringPropertyNames());
            }
            collectClosure(folder, closureJars, closureScopes, closureChecksums);
            Path bomsFile = folder.resolve(BOMS);
            if (Files.isRegularFile(folder.resolve(DEPENDENCIES)) && Files.isRegularFile(bomsFile)) {
                SequencedProperties resolvedBoms = SequencedProperties.ofFiles(bomsFile);
                for (String key : resolvedBoms.stringPropertyNames()) {
                    if (key.startsWith("bom/")) {
                        bomFiles.putIfAbsent(key, folder.resolve(resolvedBoms.getProperty(key)).normalize());
                    } else if (key.startsWith("entry/") || key.startsWith("version/")) {
                        bomValues.putIfAbsent(key, resolvedBoms.getProperty(key));
                    }
                }
            }
        }
        String prefix = this.module == null ? prefixOf(path) : this.module + ".";
        SequencedProperties inventory = new SequencedProperties();
        if (supplement) {
            writePaths(inventory, context, prefix + "sources", sources.stream()
                    .filter(file -> archived(file.getParent(), file))
                    .toList());
            writePaths(inventory, context, prefix + "documentation", documentation);
            writeClosure(inventory, context, prefix + "tool.", closureJars, closureScopes, closureChecksums);
            if (!inventory.isEmpty()) {
                inventory.store(context.next().resolve(INVENTORY));
            }
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        SequencedSet<Path> runtime = new LinkedHashSet<>(artifacts);
        for (Map.Entry<String, Path> entry : closureJars.entrySet()) {
            String group = entry.getKey().substring(0, entry.getKey().indexOf('/'));
            String scope = closureScopes.get(entry.getKey());
            if (group.equals(this.group) && scope != null && List.of(scope.split(",")).contains("runtime")) {
                runtime.add(entry.getValue());
            }
        }
        int identityIndex = 0;
        for (String coordinate : identity) {
            inventory.setProperty(prefix + "identity." + identityIndex++, coordinate);
        }
        writeClosure(inventory, context, prefix + "dependency.", closureJars, closureScopes, closureChecksums);
        int agentIndex = 0;
        for (Map.Entry<String, String> attachment : attachments.entrySet()) {
            String key = attachment.getKey();
            int slash = key.indexOf('/');
            String candidate = key.substring(0, slash) + key.substring(key.indexOf('/', slash + 1));
            Path jar = null;
            for (Map.Entry<String, Path> closure : closureJars.entrySet()) {
                String coordinate = closure.getKey();
                if (coordinate.equals(candidate)
                        || coordinate.startsWith(candidate + "/")
                        && coordinate.indexOf('/', candidate.length() + 1) < 0) {
                    jar = closure.getValue();
                    break;
                }
            }
            if (jar == null) {
                throw new IllegalStateException("No resolved artifact for attached agent " + key);
            }
            inventory.setProperty(prefix + "agent." + agentIndex, relativize(context, jar));
            if (!attachment.getValue().isEmpty()) {
                inventory.setProperty(prefix + "agent." + agentIndex + ".arguments", attachment.getValue());
            }
            inventory.setProperty(prefix + "agent." + agentIndex + ".coordinate", key);
            agentIndex++;
        }
        SequencedSet<Path> reachable = new LinkedHashSet<>(runtime);
        for (Map.Entry<String, Path> entry : closureJars.entrySet()) {
            String scope = closureScopes.get(entry.getKey());
            if (entry.getKey().startsWith("layer:") && scope != null && List.of(scope.split(",")).contains("runtime")) {
                reachable.add(entry.getValue());
            }
        }
        SequencedMap<Path, String> layered = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : closureJars.entrySet()) {
            if (entry.getKey().startsWith("layer:") && reachable.contains(entry.getValue())) {
                layered.putIfAbsent(entry.getValue(), entry.getKey().substring(6, entry.getKey().indexOf('/')));
            }
        }
        SequencedSet<String> declaring = new LinkedHashSet<>();
        for (Map.Entry<String, Layers.Declaration> declaration : Layers.declared(arguments).entrySet()) {
            if (declaration.getValue().module().equals(module)) {
                declaring.add(declaration.getKey());
            }
        }
        SequencedSet<Path> granted = new LinkedHashSet<>();
        if (self) {
            granted.addAll(artifacts);
        }
        for (String key : natives) {
            Path jar = located(key, closureJars, key.startsWith("layer:") ? reachable : runtime);
            if (jar != null && layered.containsKey(jar) && !declaring.contains(layered.get(jar))) {
                jar = null;
            }
            if (jar == null) {
                throw new IllegalStateException("@jenesis.native grants "
                        + key.substring(0, key.indexOf('/')) + key.substring(key.indexOf('/', key.indexOf('/') + 1))
                        + " native access, but "
                        + (module == null ? path : module)
                        + " does not resolve it at run time");
            }
            granted.add(jar);
        }
        Deque<Path> delegating = new ArrayDeque<>(granted);
        while (!delegating.isEmpty()) {
            Path jar = delegating.removeFirst();
            Set<String> declared = PathPlacement.layers(jar).keySet();
            if (declared.isEmpty()) {
                continue;
            }
            for (String token : PathPlacement.nativeAccess(jar)) {
                Path named = located(key(token), closureJars, reachable);
                if (named != null && declared.contains(layered.get(named)) && granted.add(named)) {
                    delegating.addLast(named);
                }
            }
        }
        if (nativeAccess != NativeAccess.IGNORE) {
            List<String> violations = new ArrayList<>();
            for (Path jar : reachable) {
                Set<String> declared = PathPlacement.layers(jar).keySet();
                for (String token : PathPlacement.nativeAccess(jar)) {
                    Path named = located(key(token), closureJars, reachable);
                    if (named == null) {
                        continue;
                    }
                    boolean delegated = declared.contains(layered.get(named));
                    if (!granted.contains(delegated ? jar : named)) {
                        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                        String name = descriptor == null ? jar.getFileName().toString() : descriptor.name();
                        String coordinate = token.substring(token.indexOf('/') + 1);
                        violations.add(delegated
                                ? name + " passes native access on to "
                                        + (coordinate.startsWith("module/") ? coordinate.substring(7) : coordinate)
                                        + " in its layer " + layered.get(named) + ", once granted itself"
                                : name + " names " + token);
                    }
                }
            }
            if (!violations.isEmpty()) {
                String message = (module == null ? path : module)
                        + " runs modules that declare a need for native access it does not grant:\n  "
                        + String.join("\n  ", violations)
                        + "\nGrant each with @jenesis.native in "
                        + (module == null ? path : module)
                        + " if it runs code that needs it, or build with -Djenesis.dependency.native=ignore";
                if (nativeAccess == NativeAccess.STRICT) {
                    throw new IllegalStateException(message);
                } else if (warnings != null) {
                    warnings.accept("WARNING: " + message);
                }
            }
        }
        int nativeIndex = 0;
        for (Path jar : granted) {
            inventory.setProperty(prefix + "nativeAccess." + nativeIndex++, relativize(context, jar));
        }
        int bomIndex = 0;
        for (Map.Entry<String, Path> entry : bomFiles.entrySet()) {
            inventory.setProperty(prefix + "bom." + bomIndex++,
                    entry.getKey() + " " + relativize(context, entry.getValue()));
        }
        for (Map.Entry<String, String> entry : bomValues.entrySet()) {
            inventory.setProperty(prefix + "bom." + bomIndex++, entry.getKey() + " " + entry.getValue());
        }
        writePaths(inventory, context, prefix + "artifacts", artifacts);
        for (Map.Entry<String, Path> variant : classified.entrySet()) {
            inventory.setProperty(prefix + "attachment." + variant.getKey(), relativize(context, variant.getValue()));
        }
        writePaths(inventory, context, prefix + "process", processes);
        writePaths(inventory, context, prefix + "bomfile", bomArtifacts);
        writePaths(inventory, context, prefix + "sources", sources.stream()
                .filter(file -> archived(file.getParent(), file))
                .toList());
        writePaths(inventory, context, prefix + "generated", generated);
        writePaths(inventory, context, prefix + "documentation", documentation);
        writePaths(inventory, context, prefix + "jmod", jmods);
        for (Map.Entry<String, Path> entry : reports.entrySet()) {
            inventory.setProperty(prefix + "report." + entry.getKey(), relativize(context, entry.getValue()));
        }
        writePaths(inventory, context, prefix + "graph", graphs);
        for (Path graph : graphs) {
            if (SequencedProperties.ofFiles(graph).entrySet().stream()
                    .filter(edge -> edge.getKey().toString().startsWith("edge/"))
                    .map(edge -> edge.getValue().toString())
                    .map(edge -> edge.substring(0, edge.indexOf('\t')))
                    .allMatch(named -> named.equals(group) || named.startsWith("layer:"))) {
                inventory.setProperty(prefix + "report.dependencies", relativize(context, graph));
                break;
            }
        }
        writePaths(inventory, context, prefix + "licenses", dependencyLicenses);
        if (image != null) {
            inventory.setProperty(prefix + "package", relativize(context, image));
        }
        if (runtimeImage != null) {
            inventory.setProperty(prefix + "image", relativize(context, runtimeImage));
        }
        if (layers != null) {
            inventory.setProperty(prefix + "layers", relativize(context, layers));
        }
        if (nativeBinary != null) {
            inventory.setProperty(prefix + "native", relativize(context, nativeBinary));
        }
        if (metadataImage != null) {
            inventory.setProperty(prefix + "nativeimage", relativize(context, metadataImage));
        }
        if (dockerContext != null) {
            inventory.setProperty(prefix + "docker", relativize(context, dockerContext));
        }
        if (pomFile != null) {
            inventory.setProperty(prefix + "pom", relativize(context, pomFile));
        }
        if (version != null) {
            inventory.setProperty(prefix + "version", version);
        }
        if (artifact != null) {
            inventory.setProperty(prefix + "artifact", artifact);
        }
        int licenseIndex = 0;
        for (String license : licenses.values()) {
            inventory.setProperty(prefix + "license." + licenseIndex++, license);
        }
        if (tests != null) {
            inventory.setProperty(prefix + "test", tests);
        }
        if (release != null) {
            inventory.setProperty(prefix + "release", release);
        }
        if (abstractTest) {
            inventory.setProperty(prefix + "abstract", "true");
        }
        if (!staged) {
            inventory.setProperty(prefix + "stage", "false");
        }
        if (mainClass != null) {
            inventory.setProperty(prefix + "mainClass", mainClass);
        }
        if (path != null) {
            inventory.setProperty(prefix + "path", path);
        }
        if (modular && module != null) {
            inventory.setProperty(prefix + "module", module);
        }
        writePaths(inventory, context, prefix + "runtime", runtime);
        if (!inventory.isEmpty()) {
            inventory.store(context.next().resolve(INVENTORY));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private String key(String token) {
        int slash = token.indexOf('/'), second = slash < 0 ? -1 : token.indexOf('/', slash + 1);
        return slash < 0
                ? group + "/native/module/" + token
                : second < 0
                        ? group + "/native/maven/" + token
                        : token.substring(0, slash) + "/native" + token.substring(slash);
    }

    private static Path located(String key, SequencedMap<String, Path> closureJars, SequencedSet<Path> reachable) {
        int slash = key.indexOf('/'), second = key.indexOf('/', slash + 1);
        String candidate = key.substring(0, slash) + key.substring(second);
        for (Map.Entry<String, Path> closure : closureJars.entrySet()) {
            String coordinate = closure.getKey();
            if (reachable.contains(closure.getValue())
                    && (coordinate.equals(candidate)
                            || coordinate.startsWith(candidate + "/")
                            && coordinate.indexOf('/', candidate.length() + 1) < 0)) {
                return closure.getValue();
            }
        }
        if (key.startsWith("module/", second + 1)) {
            for (Path file : reachable) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(file);
                if (descriptor != null && descriptor.name().equals(key.substring(second + 8))) {
                    return file;
                }
            }
        }
        return null;
    }

    private static void writeClosure(SequencedProperties inventory,
                                     BuildStepContext context,
                                     String key,
                                     SequencedMap<String, Path> closureJars,
                                     SequencedMap<String, String> closureScopes,
                                     SequencedMap<String, String> closureChecksums) {
        int index = 0;
        for (Map.Entry<String, Path> entry : closureJars.entrySet()) {
            int slash = entry.getKey().indexOf('/');
            String checksum = closureChecksums.get(entry.getKey());
            inventory.setProperty(key + index,
                    entry.getKey().substring(slash + 1) + " " + relativize(context, entry.getValue())
                            + (checksum == null || checksum.isEmpty() ? "" : " " + checksum));
            String scope = closureScopes.get(entry.getKey());
            if (scope != null) {
                inventory.setProperty(key + index + ".scope", scope);
            }
            inventory.setProperty(key + index + ".group", entry.getKey().substring(0, slash));
            index++;
        }
    }

    private static void writePaths(SequencedProperties inventory,
                                   BuildStepContext context,
                                   String key,
                                   Collection<Path> files) {
        int index = 0;
        for (Path file : files) {
            inventory.setProperty(key + "." + index, relativize(context, file));
            index++;
        }
    }

    public static SequencedMap<String, List<Path>> supplements(Iterable<BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, List<Path>> supplements = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments) {
            Path inventoryFile = argument.removed() ? null : argument.folder().resolve(INVENTORY);
            if (inventoryFile == null || !Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            if (!prefixes(inventory).isEmpty()) {
                continue;
            }
            for (String key : inventory.stringPropertyNames()) {
                int index = key.lastIndexOf('.');
                String listed = index < 0 ? key : key.substring(0, index);
                if ((listed.endsWith(".sources") || listed.endsWith(".documentation")) && !supplements.containsKey(listed)) {
                    supplements.put(listed, paths(inventory, argument.folder(), listed));
                }
            }
        }
        return supplements;
    }

    public static List<Path> supplemented(SequencedProperties inventory,
                                          Path folder,
                                          String key,
                                          SequencedMap<String, List<Path>> supplements) {
        List<Path> paths = paths(inventory, folder, key);
        return paths.isEmpty() ? supplements.getOrDefault(key, List.of()) : paths;
    }

    public static String prefixOf(String path) {
        return ((path == null || path.isEmpty()) ? "module" : "module-" + path) + ".";
    }

    public static SequencedSet<String> prefixes(SequencedProperties inventory) {
        SequencedSet<String> prefixes = new LinkedHashSet<>();
        for (String key : inventory.stringPropertyNames()) {
            if (key.endsWith(".path") && key.equals(prefixOf(inventory.getProperty(key)) + "path")) {
                prefixes.add(key.substring(0, key.length() - ".path".length()));
            }
        }
        return prefixes;
    }

    public static String ownerOf(String key, Collection<String> prefixes) {
        String owner = null;
        for (String prefix : prefixes) {
            if (key.startsWith(prefix + ".") && (owner == null || prefix.length() > owner.length())) {
                owner = prefix;
            }
        }
        return owner;
    }

    public record Dependency(Path jar, String checksum, String scope, String group) {

        public String checksum(HashDigestFunction hashFunction) throws IOException {
            if (jar != null && Files.isRegularFile(jar)) {
                return hashFunction.encodedHash(jar);
            }
            return checksum.isEmpty() ? null : checksum;
        }
    }

    public static SequencedMap<String, Dependency> closure(Iterable<BuildStepArgument> arguments, String path) throws IOException {
        SequencedMap<String, Dependency> closure = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String key : List.of(prefixOf(path) + "dependency.", prefixOf(path) + "tool.")) {
                for (int index = 0; ; index++) {
                    String value = inventory.getProperty(key + index);
                    if (value == null) {
                        break;
                    }
                    String[] parts = value.split(" ", 3);
                    String group = inventory.getProperty(key + index + ".group");
                    if (group == null) {
                        group = "main";
                    }
                    closure.putIfAbsent(group + "/" + parts[0], new Dependency(
                            argument.folder().resolve(parts[1]).normalize(),
                            parts.length > 2 ? parts[2] : "",
                            inventory.getProperty(key + index + ".scope"),
                            group));
                }
            }
        }
        return closure;
    }

    public static SequencedSet<String> modulePaths(Iterable<BuildStepArgument> arguments) throws IOException {
        SequencedSet<String> paths = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String key : inventory.stringPropertyNames()) {
                if (key.endsWith(".path")) {
                    paths.add(inventory.getProperty(key));
                }
            }
        }
        return paths;
    }

    public static SequencedMap<String, SequencedMap<String, Path>> attachments(Iterable<BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<Path, SequencedProperties> inventories = new LinkedHashMap<>();
        SequencedSet<String> prefixes = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(INVENTORY);
            if (Files.isRegularFile(inventoryFile)) {
                SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
                inventories.put(argument.folder(), inventory);
                prefixes.addAll(prefixes(inventory));
            }
        }
        SequencedMap<String, SequencedMap<String, Path>> attachments = new LinkedHashMap<>();
        for (Map.Entry<Path, SequencedProperties> entry : inventories.entrySet()) {
            SequencedProperties inventory = entry.getValue();
            for (String key : inventory.stringPropertyNames()) {
                String prefix = ownerOf(key, prefixes);
                if (prefix == null || !key.startsWith("attachment.", prefix.length() + 1)) {
                    continue;
                }
                String classifier = key.substring(prefix.length() + 1 + "attachment.".length());
                Path file = entry.getKey().resolve(inventory.getProperty(key)).normalize();
                if (attachments.computeIfAbsent(prefix, _ -> new LinkedHashMap<>()).putIfAbsent(classifier, file) != null) {
                    throw new IllegalStateException("More than one file is attached to " + prefix + " as " + classifier
                            + " - give each attachment a classifier of its own");
                }
            }
        }
        return attachments;
    }

    public static SequencedMap<String, Path> bomReferences(Iterable<BuildStepArgument> arguments, String path)
            throws IOException {
        SequencedMap<String, Path> references = new LinkedHashMap<>();
        forEachBom(arguments, path, (key, value, folder) -> {
            if (key.startsWith("bom/")) {
                references.putIfAbsent(key.substring(4), folder.resolve(value).normalize());
            }
        });
        return references;
    }

    public static SequencedMap<String, String> bomEntries(Iterable<BuildStepArgument> arguments, String path)
            throws IOException {
        SequencedMap<String, String> entries = new LinkedHashMap<>();
        forEachBom(arguments, path, (key, value, _) -> {
            if (key.startsWith("entry/")) {
                entries.putIfAbsent(key.substring(6), value);
            }
        });
        return entries;
    }

    public static SequencedMap<String, String> bomVersions(Iterable<BuildStepArgument> arguments, String path)
            throws IOException {
        SequencedMap<String, String> versions = new LinkedHashMap<>();
        forEachBom(arguments, path, (key, value, _) -> {
            if (key.startsWith("version/")) {
                versions.putIfAbsent(key.substring(8), value);
            }
        });
        return versions;
    }

    private interface BomConsumer {

        void accept(String key, String value, Path folder);
    }

    private static void forEachBom(Iterable<BuildStepArgument> arguments,
                                   String path,
                                   BomConsumer consumer) throws IOException {
        String key = prefixOf(path) + "bom.";
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (int index = 0; ; index++) {
                String value = inventory.getProperty(key + index);
                if (value == null) {
                    break;
                }
                int space = value.indexOf(' ');
                if (space < 1) {
                    continue;
                }
                consumer.accept(value.substring(0, space), value.substring(space + 1), argument.folder());
            }
        }
    }

    public static Set<String> identities(Iterable<BuildStepArgument> arguments) throws IOException {
        Set<String> identities = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments) {
            if (argument.removed()) {
                continue;
            }
            Path inventoryFile = argument.folder().resolve(INVENTORY);
            if (!Files.isRegularFile(inventoryFile)) {
                continue;
            }
            SequencedProperties inventory = SequencedProperties.ofFiles(inventoryFile);
            for (String key : inventory.stringPropertyNames()) {
                if (key.contains(".identity.")) {
                    identities.add(inventory.getProperty(key));
                }
            }
        }
        return identities;
    }

    public static List<Path> paths(SequencedProperties inventory, Path folder, String key) {
        List<Path> resolved = new ArrayList<>();
        for (int index = 0; ; index++) {
            String value = inventory.getProperty(key + "." + index);
            if (value == null) {
                return resolved;
            }
            resolved.add(folder.resolve(value).normalize());
        }
    }

    private static boolean holdsSources(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            return false;
        }
        try (Stream<Path> files = Files.walk(folder)) {
            return files.anyMatch(file -> Files.isRegularFile(file) && !archived(folder, file));
        }
    }

    private static boolean archived(Path folder, Path file) {
        return folder.equals(file.getParent()) && file.getFileName().toString().endsWith(".jar");
    }

    private static void collect(Path folder, SequencedSet<Path> sink) throws IOException {
        if (!Files.isDirectory(folder)) {
            return;
        }
        List<Path> files = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(folder)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(file);
                }
            }
        }
        files.sort(Comparator.comparing(file -> file.getFileName().toString()));
        sink.addAll(files);
    }

    private static void collectClosure(Path folder,
                                       SequencedMap<String, Path> jars,
                                       SequencedMap<String, String> scopes,
                                       SequencedMap<String, String> checksums) throws IOException {
        Path indexFile = folder.resolve(DEPENDENCIES);
        if (!Files.isRegularFile(indexFile)) {
            return;
        }
        SequencedProperties index = SequencedProperties.ofFiles(indexFile);
        for (String key : index.stringPropertyNames()) {
            int firstSlash = key.indexOf('/');
            int secondSlash = firstSlash < 0 ? -1 : key.indexOf('/', firstSlash + 1);
            if (secondSlash < 0) {
                continue;
            }
            String group = key.substring(0, firstSlash);
            String scope = key.substring(firstSlash + 1, secondSlash);
            String coordinate = key.substring(secondSlash + 1);
            String entry = group + "/" + coordinate;
            String value = index.getProperty(key);
            int space = value.indexOf(' ');
            Path file = folder.resolve(space < 0 ? value : value.substring(0, space)).normalize();
            if (!Files.isRegularFile(file)) {
                continue;
            }
            if (space >= 0) {
                checksums.putIfAbsent(entry, value.substring(space + 1));
            }
            jars.putIfAbsent(entry, file);
            String prior = scopes.get(entry);
            if (prior == null) {
                scopes.put(entry, scope);
            } else if (!List.of(prior.split(",")).contains(scope)) {
                scopes.put(entry, prior + "," + scope);
            }
        }
    }

    private static String relativize(BuildStepContext context, Path file) {
        return context.next().relativize(file).toString().replace(File.separatorChar, '/');
    }
}

package build.jenesis.project;

import module java.base;
import module jdk.compiler;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.PathPlacement;
import build.jenesis.Resolver;
import build.jenesis.SequencedProperties;
import build.jenesis.step.Dependencies;
import build.jenesis.step.JDeps;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;
import java.util.jar.Attributes;
import javax.tools.ToolProvider;

public class ModularizeModule implements BuildExecutorModule {

    public static final String PSEUDO = "build.jenesis.pseudo.module";
    private static final String PREPARE = "prepare", DESCRIBE = "describe", MODULARIZE = "modularize";
    private static final String STAGED = "staged.properties", IDENTITY = "identity.properties";

    private final Mode mode;
    private final OffsetDateTime timestamp;
    private final JDeps describe;

    public ModularizeModule(ProcessHandler.Factory factory) {
        this(Mode.DECLARED, BuildStep.timestamp(), new JDeps(factory));
    }

    public static ModularizeModule ofEnvironment(Environment environment, ProcessHandler.Factory factory) {
        return new ModularizeModule(Mode.DECLARED, BuildStep.timestamp(environment), JDeps.ofEnvironment(environment, factory));
    }

    private ModularizeModule(Mode mode, OffsetDateTime timestamp, JDeps describe) {
        this.mode = mode;
        this.timestamp = timestamp;
        this.describe = describe;
    }

    public ModularizeModule mode(Mode mode) {
        return new ModularizeModule(mode, timestamp, describe);
    }

    public ModularizeModule timestamp(OffsetDateTime timestamp) {
        return new ModularizeModule(mode, timestamp, describe);
    }

    public ModularizeModule describe(JDeps describe) {
        return new ModularizeModule(mode, timestamp, describe);
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(PREPARE, new Prepare(mode), inherited.sequencedKeySet());
        buildExecutor.addStep(DESCRIBE, describe, PREPARE);
        buildExecutor.addStep(MODULARIZE, new Modularize(timestamp), PREPARE, DESCRIBE);
    }

    @Override
    public Optional<String> resolve(String path) {
        return path.equals(MODULARIZE) ? Optional.of("") : Optional.empty();
    }

    private record Identity(String module, String version) {
    }

    private static ModuleDescriptor derive(Path jar) {
        Set<ModuleReference> references;
        try {
            references = ModuleFinder.of(jar).findAll();
        } catch (FindException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new IllegalArgumentException(jar.getFileName()
                    + " cannot be resolved to a named module: "
                    + cause.getMessage(), e);
        }
        if (references.size() != 1) {
            throw new IllegalArgumentException(jar.getFileName() + " does not describe a single module");
        }
        return references.iterator().next().descriptor();
    }

    private static class Prepare implements BuildStep {

        @SuppressWarnings("unused")
        private final String version = Runtime.version().toString();

        private final Mode mode;

        private Prepare(Mode mode) {
            this.mode = mode;
        }

        @Override
        public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
            return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                    Path.of(DEPENDENCIES),
                    Path.of(Dependencies.RESOLVED)));
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path modules = Files.createDirectory(context.next().resolve(JDeps.MODULES));
            Path analyzed = Files.createDirectory(context.next().resolve(JDeps.ANALYZED));
            SequencedProperties staged = new SequencedProperties(), identity = new SequencedProperties();
            SequencedMap<Path, String> placed = new LinkedHashMap<>();
            SequencedMap<String, Path> claimed = new LinkedHashMap<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path index = argument.folder().resolve(DEPENDENCIES);
                if (!Files.exists(index)) {
                    continue;
                }
                SequencedMap<String, String> versions = versions(argument.folder());
                SequencedProperties properties = SequencedProperties.ofFiles(index);
                for (String key : properties.stringPropertyNames()) {
                    String value = properties.getProperty(key);
                    int space = value.indexOf(' ');
                    Path jar = argument.folder()
                            .resolve(space < 0 ? value : value.substring(0, space))
                            .normalize();
                    if (!Files.exists(jar)) {
                        continue;
                    }
                    String relative = placed.get(jar);
                    if (relative == null) {
                        ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                        String module;
                        if (descriptor == null) {
                            if (mode == Mode.DECLARED) {
                                throw new IllegalArgumentException("No module name is declared by "
                                        + key
                                        + ": require an upstream Automatic-Module-Name, declare @jenesis.alias"
                                        + " for the artifact, or set mode=synthetic in modules.properties to"
                                        + " derive a name of the form "
                                        + PSEUDO
                                        + "<hash>");
                            }
                            module = PSEUDO + HexFormat.of().formatHex(
                                    new HashDigestFunction("SHA-256").hash(jar), 0, 16);
                        } else {
                            module = descriptor.name();
                        }
                        boolean named = descriptor != null && !descriptor.isAutomatic();
                        Path previous = claimed.putIfAbsent(module, jar);
                        if (previous != null && !Files.isSameFile(previous, jar)) {
                            throw new IllegalArgumentException("Module " + module
                                    + " is claimed by both "
                                    + previous.getFileName()
                                    + " and "
                                    + jar.getFileName());
                        }
                        int first = key.indexOf('/'),
                                second = first < 0 ? -1 : key.indexOf('/', first + 1),
                                third = second < 0 ? -1 : key.indexOf('/', second + 1);
                        String version = third < 0 ? null : versions.get(key.substring(second + 1));
                        String name = version == null
                                ? module + ".jar"
                                : PathPlacement.fileName(key.substring(third + 1), module, named);
                        relative = (named ? JDeps.MODULES : JDeps.ANALYZED) + name;
                        Path target = (named ? modules : analyzed).resolve(name);
                        if (!Files.exists(target)) {
                            BuildStep.linkOrCopy(target, jar);
                        }
                        placed.put(jar, relative);
                        identity.setProperty(relative, module + (version == null ? "" : "\t" + version));
                    }
                    staged.setProperty(key, relative);
                }
            }
            staged.store(context.next().resolve(STAGED));
            identity.store(context.next().resolve(IDENTITY));
            SequencedProperties options = new SequencedProperties();
            options.setProperty("--multi-release", String.valueOf(Runtime.version().feature()));
            options.setProperty("--ignore-missing-deps", "");
            ProcessBuildStep.store(context.next(), ProcessBuildStep.PROCESS, "jdeps", options);
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static SequencedMap<String, String> versions(Path folder) throws IOException {
            SequencedMap<String, String> versions = new LinkedHashMap<>();
            Path graph = folder.resolve(Dependencies.GRAPH);
            if (!Files.exists(graph)) {
                return versions;
            }
            for (Resolver.Resolution resolution : Dependencies.graph(List.of(graph), List.of()).values()) {
                resolution.vertices().forEach((coordinate, vertex) -> {
                    if (vertex.resolvedVersion() != null) {
                        versions.putIfAbsent(coordinate + "/" + vertex.resolvedVersion(), vertex.resolvedVersion());
                    }
                });
            }
            return versions;
        }
    }

    private static class Modularize implements BuildStep {

        private final OffsetDateTime timestamp;

        private Modularize(OffsetDateTime timestamp) {
            this.timestamp = timestamp;
        }

        @Override
        public CompletionStage<BuildStepResult> apply(Executor executor,
                                                      BuildStepContext context,
                                                      SequencedMap<String, BuildStepArgument> arguments)
                throws IOException {
            Path staged = arguments.get(PREPARE).folder(),
                    descriptors = arguments.get(DESCRIBE).folder().resolve(JDeps.DESCRIPTORS);
            SequencedProperties staging = SequencedProperties.ofFiles(staged.resolve(STAGED));
            SequencedMap<String, Identity> identities = identities(staged);
            Path target = Files.createDirectory(context.next().resolve(Dependencies.MODULAR_PATH));
            SequencedMap<String, String> owners = owners(staged, staging, identities);
            SequencedMap<Path, Path> emitted = new LinkedHashMap<>();
            SequencedProperties index = new SequencedProperties();
            for (String key : staging.stringPropertyNames()) {
                String relative = staging.getProperty(key);
                Path source = staged.resolve(relative).normalize();
                if (!Files.exists(source)) {
                    throw new IllegalStateException("Staged module " + relative + " does not exist for " + key);
                }
                Path file = emitted.get(source);
                if (file == null) {
                    Identity identity = identities.get(relative);
                    file = target.resolve(source.getFileName().toString());
                    if (emitted.containsValue(file)) {
                        throw new IllegalStateException("Module " + identity.module() + " is staged more than once");
                    }
                    if (relative.startsWith(JDeps.ANALYZED)) {
                        inject(source, file, synthesize(source, identity, descriptors, owners));
                    } else {
                        BuildStep.linkOrCopy(file, source);
                    }
                    emitted.put(source, file);
                }
                index.setProperty(key, context.next()
                        .relativize(file)
                        .toString()
                        .replace(File.separatorChar, '/'));
            }
            index.store(context.next().resolve(Dependencies.MODULAR));
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }

        private static SequencedMap<String, Identity> identities(Path staged) throws IOException {
            SequencedMap<String, Identity> identities = new LinkedHashMap<>();
            SequencedProperties properties = SequencedProperties.ofFiles(staged.resolve(IDENTITY));
            properties.forEachProperty((relative, value) -> {
                int tab = value.indexOf('\t');
                identities.put(relative, tab < 0
                        ? new Identity(value, null)
                        : new Identity(value.substring(0, tab), value.substring(tab + 1)));
            });
            return identities;
        }

        private static SequencedMap<String, String> owners(Path staged,
                                                           SequencedProperties staging,
                                                           SequencedMap<String, Identity> identities) {
            SequencedMap<String, String> owners = new LinkedHashMap<>();
            for (ModuleReference reference : ModuleFinder.ofSystem().findAll()) {
                for (String name : reference.descriptor().packages()) {
                    owners.putIfAbsent(name, reference.descriptor().name());
                }
            }
            SequencedMap<Path, String> jars = new LinkedHashMap<>();
            for (String key : staging.stringPropertyNames()) {
                String relative = staging.getProperty(key);
                jars.putIfAbsent(staged.resolve(relative).normalize(), identities.get(relative).module());
            }
            for (Map.Entry<Path, String> entry : jars.entrySet()) {
                Path jar = entry.getKey();
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(jar);
                boolean named = descriptor != null && !descriptor.isAutomatic();
                String module = named ? descriptor.name() : entry.getValue();
                for (String name : (named ? descriptor : derive(jar)).packages()) {
                    String previous = owners.putIfAbsent(name, module);
                    if (previous != null && !previous.equals(module)) {
                        throw new IllegalStateException("Package " + name
                                + " is split between " + previous + " and " + module
                                + ": a split package cannot be resolved on the module path, so the closure"
                                + " cannot be modularized until one of the two stops carrying it");
                    }
                }
            }
            return owners;
        }

        private static byte[] synthesize(Path jar,
                                         Identity identity,
                                         Path descriptors,
                                         SequencedMap<String, String> owners) throws IOException {
            String module = identity.module();
            ModuleDescriptor derived = derive(jar);
            SequencedSet<String> packages = new TreeSet<>(derived.packages());
            SequencedMap<String, Integer> requires = requires(module, descriptors, packages.isEmpty());
            SequencedSet<String> uses = new TreeSet<>();
            for (String service : services(jar)) {
                String owner = service.substring(0, Math.max(0, service.lastIndexOf('.')));
                if (packages.contains(owner) || owners.containsKey(owner)) {
                    uses.add(service);
                }
            }
            return ClassFile.of().buildModule(ModuleAttribute.of(ModuleDesc.of(module), builder -> {
                builder.moduleFlags(ClassFile.ACC_OPEN);
                if (identity.version() != null) {
                    builder.moduleVersion(identity.version());
                }
                builder.requires(ModuleDesc.of("java.base"), ClassFile.ACC_MANDATED, null);
                requires.forEach((name, flags) -> builder.requires(ModuleDesc.of(name), flags, null));
                packages.forEach(name -> builder.exports(PackageDesc.of(name), 0));
                uses.forEach(service -> builder.uses(ClassDesc.of(service)));
                for (ModuleDescriptor.Provides provides : derived.provides()) {
                    List<ClassDesc> providers = provides.providers().stream()
                            .filter(provider -> packages.contains(
                                    provider.substring(0, Math.max(0, provider.lastIndexOf('.')))))
                            .map(ClassDesc::of)
                            .toList();
                    if (!providers.isEmpty()) {
                        builder.provides(ClassDesc.of(provides.service()), providers.toArray(ClassDesc[]::new));
                    }
                }
            }), builder -> {
                builder.withVersion(ClassFile.JAVA_9_VERSION, 0);
                if (!packages.isEmpty()) {
                    builder.with(ModulePackagesAttribute.ofNames(packages.stream().map(PackageDesc::of).toList()));
                }
            });
        }

        private static SequencedMap<String, Integer> requires(String module,
                                                              Path descriptors,
                                                              boolean optional) throws IOException {
            Path source = null;
            Path candidate = descriptors.resolve(module);
            if (Files.isDirectory(candidate)) {
                try (Stream<Path> files = Files.walk(candidate)) {
                    source = files.filter(file -> file.getFileName().toString().equals("module-info.java"))
                            .sorted()
                            .findFirst()
                            .orElse(null);
                }
            }
            if (source == null) {
                if (optional) {
                    return Collections.emptyNavigableMap();
                }
                throw new IllegalStateException("No module descriptor was generated for " + module);
            }
            Path file = source;
            JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
            JavacTask javac = (JavacTask) compiler.getTask(new PrintWriter(Writer.nullWriter()),
                    compiler.getStandardFileManager(null, null, null),
                    null,
                    null,
                    null,
                    List.of(new SimpleJavaFileObject(file.toUri(), JavaFileObject.Kind.SOURCE) {
                        @Override
                        public CharSequence getCharContent(boolean ignoreEncodingErrors) throws IOException {
                            return Files.readString(file);
                        }
                    }));
            SequencedMap<String, Integer> requires = new LinkedHashMap<>();
            for (CompilationUnitTree unit : javac.parse()) {
                ModuleTree tree = unit.getModule();
                if (tree == null) {
                    continue;
                }
                for (DirectiveTree directive : tree.getDirectives()) {
                    if (directive instanceof RequiresTree required) {
                        String name = required.getModuleName().toString();
                        if (name.equals("java.base")) {
                            continue;
                        }
                        requires.put(name, (required.isTransitive() ? ClassFile.ACC_TRANSITIVE : 0)
                                | (required.isStatic() ? ClassFile.ACC_STATIC_PHASE : 0));
                    }
                }
            }
            return requires;
        }

        private static SequencedSet<String> services(Path jar) throws IOException {
            SequencedSet<String> services = new LinkedHashSet<>();
            try (JarFile file = new JarFile(jar.toFile(), false, ZipFile.OPEN_READ, JarFile.runtimeVersion())) {
                for (JarEntry entry : (Iterable<JarEntry>) file.versionedStream()::iterator) {
                    if (!entry.getName().endsWith(".class") || entry.getName().equals("module-info.class")) {
                        continue;
                    }
                    byte[] bytes;
                    try (InputStream input = file.getInputStream(entry)) {
                        bytes = input.readAllBytes();
                    }
                    ClassModel model;
                    try {
                        model = ClassFile.of().parse(bytes);
                    } catch (IllegalArgumentException _) {
                        continue;
                    }
                    boolean loads = false;
                    for (PoolEntry pooled : model.constantPool()) {
                        if (pooled instanceof MemberRefEntry reference
                                && reference.owner().asInternalName().equals("java/util/ServiceLoader")) {
                            loads = true;
                            break;
                        }
                    }
                    if (!loads) {
                        continue;
                    }
                    for (MethodModel method : model.methods()) {
                        method.code().ifPresent(code -> {
                            ClassDesc loaded = null;
                            for (CodeElement element : code) {
                                if (element instanceof ConstantInstruction constant
                                        && constant.constantValue() instanceof ClassDesc description) {
                                    loaded = description;
                                } else if (element instanceof InvokeInstruction invoke
                                        && invoke.owner().asInternalName().equals("java/util/ServiceLoader")
                                        && (invoke.name().stringValue().equals("load")
                                        || invoke.name().stringValue().equals("loadInstalled"))) {
                                    if (loaded != null && !loaded.isArray() && !loaded.isPrimitive()) {
                                        services.add(loaded.packageName().isEmpty()
                                                ? loaded.displayName()
                                                : loaded.packageName() + "." + loaded.displayName());
                                    }
                                    loaded = null;
                                }
                            }
                        });
                    }
                }
            }
            return services;
        }

        private void inject(Path source, Path target, byte[] descriptor) throws IOException {
            try (JarFile jar = new JarFile(source.toFile(), false, ZipFile.OPEN_READ)) {
                boolean signed = jar.stream().map(JarEntry::getName).anyMatch(Modularize::signature);
                try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target))) {
                    for (JarEntry entry : (Iterable<JarEntry>) jar.stream()::iterator) {
                        String name = entry.getName();
                        if (name.equals("module-info.class") || signature(name)) {
                            continue;
                        }
                        byte[] replacement = null;
                        if (signed && name.equalsIgnoreCase(JarFile.MANIFEST_NAME)) {
                            Manifest manifest;
                            try (InputStream input = jar.getInputStream(entry)) {
                                manifest = new Manifest(input);
                            }
                            manifest.getEntries().values().forEach(attributes -> attributes.keySet()
                                    .removeIf(attribute -> attribute.toString().endsWith("-Digest")
                                            || attribute.toString().endsWith("-Digest-Manifest")));
                            manifest.getEntries().values().removeIf(Attributes::isEmpty);
                            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                            manifest.write(buffer);
                            replacement = buffer.toByteArray();
                        }
                        JarEntry copy = new JarEntry(name);
                        if (timestamp == null) {
                            copy.setTime(entry.getTime());
                        } else {
                            copy.setTimeLocal(timestamp.toLocalDateTime());
                        }
                        out.putNextEntry(copy);
                        if (replacement != null) {
                            out.write(replacement);
                        } else if (!entry.isDirectory()) {
                            try (InputStream input = jar.getInputStream(entry)) {
                                input.transferTo(out);
                            }
                        }
                        out.closeEntry();
                    }
                    JarEntry entry = new JarEntry("module-info.class");
                    if (timestamp != null) {
                        entry.setTimeLocal(timestamp.toLocalDateTime());
                    }
                    out.putNextEntry(entry);
                    out.write(descriptor);
                    out.closeEntry();
                }
            }
        }

        private static boolean signature(String name) {
            if (!name.startsWith("META-INF/") || name.indexOf('/', "META-INF/".length()) >= 0) {
                return false;
            }
            String file = name.substring("META-INF/".length()).toUpperCase(Locale.ROOT);
            return file.endsWith(".SF")
                    || file.endsWith(".DSA")
                    || file.endsWith(".RSA")
                    || file.endsWith(".EC")
                    || file.startsWith("SIG-");
        }
    }

    public enum Mode {

        DECLARED, SYNTHETIC
    }
}

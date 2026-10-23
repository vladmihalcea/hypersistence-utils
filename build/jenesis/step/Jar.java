package build.jenesis.step;

import module java.base;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import java.util.jar.Attributes;

public class Jar extends ProcessBuildStep {

    private static final Attributes.Name CREATED_BY = new Attributes.Name("Created-By");

    private final Sort sort;
    private final OffsetDateTime timestamp;

    public Jar(ProcessHandler.Factory factory,
               Sort sort) {
        this(factory.apply("jar", "bin/jar"),
             sort,
             BuildStep.timestamp(),
             new Terms());
    }

    public static Jar ofEnvironment(Environment environment,
                             ProcessHandler.Factory factory,
                             Sort sort) {
        return new Jar(factory.apply("jar", "bin/jar"),
                sort,
                BuildStep.timestamp(environment),
                Terms.ofEnvironment(environment, "jar"));
    }

    private Jar(Function<List<String>, ? extends ProcessHandler> factory,
                Sort sort,
                OffsetDateTime timestamp,
                Terms terms) {
        super("jar", factory, terms);
        this.sort = sort;
        this.timestamp = timestamp;
    }

    public Jar verbose(BiConsumer<Boolean, String> printing) {
        return new Jar(factory, sort, timestamp, terms.printing(printing));
    }

    public Jar timestamp(OffsetDateTime timestamp) {
        return new Jar(factory, sort, timestamp, terms);
    }

    @Override
    protected List<String> configurations() {
        return sort == Sort.CLASSES ? super.configurations() : List.of();
    }

    @Override
    public CompletionStage<List<String>> process(Executor executor,
                                                 BuildStepContext context,
                                                 SequencedMap<String, BuildStepArgument> arguments,
                                                 SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        for (SequencedMap<String, String> configured : properties.values()) {
            for (String option : List.of("--manifest", "-m")) {
                if (configured.containsKey(option)) {
                    throw new IllegalArgumentException("process-jar.properties sets " + option + ", but the jar step"
                            + " writes the manifest itself - place the attributes in a META-INF/MANIFEST.MF among"
                            + " the module's resources instead, which is the basis of the jar's manifest");
                }
            }
        }
        if (sort == Sort.CLASSES) {
            String mainClass = properties.values().stream()
                    .map(configured -> configured.getOrDefault("--main-class", configured.get("-e")))
                    .filter(Objects::nonNull)
                    .findFirst()
                    .orElse(null);
            if (mainClass != null) {
                requireMain(mainClass, arguments);
            }
        }
        List<String> commands = new ArrayList<>(List.of(
                "--create",
                "--file",
                Files.createDirectory(context.next().resolve(sort.folder))
                        .resolve(sort.file(arguments))
                        .toString()));
        if (timestamp != null) {
            commands.add("--date=" + timestamp);
        }
        List<Path> manifestFiles = new ArrayList<>();
        for (BuildStepArgument argument : sort == Sort.CLASSES ? arguments.values() : List.<BuildStepArgument>of()) {
            if (argument.removed()) {
                continue;
            }
            for (String name : sort.folders) {
                Path candidate = argument.folder().resolve(name).resolve(JarFile.MANIFEST_NAME);
                if (Files.isRegularFile(candidate)) {
                    manifestFiles.add(candidate);
                }
            }
        }
        for (BuildStepArgument argument : sort == Sort.CLASSES ? arguments.values() : List.<BuildStepArgument>of()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(Versions.MANIFEST);
            if (Files.exists(candidate)) {
                manifestFiles.add(candidate);
            }
        }
        Manifest merged = new Manifest();
        for (Path path : manifestFiles) {
            Manifest current;
            try (InputStream in = Files.newInputStream(path)) {
                current = new Manifest(in);
            }
            mergeAttributes(merged.getMainAttributes(), current.getMainAttributes(), path);
            for (Map.Entry<String, Attributes> entry : current.getEntries().entrySet()) {
                Attributes target = merged.getEntries().computeIfAbsent(entry.getKey(), _ -> new Attributes());
                mergeAttributes(target, entry.getValue(), path);
            }
        }
        merged.getMainAttributes().putIfAbsent(Attributes.Name.MANIFEST_VERSION, "1.0");
        merged.getMainAttributes().putIfAbsent(CREATED_BY, "Jenesis");
        SequencedProperties metadata = SequencedProperties.ofFolders(arguments.values().stream()
                .filter(argument -> !argument.removed())
                .map(BuildStepArgument::folder)
                .toList(), METADATA);
        String title = metadata.value("name", metadata.value("artifact")),
                version = metadata.value("version"),
                vendor = metadata.value("organization.name");
        if (title != null) {
            merged.getMainAttributes().putIfAbsent(Attributes.Name.IMPLEMENTATION_TITLE, title);
        }
        if (version != null) {
            merged.getMainAttributes().putIfAbsent(Attributes.Name.IMPLEMENTATION_VERSION, version);
        }
        if (vendor != null) {
            merged.getMainAttributes().putIfAbsent(Attributes.Name.IMPLEMENTATION_VENDOR, vendor);
        }
        Path output = context.supplement().resolve(Versions.MANIFEST);
        try (OutputStream out = Files.newOutputStream(output)) {
            merged.write(out);
        }
        commands.add("--manifest");
        commands.add(output.toString());
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (String name : sort.folders) {
                Path folder = argument.folder().resolve(name);
                if (!Files.exists(folder)) {
                    continue;
                }
                Path metaInf = folder.resolve("META-INF");
                if (!Files.isDirectory(metaInf.resolve("build.jenesis"))) {
                    commands.add("-C");
                    commands.add(folder.toString());
                    commands.add(".");
                    continue;
                }
                List<Path> entries = new ArrayList<>();
                try (Stream<Path> files = Files.list(folder)) {
                    files.filter(file -> !file.equals(metaInf)).forEach(entries::add);
                }
                try (Stream<Path> files = Files.list(metaInf)) {
                    files.filter(file -> !BuildStep.underBuildJenesis(folder.relativize(file))).forEach(entries::add);
                }
                entries.sort(null);
                for (Path entry : entries) {
                    commands.add("-C");
                    commands.add(folder.toString());
                    commands.add(folder.relativize(entry).toString());
                }
            }
        }
        return CompletableFuture.completedStage(commands);
    }

    private static void requireMain(String mainClass, SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        List<Path> folders = new ArrayList<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (!argument.removed() && Files.isDirectory(argument.folder().resolve(CLASSES))) {
                folders.add(argument.folder().resolve(CLASSES));
            }
        }
        List<Path> jars = new ArrayList<>(Dependencies.paths(arguments, "main", "compile"));
        jars.addAll(Dependencies.paths(arguments, "main", "runtime"));
        ClassModel type = find(folders, jars, mainClass.replace('.', '/'));
        if (type == null) {
            throw new IllegalArgumentException("The main class " + mainClass + " is neither among the classes"
                    + " of the module nor in its dependencies: name a class that declares or inherits a main"
                    + " method, by its binary name, with @jenesis.main in module-info.java or with a"
                    + " <mainClass> property in pom.xml");
        }
        List<ClassModel> hierarchy = new ArrayList<>(List.of(type));
        Set<String> seen = new HashSet<>(Set.of(type.thisClass().asInternalName()));
        for (int index = 0; index < hierarchy.size(); index++) {
            ClassModel current = hierarchy.get(index);
            List<ClassEntry> supertypes = new ArrayList<>();
            current.superclass().ifPresent(supertypes::add);
            supertypes.addAll(current.interfaces());
            for (ClassEntry supertype : supertypes) {
                if (seen.add(supertype.asInternalName())) {
                    ClassModel parent = find(folders, jars, supertype.asInternalName());
                    if (parent == null) {
                        return;
                    }
                    hierarchy.add(parent);
                }
            }
        }
        hierarchy.sort(Comparator.comparing(declaring -> (declaring.flags().flagsMask() & ClassFile.ACC_INTERFACE) != 0
                && declaring != type));
        for (String descriptor : List.of("([Ljava/lang/String;)V", "()V")) {
            MethodModel main = null;
            for (ClassModel declaring : hierarchy) {
                for (MethodModel method : declaring.methods()) {
                    if (main == null
                            && method.methodName().equalsString("main")
                            && method.methodType().equalsString(descriptor)
                            && (declaring == type
                            || (declaring.flags().flagsMask() & ClassFile.ACC_INTERFACE) == 0
                            || (method.flags().flagsMask() & ClassFile.ACC_STATIC) == 0)) {
                        main = method;
                    }
                }
            }
            if (main == null || (main.flags().flagsMask() & ClassFile.ACC_PRIVATE) != 0) {
                continue;
            } else if ((main.flags().flagsMask() & ClassFile.ACC_STATIC) != 0) {
                return;
            } else if ((type.flags().flagsMask() & (ClassFile.ACC_ABSTRACT | ClassFile.ACC_INTERFACE)) != 0) {
                throw new IllegalArgumentException("The main class " + mainClass + " is abstract or an interface"
                        + " but its main method is not static: make the class concrete, or the method static");
            } else if (type.methods().stream().noneMatch(method -> method.methodName().equalsString("<init>")
                    && method.methodType().equalsString("()V")
                    && (method.flags().flagsMask() & ClassFile.ACC_PRIVATE) == 0)) {
                throw new IllegalArgumentException("The main class " + mainClass + " has an instance main method"
                        + " but no non-private constructor without parameters: declare one, declare the class"
                        + " static if it is an inner class, or make the main method static");
            }
            return;
        }
        throw new IllegalArgumentException("The main class " + mainClass + " declares or inherits no main"
                + " method that java runs: declare a non-private void main(String[] args) or void main(),"
                + " static or not");
    }

    private static ClassModel find(List<Path> folders, List<Path> jars, String name) throws IOException {
        String file = name + ".class";
        for (Path folder : folders) {
            Path candidate = folder.resolve(file);
            if (Files.isRegularFile(candidate)) {
                return ClassFile.of().parse(candidate);
            }
        }
        try (InputStream in = ClassLoader.getPlatformClassLoader().getResourceAsStream(file)) {
            if (in != null) {
                return ClassFile.of().parse(in.readAllBytes());
            }
        }
        for (Path jar : jars) {
            try (JarFile archive = new JarFile(jar.toFile())) {
                JarEntry entry = archive.getJarEntry(file);
                if (entry != null) {
                    try (InputStream in = archive.getInputStream(entry)) {
                        return ClassFile.of().parse(in.readAllBytes());
                    }
                }
            }
        }
        return null;
    }

    private static void mergeAttributes(Attributes target, Attributes source, Path file) {
        for (Map.Entry<Object, Object> entry : source.entrySet()) {
            Object key = entry.getKey(), value = entry.getValue(), existing = target.get(key);
            if (existing == null) {
                target.put(key, value);
            } else if (!existing.equals(value)) {
                throw new IllegalStateException("Conflicting manifest attribute '"
                        + key
                        + "' in "
                        + file
                        + ": '"
                        + existing
                        + "' vs '"
                        + value
                        + "'");
            }
        }
    }

    public enum Sort {

        CLASSES("classes", "", BuildStep.ARTIFACTS, BuildStep.CLASSES, BuildStep.RESOURCES),
        SOURCES("sources", "-sources", BuildStep.SOURCES, BuildStep.SOURCES, BuildStep.RESOURCES),
        JAVADOC("javadoc", "-javadoc", BuildStep.DOCUMENTATION, Javadoc.JAVADOC);

        final String kind;
        final String suffix;
        final String folder;
        final List<String> folders;

        Sort(String kind, String suffix, String folder, String... folders) {
            this.kind = kind;
            this.suffix = suffix;
            this.folder = folder;
            this.folders = List.of(folders);
        }

        public String file(SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            SequencedProperties module = null, metadata = null;
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                Path moduleFile = argument.folder().resolve(BuildStep.MODULE),
                        metadataFile = argument.folder().resolve(BuildStep.METADATA);
                if (module == null && Files.isRegularFile(moduleFile)) {
                    module = SequencedProperties.ofFiles(moduleFile);
                }
                if (metadata == null && Files.isRegularFile(metadataFile)) {
                    metadata = SequencedProperties.ofFiles(metadataFile);
                }
            }
            String version = metadata == null
                    ? null
                    : metadata.value("version", metadata.value("project") == null ? null : "0-SNAPSHOT");
            if (module != null && module.flag("modular") && module.value("module") != null) {
                return BuildExecutorModule.encode(module.value("module"))
                        + (version == null ? "" : "-" + BuildExecutorModule.encode(version)) + suffix + ".jar";
            }
            String artifact = metadata == null ? null : metadata.value("artifact");
            if (artifact == null) {
                return kind + ".jar";
            }
            String group = metadata.value("project");
            return PathPlacement.stem((group == null ? "" : group + "/") + artifact + (version == null ? "" : "/" + version))
                    + (module != null && module.value("test") != null ? "-tests" : "") + suffix + ".jar";
        }
    }
}

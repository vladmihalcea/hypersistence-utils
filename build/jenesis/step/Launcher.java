package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;
import java.util.jar.Attributes;

public class Launcher implements BuildStep {

    public static final String LAUNCHER = "launcher/";
    private static final String MAIN_CLASS = "build.jenesis.launcher.Launcher",
            LAUNCHER_PREFIX = "build/jenesis/launcher/",
            DESCRIPTOR = "META-INF/jenesis/application.properties",
            MINIMUM = "0.6.0";

    private final String tool;
    private final String group;
    private final PathPlacement pathPlacement;
    private final OffsetDateTime timestamp;
    private final transient Consumer<String> out;
    private final transient Palette palette;

    public Launcher(String tool,
                    PathPlacement pathPlacement) {
        this(tool,
             "main",
             pathPlacement,
             BuildStep.timestamp(),
             null,
             Palette.NONE);
    }

    public static Launcher ofEnvironment(Environment environment,
                                         String tool,
                                         PathPlacement pathPlacement) {
        return new Launcher(tool, pathPlacement)
                .timestamp(BuildStep.timestamp(environment))
                .printing(environment.out(), Palette.ofEnvironment(environment));
    }

    private Launcher(String tool,
                     String group,
                     PathPlacement pathPlacement,
                     OffsetDateTime timestamp,
                     Consumer<String> out,
                     Palette palette) {
        this.tool = tool;
        this.group = group;
        this.pathPlacement = pathPlacement;
        this.timestamp = timestamp;
        this.out = out;
        this.palette = palette;
    }

    public Launcher tool(String tool) {
        return new Launcher(tool, group, pathPlacement, timestamp, out, palette);
    }

    public Launcher group(String group) {
        return new Launcher(tool, group, pathPlacement, timestamp, out, palette);
    }

    public Launcher pathPlacement(PathPlacement pathPlacement) {
        return new Launcher(tool, group, pathPlacement, timestamp, out, palette);
    }

    public Launcher timestamp(OffsetDateTime timestamp) {
        return new Launcher(tool, group, pathPlacement, timestamp, out, palette);
    }

    public Launcher printing(Consumer<String> out, Palette palette) {
        return new Launcher(tool, group, pathPlacement, timestamp, out, palette);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String mainClass = null, mainModule = null, name = null, location = null;
        Path shaded = null, sbom = null;
        Manifest fragment = null;
        SequencedMap<String, Path> jars = new TreeMap<>();
        SequencedSet<Path> granted = new LinkedHashSet<>();
        SequencedMap<String, SequencedSet<String>> access = new LinkedHashMap<>();
        SequencedSet<String> dropped = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path process = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, "java");
            if (process != null) {
                SequencedProperties.ofFiles(process).forEachProperty((option, values) -> {
                    String key = switch (option) {
                        case "--add-reads" -> "addReads";
                        case "--add-exports" -> "addExports";
                        case "--add-opens" -> "addOpens";
                        case "--enable-native-access" -> "enableNativeAccess";
                        default -> null;
                    };
                    for (String value : values.split("\n")) {
                        if (key == null || value.isBlank()) {
                            dropped.add(option);
                        } else {
                            access.computeIfAbsent(key, _ -> new LinkedHashSet<>()).add(value.strip());
                        }
                    }
                });
            }
            Path properties = argument.folder().resolve("launcher.properties");
            if (Files.isRegularFile(properties)) {
                SequencedProperties application = SequencedProperties.ofFiles(properties);
                if (mainClass == null) {
                    mainClass = application.getProperty("mainClass");
                }
                if (mainModule == null) {
                    mainModule = application.getProperty("mainModule");
                }
                if (name == null) {
                    name = application.getProperty("name");
                }
            }
            Path manifest = argument.folder().resolve(Versions.MANIFEST);
            if (sbom == null && Files.isRegularFile(manifest)) {
                try (InputStream in = Files.newInputStream(manifest)) {
                    fragment = new Manifest(in);
                }
                location = fragment.getMainAttributes().getValue("Sbom-Location");
                if (location != null && Files.isRegularFile(argument.folder().resolve(RESOURCES).resolve(location))) {
                    sbom = argument.folder().resolve(RESOURCES).resolve(location);
                }
            }
            for (Path file : Dependencies.select(argument.folder(), tool, "runtime")) {
                shaded = file;
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        jars.putIfAbsent(file.getFileName().toString(), file);
                    }
                }
            }
            for (Path file : Dependencies.select(argument.folder(), group, "runtime")) {
                jars.putIfAbsent(file.getFileName().toString(), file);
            }
            granted.addAll(Inventory.nativeAccess(argument.folder()));
        }
        if (mainClass == null && out != null) {
            out.accept(("%s%-11s%s %s builds no executable jar, as it names no main class: name one with"
                    + " @jenesis.main <class> in its module-info.java, or with a <mainClass> property in its pom.xml")
                    .formatted(palette.warning(),
                            "[SKIPPED]",
                            palette.reset(),
                            name == null ? "The module" : name));
        }
        if (mainClass == null || shaded == null || jars.isEmpty()) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        ModuleDescriptor.Version version = Optional.ofNullable(PathPlacement.moduleDescriptor(shaded))
                .flatMap(ModuleDescriptor::version)
                .orElse(null);
        if (version == null || version.compareTo(ModuleDescriptor.Version.parse(MINIMUM)) < 0) {
            throw new IllegalStateException("An executable jar needs build.jenesis.launcher " + MINIMUM
                    + " or later, the first to read the descriptor from " + DESCRIPTOR + " where this build writes it,"
                    + " but the " + tool + " group resolved "
                    + (version == null ? shaded.getFileName() + ", which declares no module version" : "version " + version)
                    + ": pin " + tool + "/maven/build.jenesis/build.jenesis.launcher to " + MINIMUM
                    + " or later, or resolve it from a repository that serves one");
        }
        if (!dropped.isEmpty() && out != null) {
            out.accept(("%s%-11s%s %s carries no %s of process-java.properties, as those configure the JVM that"
                    + " java -jar starts: pass them to that command, or package with bundle=true, whose argument"
                    + " file carries them")
                    .formatted(palette.warning(),
                            "[OPTIONS]",
                            palette.reset(),
                            name == null ? "The executable jar" : name + ".jar",
                            String.join(", ", dropped)));
        }
        boolean unnamedNativeAccess = false;
        SequencedSet<String> nativeAccess = new LinkedHashSet<>();
        for (String modules : access.getOrDefault("enableNativeAccess", new LinkedHashSet<>())) {
            for (String module : modules.split(",")) {
                if (module.strip().equals("ALL-UNNAMED")) {
                    unnamedNativeAccess = true;
                } else if (!module.isBlank()) {
                    nativeAccess.add(module.strip());
                }
            }
        }
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            layers.putAll(Layers.membership(argument.folder()));
        }
        SequencedSet<String> named = new LinkedHashSet<>();
        layers.values().forEach(membership -> named.addAll(membership.all()));
        SequencedMap<String, Path> isolated = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path file : Dependencies.all(argument.folder())) {
                if (named.contains(file.getFileName().toString())) {
                    isolated.putIfAbsent(file.getFileName().toString(), file);
                }
            }
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            boolean onModulePath = mainModule != null && pathPlacement.test(entry.getValue());
            (onModulePath ? modulepath : classpath).put(entry.getKey(), entry.getValue());
            if (onModulePath && granted.contains(entry.getValue().toAbsolutePath().normalize())) {
                ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(entry.getValue());
                if (descriptor == null) {
                    throw new IllegalStateException("Cannot grant native access to "
                            + entry.getKey()
                            + ", which is placed on the module path but describes no module");
                }
                nativeAccess.add(descriptor.name());
            }
        }
        SequencedProperties application = new SequencedProperties();
        application.setProperty("mainClass", mainClass);
        if (mainModule != null) {
            application.setProperty("mainModule", mainModule);
        }
        application.setProperty("classpath", String.join(",", classpath.sequencedKeySet()));
        application.setProperty("modulepath", String.join(",", modulepath.sequencedKeySet()));
        if (!nativeAccess.isEmpty()) {
            application.setProperty("enableNativeAccess", String.join(",", nativeAccess));
        }
        for (String key : List.of("addReads", "addExports", "addOpens")) {
            if (access.containsKey(key)) {
                application.setProperty(key, String.join(";", access.get(key)));
            }
        }
        for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
            application.setProperty("modulepath." + layer.getKey(),
                    String.join(",", layer.getValue().modulepath()));
            if (!layer.getValue().classpath().isEmpty()) {
                application.setProperty("classpath." + layer.getKey(),
                        String.join(",", layer.getValue().classpath()));
            }
            SequencedSet<String> modules = new LinkedHashSet<>();
            for (Map.Entry<Path, Boolean> member : layer.getValue().nativeAccess(isolated, granted).entrySet()) {
                if (member.getValue()) {
                    ModuleDescriptor descriptor = PathPlacement.moduleDescriptor(member.getKey());
                    if (descriptor == null) {
                        throw new IllegalStateException("Cannot grant native access to "
                                + member.getKey().getFileName()
                                + " in layer "
                                + layer.getKey()
                                + ", which is placed on its module path but describes no module");
                    }
                    modules.add(descriptor.name());
                }
            }
            if (!modules.isEmpty()) {
                application.setProperty("enableNativeAccess." + layer.getKey(), String.join(",", modules));
            }
        }
        Path descriptor = context.supplement().resolve("application.properties");
        application.store(descriptor);
        SequencedMap<String, Path> stored = new TreeMap<>(classpath);
        stored.putAll(modulepath);
        for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
            for (String member : layer.getValue().all()) {
                Path file = isolated.get(member);
                if (file == null) {
                    throw new IllegalStateException("Layer " + layer.getKey() + " names " + member
                            + ", which was not resolved for this application");
                }
                stored.putIfAbsent(member, file);
            }
        }
        Manifest manifest = new Manifest();
        if (sbom != null) {
            manifest.getMainAttributes().putAll(fragment.getMainAttributes());
        }
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, MAIN_CLASS);
        if (unnamedNativeAccess
                || jars.values().stream().anyMatch(jar -> granted.contains(jar.toAbsolutePath().normalize()))) {
            manifest.getMainAttributes().putValue("Enable-Native-Access", "ALL-UNNAMED");
        }
        Path jar = Files.createDirectory(context.next().resolve(LAUNCHER))
                .resolve((name == null ? "application" : name) + ".jar");
        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(jar))) {
            writeManifest(out, manifest);
            explode(out, shaded, "", entry -> entry.startsWith(LAUNCHER_PREFIX) && entry.endsWith(".class")
                    || entry.equals("META-INF/LICENSE")
                    || entry.equals("META-INF/NOTICE"));
            writeEntry(out, DESCRIPTOR, descriptor);
            if (sbom != null) {
                writeEntry(out, location, sbom);
            }
            for (Map.Entry<String, Path> entry : stored.entrySet()) {
                explode(out, entry.getValue(), "jars/" + entry.getKey() + "/", _ -> true);
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private void explode(JarOutputStream out, Path file, String prefix, Predicate<String> include)
            throws IOException {
        try (JarFile jar = new JarFile(file.toFile())) {
            for (JarEntry entry : (Iterable<JarEntry>) jar.stream()::iterator) {
                if (!include.test(entry.getName())) {
                    continue;
                }
                JarEntry copy = new JarEntry(prefix + entry.getName());
                if (timestamp == null) {
                    copy.setTime(entry.getTime());
                } else {
                    copy.setTimeLocal(timestamp.toLocalDateTime());
                }
                out.putNextEntry(copy);
                try (InputStream in = jar.getInputStream(entry)) {
                    in.transferTo(out);
                }
                out.closeEntry();
            }
        }
    }

    private void writeManifest(JarOutputStream out, Manifest manifest) throws IOException {
        JarEntry entry = new JarEntry(JarFile.MANIFEST_NAME);
        if (timestamp != null) {
            entry.setTimeLocal(timestamp.toLocalDateTime());
        }
        out.putNextEntry(entry);
        manifest.write(out);
        out.closeEntry();
    }

    private void writeEntry(JarOutputStream out, String name, Path file) throws IOException {
        JarEntry entry = new JarEntry(name);
        if (timestamp == null) {
            entry.setTime(Files.getLastModifiedTime(file).toMillis());
        } else {
            entry.setTimeLocal(timestamp.toLocalDateTime());
        }
        out.putNextEntry(entry);
        Files.copy(file, out);
        out.closeEntry();
    }
}

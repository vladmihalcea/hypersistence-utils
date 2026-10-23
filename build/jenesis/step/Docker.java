package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.License;
import build.jenesis.ModuleGraph;
import build.jenesis.PathPlacement;
import build.jenesis.SequencedProperties;

public class Docker implements BuildStep {

    public static final String DOCKER = "docker/";
    public static final Set<String> FORMATS = Set.of("app-image", "deb", "rpm");
    private static final String MODULE_PATH = "/app/extensions/modulepath",
            CLASS_PATH = "/app/extensions/classpath/*",
            ANNOTATION = "org.opencontainers.image.";
    private static final long serialVersionUID = 1L;

    private final String from;
    private final String group;
    private final SequencedMap<String, String> labels;
    private final OffsetDateTime created;
    private final String jpackage;

    public Docker(String from) {
        this(from,
             "main",
             Collections.emptyNavigableMap(),
             null,
             null);
    }

    public static Docker ofEnvironment(Environment environment, String from) {
        return new Docker(from,
                "main",
                Collections.emptyNavigableMap(),
                environment.value("archive.timestamp") == null ? null : BuildStep.timestamp(environment),
                null);
    }

    private Docker(String from,
                   String group,
                   SequencedMap<String, String> labels,
                   OffsetDateTime created,
                   String jpackage) {
        this.from = from;
        this.group = group;
        this.labels = labels;
        this.created = created;
        this.jpackage = jpackage;
    }

    public Docker group(String group) {
        return new Docker(from, group, labels, created, jpackage);
    }

    public Docker labels(SequencedMap<String, String> labels) {
        return new Docker(from, group, new LinkedHashMap<>(labels), created, jpackage);
    }

    public Docker created(OffsetDateTime created) {
        return new Docker(from, group, labels, created, jpackage);
    }

    public Docker jpackage(String jpackage) {
        if (jpackage != null && !FORMATS.contains(jpackage)) {
            throw new IllegalArgumentException("A Docker image cannot run a jpackage " + jpackage
                    + " package - name one of " + new TreeSet<>(FORMATS));
        }
        return new Docker(from, group, labels, created, jpackage);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Path folder = context.next().resolve(DOCKER);
        String instructions = jpackage == null ? launched(folder, arguments) : packaged(folder, jpackage, arguments);
        if (instructions == null) {
            return CompletableFuture.completedStage(new BuildStepResult(true));
        }
        List<Path> folders = arguments.values().stream()
                .filter(argument -> !argument.removed())
                .map(BuildStepArgument::folder)
                .toList();
        SequencedProperties metadata = SequencedProperties.ofFolders(folders, METADATA);
        SequencedMap<String, String> annotations = new LinkedHashMap<>();
        annotations.put(ANNOTATION + "base.name", from);
        annotations.put(ANNOTATION + "base.digest", null);
        annotations.put(ANNOTATION + "title", metadata.value("name", metadata.value("artifact")));
        annotations.put(ANNOTATION + "description", metadata.value("description"));
        annotations.put(ANNOTATION + "version", metadata.value("version"));
        annotations.put(ANNOTATION + "created", created == null ? null : DateTimeFormatter.ISO_INSTANT.format(created));
        annotations.put(ANNOTATION + "authors", null);
        annotations.put(ANNOTATION + "url", metadata.value("url"));
        annotations.put(ANNOTATION + "documentation", null);
        annotations.put(ANNOTATION + "source", metadata.value("scm.url"));
        annotations.put(ANNOTATION + "revision", metadata.value("scm.revision"));
        annotations.put(ANNOTATION + "vendor", metadata.value("organization.name"));
        annotations.put(ANNOTATION + "licenses", null);
        annotations.put(ANNOTATION + "ref.name", null);
        SequencedSet<String> developers = new LinkedHashSet<>(), licenses = new LinkedHashSet<>();
        for (String key : metadata.stringPropertyNames()) {
            if (key.startsWith("developer.") && key.lastIndexOf('.') > "developer.".length()) {
                developers.add(key.substring(0, key.lastIndexOf('.') + 1));
            } else if (key.startsWith("license.") && key.lastIndexOf('.') > "license.".length()) {
                licenses.add(key.substring(0, key.lastIndexOf('.') + 1));
            }
        }
        List<String> authors = new ArrayList<>();
        for (String developer : developers) {
            String name = metadata.value(developer + "name"), email = metadata.value(developer + "email");
            if (name != null || email != null) {
                authors.add(name == null ? "<" + email + ">" : email == null ? name : name + " <" + email + ">");
            }
        }
        annotations.put(ANNOTATION + "authors", authors.isEmpty() ? null : String.join(", ", authors));
        Map<String, String> aliases = Dependencies.aliases(folders);
        List<String> identifiers = licenses.stream()
                .map(license -> new License(null, null, metadata.value(license + "name"), metadata.value(license + "url"))
                        .identified(aliases)
                        .id())
                .toList();
        annotations.put(ANNOTATION + "licenses", identifiers.isEmpty() || identifiers.contains(null)
                ? null
                : String.join(" OR ", identifiers));
        annotations.putAll(labels);
        annotations.replaceAll((_, value) -> value == null ? "" : value);
        StringBuilder dockerfile = new StringBuilder("FROM ").append(from);
        String separator = "\nLABEL ";
        for (Map.Entry<String, String> annotation : annotations.entrySet()) {
            dockerfile.append(separator).append(label(annotation.getKey())).append('=').append(label(annotation.getValue()));
            separator = " \\\n      ";
        }
        Files.writeString(folder.resolve("Dockerfile"), dockerfile.append('\n').append(instructions).toString());
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }

    private static String packaged(Path folder,
                                   String format,
                                   SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        Path packages = null;
        String name = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path candidate = argument.folder().resolve(JPackage.PACKAGES);
            if (packages == null && Files.isDirectory(candidate)) {
                packages = candidate;
            }
            for (String configuration : List.of("jpackage-" + format, "jpackage")) {
                Path file = ProcessBuildStep.locate(argument.folder(), ProcessBuildStep.PROCESS, configuration);
                if (name == null && file != null) {
                    name = SequencedProperties.ofFiles(file).value("--name");
                }
            }
        }
        if (packages == null) {
            return null;
        }
        SequencedSet<String> written = new TreeSet<>(), matched = new TreeSet<>();
        try (DirectoryStream<Path> files = Files.newDirectoryStream(packages)) {
            for (Path file : files) {
                String candidate = file.getFileName().toString();
                written.add(candidate);
                if (format.equals("app-image")
                        ? Files.isRegularFile(file.resolve("bin").resolve(candidate)) && Files.isDirectory(file.resolve("lib").resolve("runtime"))
                        : Files.isRegularFile(file) && candidate.endsWith("." + format)) {
                    matched.add(candidate);
                }
            }
        }
        if (matched.isEmpty()) {
            throw new IllegalStateException("A Docker image of a jpackage " + format + " needs one built for Linux, but jpackage wrote "
                    + written + " - jpackage packages for the platform it runs on, so build on Linux,"
                    + " for instance with -Djenesis.project.docker=true");
        } else if (matched.size() > 1) {
            throw new IllegalStateException("A Docker image holds one jpackage application, but jpackage wrote "
                    + matched + " - give the image one application to run");
        }
        String file = matched.getFirst();
        if (format.equals("app-image")) {
            Path image = packages.resolve(file), target = Files.createDirectories(folder.resolve(file));
            Files.walkFileTree(image, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                    Files.createDirectories(target.resolve(image.relativize(directory).toString()));
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                    BuildStep.linkOrCopy(target.resolve(image.relativize(file).toString()), file);
                    return FileVisitResult.CONTINUE;
                }
            });
            return "COPY [" + quoted(List.of(file + "/", "/app/")) + "]\nWORKDIR /app\n"
                    + "ENTRYPOINT [" + quoted(List.of("/app/bin/" + file)) + "]\n";
        }
        if (name == null) {
            throw new IllegalStateException("The jpackage " + format + " package " + file + " is installed into the Docker image,"
                    + " but no --name tells which launcher to start - set it in process-jpackage.properties");
        }
        BuildStep.linkOrCopy(Files.createDirectories(folder).resolve(file), packages.resolve(file));
        String installed = "/tmp/jpackage/" + shell(file);
        return "WORKDIR /app\nCOPY [" + quoted(List.of(file, "/tmp/jpackage/")) + "]\nRUN " + switch (format) {
            case "deb" -> "apt-get update \\\n"
                    + "    && apt-get install -y --no-install-recommends " + installed + " \\\n"
                    + "    && package=\"$(dpkg-deb --field " + installed + " Package)\" \\\n"
                    + "    && rm -rf /tmp/jpackage /var/lib/apt/lists/* \\\n"
                    + "    && dpkg --listfiles \"$package\"";
            case "rpm" -> "package=\"$(rpm --query --package --queryformat '%{NAME}' " + installed + ")\" \\\n"
                    + "    && if command -v dnf > /dev/null; then dnf install -y " + installed + " && dnf clean all; \\\n"
                    + "       elif command -v yum > /dev/null; then yum install -y " + installed + " && yum clean all; \\\n"
                    + "       elif command -v zypper > /dev/null; then zypper --non-interactive install --allow-unsigned-rpm "
                    + installed + " && zypper clean --all; \\\n"
                    + "       else rpm --install " + installed + "; fi \\\n"
                    + "    && rm -rf /tmp/jpackage \\\n"
                    + "    && rpm --query --list \"$package\"";
            default -> throw new IllegalStateException("Unexpected jpackage format: " + format);
        } + " | while IFS= read -r file; do case \"$file\" in */lib/app/" + shell(name) + ".cfg) \\\n"
                + "       ln -s \"${file%/lib/app/*}/bin/\"" + shell(name) + " /app/launcher;; esac; done \\\n"
                + "    && test -x /app/launcher\n"
                + "ENTRYPOINT [" + quoted(List.of("/app/launcher")) + "]\n";
    }

    private String launched(Path folder, SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        String mainClass = null, mainModule = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path properties = argument.folder().resolve("launcher.properties");
            if (!Files.isRegularFile(properties)) {
                continue;
            }
            SequencedProperties launcher = SequencedProperties.ofFiles(properties);
            if (mainClass == null) {
                mainClass = launcher.getProperty("mainClass");
            }
            if (mainModule == null) {
                mainModule = launcher.getProperty("mainModule");
            }
        }
        if (mainClass == null) {
            return null;
        }
        SequencedMap<String, Path> jars = new TreeMap<>();
        SequencedSet<Path> granted = new LinkedHashSet<>();
        SequencedMap<String, Layers.Membership> layers = new TreeMap<>();
        SequencedMap<String, String> agents = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(artifacts)) {
                    for (Path file : files) {
                        jars.putIfAbsent(file.getFileName().toString(), file);
                    }
                }
            }
            for (String scope : List.of("runtime", "agent")) {
                for (Path file : Dependencies.select(argument.folder(), group, scope)) {
                    jars.putIfAbsent(file.getFileName().toString(), file);
                }
            }
            granted.addAll(Inventory.nativeAccess(argument.folder()));
            layers.putAll(Layers.membership(argument.folder()));
            agents.putAll(Inventory.agents(argument.folder()));
        }
        if (jars.isEmpty()) {
            return null;
        }
        SequencedMap<String, Path> classpath = new LinkedHashMap<>(), modulepath = new LinkedHashMap<>();
        ModuleGraph graph = new ModuleGraph();
        for (Map.Entry<String, Path> entry : jars.entrySet()) {
            boolean placed = graph.place(mainModule == null ? PathPlacement.CLASS_PATH : PathPlacement.INFERRED, entry.getValue());
            (placed ? modulepath : classpath).put(entry.getKey(), entry.getValue());
            if (granted.contains(entry.getValue().toAbsolutePath().normalize())) {
                graph.enableNativeAccess(entry.getValue(), placed);
            }
        }
        if (layers.values().stream().anyMatch(membership -> !membership.classpath().isEmpty())) {
            graph.unnamed();
        }
        SequencedMap<String, Path> stored = new TreeMap<>(classpath);
        stored.putAll(modulepath);
        SequencedMap<String, Path> resolved = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.all(argument.folder())) {
                resolved.putIfAbsent(jar.getFileName().toString(), jar);
            }
        }
        for (Map.Entry<String, Layers.Membership> layer : layers.entrySet()) {
            for (String name : layer.getValue().all()) {
                Path jar = resolved.get(name);
                if (jar == null) {
                    throw new IllegalStateException("Layer " + layer.getKey() + " names " + name
                            + ", which was not resolved for this application");
                }
                stored.putIfAbsent(name, jar);
            }
        }
        layers.forEach((layer, membership) -> membership.nativeAccess(resolved, granted)
                .forEach((jar, module) -> graph.enableNativeAccess(layer, jar, module)));
        agents.keySet().retainAll(stored.sequencedKeySet());
        Path store = Files.createDirectories(folder.resolve("jars"));
        for (Map.Entry<String, Path> entry : stored.entrySet()) {
            BuildStep.linkOrCopy(store.resolve(entry.getKey()), entry.getValue());
        }
        List<String> command = Bundle.javaOptions(arguments);
        agents.forEach((jar, options) -> command.add("-javaagent:/app/jars/" + jar
                + (options.isEmpty() ? "" : "=" + options)));
        layers.forEach((layer, membership) -> {
            command.add("-Djlayer.modulepath." + layer + "=" + path(membership.modulepath()));
            if (!membership.classpath().isEmpty()) {
                command.add("-Djlayer.classpath." + layer + "=" + path(membership.classpath()));
            }
        });
        command.add("--class-path");
        command.add(classpath.isEmpty() ? CLASS_PATH : path(classpath.sequencedKeySet()) + ":" + CLASS_PATH);
        command.add("--module-path");
        if (modulepath.isEmpty()) {
            command.add(MODULE_PATH);
            command.addAll(graph.arguments());
            command.add(mainClass);
        } else {
            command.add(path(modulepath.sequencedKeySet()) + ":" + MODULE_PATH);
            command.addAll(graph.arguments());
            command.add("--module");
            command.add(mainModule + "/" + mainClass);
        }
        ProcessBuildStep.argumentFile(folder.resolve("application.args"), command);
        return "COPY jars/ /app/jars/\nCOPY application.args /app/\nWORKDIR /app\n"
                + "ENTRYPOINT [" + quoted(List.of("java", "@/app/application.args")) + "]\n";
    }

    private static String path(SequencedSet<String> names) {
        return names.stream().map(name -> "/app/jars/" + name).collect(Collectors.joining(":"));
    }

    private static String shell(String value) {
        return "'" + value.replace("'", "'\\''") + "'";
    }

    private static String label(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char character : value.strip().replaceAll("\\s+", " ").toCharArray()) {
            switch (character) {
                case '"', '\\', '$' -> builder.append('\\').append(character);
                default -> builder.append(character);
            }
        }
        return builder.append('"').toString();
    }

    private static String quoted(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (!builder.isEmpty()) {
                builder.append(", ");
            }
            builder.append('"');
            for (int index = 0; index < value.length(); index++) {
                char character = value.charAt(index);
                switch (character) {
                    case '"' -> builder.append("\\\"");
                    case '\\' -> builder.append("\\\\");
                    default -> builder.append(character);
                }
            }
            builder.append('"');
        }
        return builder.toString();
    }
}

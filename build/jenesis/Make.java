package build.jenesis;

import module java.base;

public final class Make {

    private static final String PROVIDED = "jenesis.make.provided";
    private static final List<String> CREDENTIALS = List.of("jenesis.maven.token",
            "jenesis.module.token",
            "jenesis.release.token",
            "jenesis.deploy.token",
            "jenesis.cache.key");
    private static final List<String> PLAINTEXT = List.of("jenesis.repository.insecure", "jenesis.cache.insecure");
    private static final List<String> PROGRAMS = List.of("jenesis.daemon.options",
            "jenesis.openpgp.command");
    private static final List<String> SHARED = List.of("jenesis.cache.uri",
            "jenesis.maven.local",
            "jenesis.module.local",
            "jenesis.discovery.ttl",
            "jenesis.sigstore.uri",
            "jenesis.sigstore.issuers");
    private static final List<String> CONFINED = List.of("jenesis.project.target",
            "jenesis.project.reproduce",
            "jenesis.project.artifacts",
            "jenesis.project.cache",
            "jenesis.openpgp.local",
            "jenesis.make.classes",
            "jenesis.pin.file",
            "jenesis.aot.folder");
    private static final Set<String> PRINTING = Set.of("help", "skill", "prompt", "configuration", "properties", "--stop");
    static final List<String> CONTAINED = List.of("jenesis.project.docker",
            "jenesis.project.image",
            "jenesis.project.readable",
            "jenesis.project.writable",
            "jenesis.project.env"),
            EXECUTED = List.of("jenesis.execute.docker",
                    "jenesis.execute.image",
                    "jenesis.execute.readable",
                    "jenesis.execute.writable",
                    "jenesis.execute.env");

    private final String mainClass;
    private final Map<String, String> ambient;
    private final Path root;
    private final Path classes;
    private final Mode mode;
    private final Path aotFolder;
    private final Duration aotLifetime;
    private final Settings settings;

    public record Settings(Map<String, String> keys,
                           SequencedSet<Path> profiles,
                           SequencedSet<String> declared) {
    }

    private record Layer(Properties properties, boolean trusted) {
    }

    public Make(String mainClass) {
        this(mainClass, ambient(Map.of()));
    }

    public Make(String mainClass, Map<String, String> ambient) {
        this.mainClass = mainClass;
        this.ambient = Map.copyOf(ambient);
        String location = ambient.get("make.root");
        root = Path.of(location == null ? "" : location).toAbsolutePath().normalize();
        try {
            settings = settings(root, ambient);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the properties that configure this build", e);
        }
        String classesLocation = settings.keys().get("make.classes");
        classes = classesLocation == null || classesLocation.isBlank()
                ? root.resolve(".jenesis").resolve("classes")
                : root.resolve(classesLocation).normalize();
        mode = Mode.of(settings.keys().get("make.mode"));
        String cache = settings.keys().get("aot.folder");
        aotFolder = cache == null || cache.isBlank()
                ? root.resolve(".jenesis").resolve("aot")
                : root.resolve(cache.trim()).normalize();
        String lifetime = settings.keys().get("aot.lifetime");
        if (lifetime == null || lifetime.isBlank()) {
            aotLifetime = Duration.ZERO;
        } else {
            try {
                aotLifetime = Duration.parse(lifetime.trim());
            } catch (DateTimeParseException e) {
                throw new IllegalArgumentException("Malformed value for jenesis.aot.lifetime: '"
                        + lifetime
                        + "' (expected an ISO-8601 duration, as PT12H or P7D, or no value to keep the cache)", e);
            }
        }
    }

    public enum Mode {

        SOURCE, COMPILED, AOT, DAEMON;

        static Mode of(String value) {
            if (value == null || value.isBlank()) {
                return COMPILED;
            }
            for (Mode mode : values()) {
                if (mode.name().equalsIgnoreCase(value.trim())) {
                    return mode;
                }
            }
            throw new IllegalArgumentException("Malformed value for jenesis.make.mode: '"
                    + value
                    + "' (expected source, compiled, aot or daemon)");
        }
    }

    static Boolean parsed(String name, String value) {
        if (value == null) {
            return null;
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "", "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Malformed value for "
                    + name
                    + ": '"
                    + value
                    + "' (expected true, false, or the setting named with no value at all)");
        };
    }

    private Make(String mainClass,
                 Map<String, String> ambient,
                 Path root,
                 Path classes,
                 Mode mode,
                 Path aotFolder,
                 Duration aotLifetime,
                 Settings settings) {
        this.mainClass = mainClass;
        this.ambient = ambient;
        this.root = root;
        this.classes = classes;
        this.mode = mode;
        this.aotFolder = aotFolder;
        this.aotLifetime = aotLifetime;
        this.settings = settings;
    }

    public Make root(Path root) {
        try {
            return new Make(mainClass,
                    ambient,
                    root,
                    classes,
                    mode,
                    aotFolder,
                    aotLifetime,
                    settings(root, ambient));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot read the properties that configure this build", e);
        }
    }

    public Make classes(Path classes) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, settings);
    }

    public Mode mode() {
        return mode;
    }

    public Make mode(Mode mode) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, settings);
    }

    public Make aotFolder(Path aotFolder) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, settings);
    }

    public Make aotLifetime(Duration aotLifetime) {
        return new Make(mainClass, ambient, root, classes, mode, aotFolder, aotLifetime, settings);
    }

    public record Result(int code, SequencedMap<String, Path> outputs) {
    }

    public static void main(String... arguments) throws Exception {
        SequencedMap<String, String> named = new LinkedHashMap<>();
        String[] selectors = partitioned(arguments, named);
        List<String> options = options(named);
        Map<String, String> ambient = ambient(named);
        Make make = new Make("build.jenesis.Project", ambient);
        Integer code = relaunched(Make.class, ambient, options, selectors);
        System.exit(code == null ? make.run(selectors) : code);
    }

    static Map<String, String> ambient(Map<String, String> named) {
        Map<String, String> properties = new HashMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jenesis.")) {
                properties.put(name, System.getProperty(name));
            }
        }
        properties.putAll(named);
        return keys(properties);
    }

    public static Map<String, String> keys(Map<String, String> properties) {
        Map<String, String> keys = new HashMap<>();
        properties.forEach((name, value) -> {
            if (name.startsWith("jenesis.")) {
                keys.put(name.substring("jenesis.".length()), value);
            }
        });
        return Map.copyOf(keys);
    }

    static String[] partitioned(String[] arguments, SequencedMap<String, String> named) throws IOException {
        List<String> expanded = expanded(arguments), selectors = new ArrayList<>();
        for (String argument : expanded) {
            if (!selectors.isEmpty() || !argument.startsWith("-D")) {
                selectors.add(argument);
                continue;
            }
            String assignment = argument.substring(2);
            int equals = assignment.indexOf('=');
            String name = equals < 0 ? assignment : assignment.substring(0, equals);
            if (!name.startsWith("jenesis.")) {
                throw new IllegalArgumentException("Not a Jenesis setting: -D"
                        + assignment
                        + " - a run is configured by jenesis.* settings alone, and a JVM property is named"
                        + " before the main class");
            }
            named.put(name, equals < 0 ? "" : assignment.substring(equals + 1));
        }
        return selectors.toArray(String[]::new);
    }

    static List<String> expanded(String[] arguments) throws IOException {
        List<String> expanded = new ArrayList<>();
        for (String argument : arguments) {
            if (argument.startsWith("@@")) {
                expanded.add(argument.substring(1));
            } else if (argument.length() > 1 && argument.charAt(0) == '@') {
                expanded.addAll(words(Path.of(argument.substring(1))));
            } else {
                expanded.add(argument);
            }
        }
        return expanded;
    }

    private static List<String> words(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            throw new IllegalArgumentException("No argument file at "
                    + file
                    + " - @<file> names a file of arguments, @@<text> an argument starting with @");
        }
        List<String> words = new ArrayList<>();
        StringBuilder word = new StringBuilder();
        boolean started = false, escaped = false, comment = false;
        char quote = 0;
        for (char current : Files.readString(file).toCharArray()) {
            if (escaped) {
                word.append(switch (current) {
                    case 'n' -> '\n';
                    case 'r' -> '\r';
                    case 't' -> '\t';
                    case 'f' -> '\f';
                    default -> current;
                });
                escaped = false;
            } else if (comment) {
                comment = current != '\n';
            } else if (quote != 0) {
                if (current == '\\') {
                    escaped = true;
                } else if (current == quote) {
                    quote = 0;
                } else {
                    word.append(current);
                }
            } else if (current == '\'' || current == '"') {
                quote = current;
                started = true;
            } else if (current == '#') {
                comment = true;
            } else if (Character.isWhitespace(current)) {
                if (started) {
                    words.add(word.toString());
                    word.setLength(0);
                    started = false;
                }
            } else {
                word.append(current);
                started = true;
            }
        }
        if (quote != 0) {
            throw new IllegalArgumentException("Unterminated " + quote + " in the argument file " + file);
        }
        if (started) {
            words.add(word.toString());
        }
        return words;
    }

    static List<String> options(SequencedMap<String, String> named) {
        SortedMap<String, String> options = new TreeMap<>();
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jenesis.")) {
                options.put(name, System.getProperty(name));
            }
        }
        options.putAll(named);
        List<String> arguments = new ArrayList<>();
        options.forEach((name, value) -> {
            if (!name.startsWith("jenesis.toolchain.")) {
                arguments.add("-D" + name + "=" + value);
            }
        });
        return arguments;
    }

    static Integer relaunched(Class<?> main,
                              Map<String, String> keys,
                              List<String> options,
                              String... arguments) throws Exception {
        String version = keys.get("toolchain.version");
        if (version == null || version.isBlank()) {
            return null;
        }
        Class<?> type = Class.forName("build.jenesis.Toolchain", true, Make.class.getClassLoader());
        try {
            Object toolchain = type.getMethod("ofKeys", Map.class)
                    .invoke(null, keys);
            if (type.getMethod("home").invoke(toolchain).equals(Path.of(System.getProperty("java.home")))) {
                return null;
            }
            return (Integer) type.getMethod("launch", Class.class, List.class, List.class)
                    .invoke(toolchain, main, options, List.of(arguments));
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof Exception cause) {
                throw cause;
            }
            throw e;
        }
    }

    public Result build(String... selectors) throws Exception {
        SequencedMap<String, String> collected = new LinkedHashMap<>();
        int code = run(collected, selectors);
        SequencedMap<String, Path> outputs = new LinkedHashMap<>();
        collected.forEach((identity, output) -> outputs.put(identity, root.resolve(output)));
        return new Result(code, outputs);
    }

    public int run(String... selectors) throws Exception {
        return run(null, selectors);
    }

    private int run(SequencedMap<String, String> collected, String... selectors) throws Exception {
        CodeSource code = Make.class.getProtectionDomain().getCodeSource();
        Path location;
        try {
            location = code == null ? null : Path.of(code.getLocation().toURI());
        } catch (URISyntaxException _) {
            location = null;
        }
        boolean stop = selectors.length == 1 && selectors[0].equals("--stop"),
                building = mainClass.equals("build.jenesis.Project") && (selectors.length == 0 || !Stream.of(selectors)
                        .allMatch(selector -> PRINTING.contains(selector.split("/", 2)[0])));
        if (location == null || !location.getFileName().toString().endsWith(".java")) {
            if (mode != Mode.DAEMON) {
                if (mode == Mode.AOT && collected == null && location != null && building) {
                    return cached(location, selectors);
                }
                if (location != null
                        && building
                        && !(Files.isDirectory(aotFolder) && location.toRealPath().startsWith(aotFolder.toRealPath()))
                        && tools()) {
                    sweep(location);
                }
                return invoke(Make.class.getClassLoader(), collected, selectors);
            }
            String modules = System.getProperty("jdk.module.path");
            String classPath = location == null ? System.getProperty("java.class.path") : location.toString();
            Path engine = location == null || Files.isRegularFile(location)
                    ? location
                    : location.resolve("build").resolve("jenesis");
            return dispatched(Make.class.getClassLoader(),
                    modules != null ? List.of("-p", modules) : List.of("-cp", classPath),
                    engine == null ? "" : fingerprint(engine, files(engine, ""), ""),
                    collected,
                    selectors);
        }
        if (mode == Mode.SOURCE && !stop) {
            return invoke(Make.class.getClassLoader(), collected, selectors);
        }
        Path build = location.getParent();
        List<Path> sources = files(build, ".java");
        String seed = fingerprint(build, sources, classes.toString());
        Path folder = precompiled(build, sources, seed);
        try (URLClassLoader loader = new URLClassLoader(
                new URL[] { folder.toUri().toURL() },
                ClassLoader.getPlatformClassLoader())) {
            if (mode == Mode.DAEMON || stop) {
                return dispatched(loader, List.of("-cp", folder.toString()), seed, collected, selectors);
            }
            if (building && tools()) {
                sweep(folder);
            }
            return invoke(loader, collected, selectors);
        }
    }

    private int cached(Path engine, String... selectors) throws IOException, InterruptedException {
        sweep(engine);
        String modules = System.getProperty("jdk.module.path");
        String module = modules == null ? null : Make.class.getModule().getName();
        Path jar = module != null || Files.isRegularFile(engine) ? engine : aotFolder.resolve("engine.jar");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        Path cache = aotFolder.resolve("engine-" + HexFormat.of()
                .formatHex(digest.digest((module == null ? jar.toString() : modules).getBytes(StandardCharsets.UTF_8)))
                .substring(0, 12) + ".aot");
        boolean reusable = Files.isRegularFile(cache) && Files.exists(jar);
        if (!reusable && !jar.equals(engine)) {
            Path temporary = Files.createTempFile(jar.getParent(), "engine", ".jar");
            try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(temporary));
                 Stream<Path> files = Files.walk(engine)) {
                for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                    out.putNextEntry(new JarEntry(engine.relativize(file).toString()
                            .replace(File.separatorChar, '/')));
                    Files.copy(file, out);
                    out.closeEntry();
                }
            }
            Files.move(temporary, jar, StandardCopyOption.REPLACE_EXISTING);
        }
        List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", File.separatorChar == '\\' ? "java.exe" : "java")
                .toString());
        command.add((reusable ? "-XX:AOTCache=" : "-XX:AOTCacheOutput=") + cache);
        if (!reusable) {
            command.add("-XX:+UnlockDiagnosticVMOptions");
            command.add("-XX:-DisplayVMOutput");
        }
        command.add("-Xlog:aot=off");
        new TreeMap<>(ambient).forEach((key, value) -> {
            if (!key.startsWith("toolchain.")) {
                command.add("-Djenesis." + key + "=" + value);
            }
        });
        command.add("-Djenesis.make.root=" + root);
        command.add("-Djenesis.make.classes=" + classes);
        command.add("-Djenesis.make.mode=compiled");
        if (tools()) {
            command.add("-Djenesis.process.mode=aot");
            command.add("-Djenesis.aot.folder=" + aotFolder);
        }
        if (module == null) {
            command.addAll(List.of("-cp", jar.toString(), Make.class.getName()));
        } else {
            command.addAll(List.of("-p", modules, "-m", module + "/" + Make.class.getName()));
        }
        for (String selector : selectors) {
            command.add(selector.startsWith("@") ? "@" + selector : selector);
        }
        return new ProcessBuilder(command).inheritIO().start().waitFor();
    }

    private static String fingerprint(Path folder, List<Path> files, String salt) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        digest.update((Runtime.version().feature() + "\n" + salt).getBytes(StandardCharsets.UTF_8));
        ByteBuffer buffer = ByteBuffer.allocate(1 << 16);
        for (Path file : files) {
            digest.update(folder.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
            try (FileChannel channel = FileChannel.open(file)) {
                while (channel.read(buffer) != -1) {
                    buffer.flip();
                    digest.update(buffer);
                    buffer.clear();
                }
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static List<Path> files(Path folder, String suffix) throws IOException {
        if (Files.isRegularFile(folder)) {
            return List.of(folder);
        }
        List<Path> files = new ArrayList<>();
        Files.walkFileTree(folder, Set.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (directory.equals(folder)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = directory.getFileName().toString();
                return Character.isJavaIdentifierStart(name.codePointAt(0))
                        && name.codePoints().skip(1).allMatch(Character::isJavaIdentifierPart)
                        ? FileVisitResult.CONTINUE
                        : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (file.getFileName().toString().endsWith(suffix)) {
                    files.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return files.stream().sorted().toList();
    }

    private int dispatched(ClassLoader loader,
                           List<String> path,
                           String seed,
                           SequencedMap<String, String> collected,
                           String... selectors) throws Exception {
        return (int) Class.forName("build.jenesis.daemon.DaemonClient", true, loader)
                .getMethod("doDispatch",
                        Path.class,
                        List.class,
                        String.class,
                        String.class,
                        SequencedMap.class,
                        SequencedMap.class,
                        String[].class)
                .invoke(null, root, path, mainClass, seed, supplied(), collected, selectors);
    }

    private SequencedMap<String, String> supplied() {
        SequencedMap<String, String> supplied = new LinkedHashMap<>();
        for (String key : settings.declared()) {
            String value = settings.keys().get(key);
            if (value != null) {
                supplied.put("jenesis." + key, value);
            }
        }
        return supplied;
    }

    private int invoke(ClassLoader loader, SequencedMap<String, String> collected, String... selectors)
            throws Exception {
        Class<?> project = Class.forName("build.jenesis.Project", true, loader);
        if (collected == null || !mainClass.equals("build.jenesis.Project")) {
            return (int) project
                    .getMethod("run", Map.class, String.class, Path.class, SequencedSet.class, String[].class)
                    .invoke(null, settled(), mainClass, root, settings.profiles(), selectors);
        }
        Object produced = project
                .getMethod("perform", Map.class, Path.class, SequencedSet.class, String[].class)
                .invoke(null, settled(), root, settings.profiles(), selectors);
        if (produced == null) {
            return 1;
        }
        ((Map<?, ?>) produced).forEach((identity, output) -> collected.put(identity.toString(),
                root.relativize(Path.of(output.toString()).toAbsolutePath().normalize()).toString()));
        return 0;
    }

    private boolean tools() {
        String process = settings.keys().get("process.mode");
        return process == null || process.isBlank() ? mode == Mode.AOT : process.trim().equalsIgnoreCase("aot");
    }

    private Map<String, String> settled() {
        if (!tools()) {
            return settings.keys();
        }
        Map<String, String> keys = new HashMap<>(settings.keys());
        keys.put("process.mode", "aot");
        keys.put("aot.folder", aotFolder.toString());
        return Collections.unmodifiableMap(keys);
    }

    private void sweep(Path engine) throws IOException {
        StringBuilder identity = new StringBuilder(Runtime.version().toString());
        try (Stream<Path> files = Files.walk(engine)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                BasicFileAttributes attributes = Files.readAttributes(file, BasicFileAttributes.class);
                identity.append('|').append(engine.relativize(file))
                        .append(':').append(attributes.size())
                        .append(':').append(attributes.lastModifiedTime().toMillis());
            }
        }
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        String fingerprint = HexFormat.of().formatHex(digest.digest(identity.toString().getBytes(StandardCharsets.UTF_8)));
        Path stamp = aotFolder.resolve("aot.properties");
        Properties recorded = Files.isRegularFile(stamp) ? read(stamp) : new Properties();
        Instant created;
        try {
            created = Instant.parse(recorded.getProperty("created", ""));
        } catch (DateTimeParseException _) {
            created = null;
        }
        if (fingerprint.equals(recorded.getProperty("engine"))
                && created != null
                && (aotLifetime.isZero() || !created.isBefore(Instant.now().minus(aotLifetime)))) {
            return;
        }
        if (Files.isDirectory(aotFolder)) {
            try (Stream<Path> files = Files.walk(aotFolder)) {
                for (Path file : files.filter(file -> !file.equals(aotFolder)).sorted(Comparator.reverseOrder()).toList()) {
                    Files.deleteIfExists(file);
                }
            }
        }
        Properties written = new Properties();
        written.setProperty("engine", fingerprint);
        written.setProperty("created", Instant.now().toString());
        Path temporary = Files.createTempFile(Files.createDirectories(aotFolder), "aot", ".properties");
        try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            written.store(writer, null);
        }
        Files.move(temporary, stamp, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    public static Settings settings(Path path) throws IOException {
        return settings(path, ambient(Map.of()));
    }

    public static Settings settings(Path path, Map<String, String> ambient) throws IOException {
        Path base = path.resolve("jenesis.properties");
        Properties project = read(base);
        if (project != null) {
            requireApplicable(path, base, project, false);
        }
        String configured = ambient.get("make.global");
        String location = configured == null ? System.getProperty("user.home") : configured;
        Properties user = null;
        Path home = null;
        if (!location.isEmpty()) {
            home = Path.of(location).resolve(".jenesis");
            Path file = home.resolve("jenesis.properties");
            user = read(file);
            if (user != null) {
                requireApplicable(path, file, user, true);
            }
        }
        Set<Path> loaded = new LinkedHashSet<>();
        Deque<Path> pending = new ArrayDeque<>();
        List<Layer> local = new ArrayList<>(), layers = new ArrayList<>();
        addProfiles(pending, path, ambient.get("make.profiles"));
        if (project != null) {
            addProfiles(pending, path, project.getProperty("jenesis.make.profiles"));
        }
        loadProfiles(loaded, local, pending, path, path, false);
        if (project != null) {
            local.add(new Layer(project, false));
        }
        if (user != null) {
            addProfiles(pending, home, user.getProperty("jenesis.make.profiles"));
            loadProfiles(loaded, layers, pending, home, path, true);
            layers.add(new Layer(user, true));
        }
        layers.addAll(local);
        SequencedSet<Path> profiles = new LinkedHashSet<>();
        for (Path file : loaded) {
            String name = file.getFileName().toString();
            profiles.add(Path.of(name.substring("jenesis-".length(), name.length() - ".properties".length())));
        }
        SequencedSet<String> declared = new LinkedHashSet<>();
        for (Layer layer : layers) {
            for (String name : layer.properties().stringPropertyNames()) {
                if (name.startsWith("jenesis.")) {
                    declared.add(name.substring("jenesis.".length()));
                }
            }
        }
        return new Settings(layered(ambient, layers), profiles, declared);
    }

    private static Map<String, String> layered(Map<String, String> ambient, List<Layer> layers) {
        SequencedSet<String> provided = new LinkedHashSet<>();
        Set<String> declared = new HashSet<>();
        for (Layer layer : layers) {
            for (String name : layer.properties().stringPropertyNames()) {
                if (!name.startsWith("jenesis.")) {
                    continue;
                }
                String key = name.substring("jenesis.".length());
                if (declared.add(key) && !ambient.containsKey(key) && !layer.trusted()) {
                    provided.add(key);
                }
            }
        }
        Map<String, String> keys = new HashMap<>();
        for (Layer layer : layers.reversed()) {
            for (String name : layer.properties().stringPropertyNames()) {
                if (name.startsWith("jenesis.")) {
                    keys.put(name.substring("jenesis.".length()), layer.properties().getProperty(name));
                }
            }
        }
        keys.putAll(ambient);
        keys.remove("make.provided");
        if (!provided.isEmpty()) {
            keys.put("make.provided", String.join(",", provided));
        }
        return Map.copyOf(keys);
    }

    private static void addProfiles(Deque<Path> pending, Path base, String list) {
        if (list == null) {
            return;
        }
        for (String name : list.split(",")) {
            String trimmed = name.trim();
            if (trimmed.endsWith(".properties")) {
                trimmed = trimmed.substring(0, trimmed.length() - ".properties".length());
            }
            if (!trimmed.isEmpty()) {
                pending.add(base.resolve("jenesis-" + trimmed + ".properties"));
            }
        }
    }

    private static void loadProfiles(Set<Path> loaded,
                                     List<Layer> layers,
                                     Deque<Path> pending,
                                     Path base,
                                     Path root,
                                     boolean trusted) throws IOException {
        while (!pending.isEmpty()) {
            Path file = pending.removeFirst().normalize();
            if (!loaded.add(file) || !Files.isRegularFile(file)) {
                continue;
            }
            Properties properties = read(file);
            requireApplicable(root, file, properties, trusted);
            addProfiles(pending, base, properties.getProperty("jenesis.make.profiles"));
            layers.add(new Layer(properties, trusted));
        }
    }

    private static void requireApplicable(Path root, Path file, Properties properties, boolean trusted) {
        for (String name : new TreeSet<>(properties.stringPropertyNames())) {
            if (!name.startsWith("jenesis.")) {
                throw new IllegalArgumentException(name + " in " + file + " is no setting: every key of this file"
                        + " is written in full, as `configuration` prints it, so write jenesis." + name);
            }
        }
        if (properties.getProperty(PROVIDED) != null) {
            throw new IllegalStateException(PROVIDED + " cannot be set in " + file
                    + ": it records which settings the files a project provides supplied, and Make derives it");
        }
        if (!trusted) {
            for (String key : CREDENTIALS) {
                if (properties.getProperty(key) != null) {
                    throw new IllegalStateException(key + " cannot be set in " + file
                            + ": a credential is yours to hand out, so only the command line, your own"
                            + " ~/.jenesis/jenesis.properties or the environment may name one"
                            + " (pass -D" + key + " instead)");
                }
            }
            for (String key : PLAINTEXT) {
                if (properties.getProperty(key) != null) {
                    throw new IllegalStateException(key + " cannot be set in " + file
                            + ": whether this build may talk plaintext http is yours to decide, so only the"
                            + " command line or your own ~/.jenesis/jenesis.properties may allow it"
                            + " (pass -D" + key + " instead)");
                }
            }
        }
        if (properties.getProperty("jenesis.make.root") != null) {
            throw new IllegalStateException("jenesis.make.root cannot be set in " + file
                    + ": the project root locates this file, so it is resolved before the file is read"
                    + " (pass -Djenesis.make.root on the command line instead)");
        }
        if (properties.getProperty("jenesis.release.publish") != null) {
            throw new IllegalStateException("jenesis.release.publish cannot be set in " + file
                    + ": publishing is what one run is asked to do, and every pin runs the release, so only the"
                    + " command line may name it (pass -Djenesis.release.publish=true to the release that publishes)");
        }
        if (properties.getProperty("jenesis.make.global") != null) {
            throw new IllegalStateException("jenesis.make.global cannot be set in " + file
                    + ": it locates your own user-global settings, which no file may move, least of all one a"
                    + " project provides (pass -Djenesis.make.global on the command line instead)");
        }
        if (!trusted) {
            for (String name : properties.stringPropertyNames()) {
                if (CONTAINED.contains(name) || EXECUTED.contains(name)) {
                    throw new IllegalStateException(name + " cannot be set in " + file
                            + ": whether and how the build is isolated is yours to decide, so only the command line"
                            + " or your own ~/.jenesis/jenesis.properties may configure Docker, never a file the"
                            + " project provides (pass -D" + name + " instead)");
                }
            }
        }
        for (String key : List.of("jenesis.toolchain.searchpath", "jenesis.toolchain.installer")) {
            if (!trusted && properties.getProperty(key) != null) {
                throw new IllegalStateException(key + " cannot be set in " + file
                        + ": it decides which programs the build executes, so only the command line or your own"
                        + " ~/.jenesis/jenesis.properties may set it, never a file the project provides"
                        + " (pass -D" + key + " instead)");
            }
        }
        if (trusted) {
            return;
        }
        for (String key : PROGRAMS) {
            if (properties.getProperty(key) != null) {
                throw new IllegalStateException(key + " cannot be set in " + file
                        + ": it names a program the build runs, or the options a JVM runs with, so only the command"
                        + " line or your own ~/.jenesis/jenesis.properties may set it, never a file the project"
                        + " provides (pass -D" + key + " instead)");
            }
        }
        for (String key : SHARED) {
            if (properties.getProperty(key) != null) {
                throw new IllegalStateException(key + " cannot be set in " + file
                        + ": it names a cache whose outputs the build runs, a folder this machine shares between"
                        + " projects or what the build trusts, so only the command line or your own"
                        + " ~/.jenesis/jenesis.properties may set it, never a file the project provides"
                        + " (pass -D" + key + " instead)");
            }
        }
        for (String name : properties.stringPropertyNames()) {
            if (name.startsWith("jenesis.jarsigner.")) {
                throw new IllegalStateException(name + " cannot be set in " + file
                        + ": it names the key a jar is signed with or where its password is read from, which the"
                        + " machine that signs supplies, so only the command line or your own"
                        + " ~/.jenesis/jenesis.properties may set it, never a file the project provides"
                        + " (pass -D" + name + " instead)");
            }
        }
        Path base = root.toAbsolutePath().normalize();
        for (String key : CONFINED) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                continue;
            }
            Path folder;
            try {
                folder = Path.of(value.strip());
            } catch (InvalidPathException _) {
                folder = null;
            }
            if (folder == null || folder.isAbsolute() || !base.resolve(folder).normalize().startsWith(base)) {
                throw new IllegalStateException(key + " cannot name '" + value.strip() + "' in " + file
                        + ": a file the project provides names only a folder inside the project, which the build"
                        + " writes to and may delete (pass -D" + key + " on the command line for one outside it)");
            }
        }
    }

    private static Properties read(Path file) throws IOException {
        if (!Files.isRegularFile(file)) {
            return null;
        }
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    private Path precompiled(Path build, List<Path> sources, String seed) throws IOException {
        Path stamp = root.resolve(".jenesis").resolve("make.digest");
        Path compiled = classes.resolve(mainClass.replace('.', '/') + ".class");
        if (Files.isRegularFile(stamp) && Files.readString(stamp).equals(seed) && Files.isRegularFile(compiled)) {
            return classes;
        }
        ToolProvider javac = ToolProvider.findFirst("javac").orElseThrow(() -> new IllegalStateException(
                "No javac is available to compile the build sources - run on a JDK, not a JRE"));
        List<String> arguments = new ArrayList<>(List.of("-nowarn"));
        if (!classes.equals(root)) {
            if (Files.exists(classes)) {
                Files.walkFileTree(classes, new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                        Files.delete(file);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult postVisitDirectory(Path directory, IOException exception)
                            throws IOException {
                        Files.delete(directory);
                        return FileVisitResult.CONTINUE;
                    }
                });
            }
            Files.createDirectories(classes);
            arguments.addAll(List.of("-d", classes.toString()));
        }
        sources.forEach(source -> arguments.add(source.toString()));
        StringWriter out = new StringWriter(), error = new StringWriter();
        if (javac.run(new PrintWriter(out), new PrintWriter(error), arguments.toArray(String[]::new)) != 0) {
            throw new IllegalStateException("Failed to compile the build sources under "
                    + build + ":\n" + error + out);
        }
        Files.createDirectories(stamp.getParent());
        Files.writeString(stamp, seed);
        return classes;
    }
}

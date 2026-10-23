package build.jenesis.step;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.SequencedProperties;
import build.jenesis.daemon.Worker;

public sealed interface ProcessHandler permits ProcessHandler.OfTool, ProcessHandler.OfProcess {

    static Closeable scope() {
        return JavaWorker.scope();
    }

    static boolean nest(Closeable nested) {
        return JavaWorker.nest(nested);
    }

    List<String> commands();

    int execute(Path output, Path error, Tee tee) throws IOException;

    boolean external();

    record Tee(Executor executor, Consumer<String> out, Consumer<String> err) {
    }

    @FunctionalInterface
    interface Staged extends Function<List<String>, OfProcess> {
    }

    private static Charset encoding() {
        String name = System.getProperty("native.encoding");
        if (name == null) {
            return Charset.defaultCharset();
        }
        try {
            return Charset.forName(name);
        } catch (IllegalArgumentException _) {
            return Charset.defaultCharset();
        }
    }

    private static Writer writer(Path file) throws IOException {
        return new BufferedWriter(new OutputStreamWriter(Files.newOutputStream(file), StandardCharsets.UTF_8));
    }

    enum Factory {
        TOOL {
            @Override
            public Function<List<String>, ? extends ProcessHandler> apply(String tool, String fork) {
                return ProcessHandler.OfTool.of(tool);
            }
        },
        FORK {
            @Override
            public Function<List<String>, ? extends ProcessHandler> apply(String tool, String fork) {
                return ProcessHandler.OfProcess.ofJavaHome(fork);
            }
        };

        public static Factory of() {
            return ofEnvironment(Environment.NONE);
        }

        public static Factory ofEnvironment(Environment environment) {
            String factory = environment.getProperty("process.factory");
            if (factory == null) {
                if (System.getProperty("org.graalvm.nativeimage.imagecode") == null) {
                    return TOOL;
                }
                return ToolProvider.findFirst("javac").isPresent() ? TOOL : FORK;
            }
            return switch (factory) {
                case "tool" -> TOOL;
                case "fork" -> FORK;
                default -> throw new IllegalArgumentException(
                        "Unknown process factory: " + factory + " (expected 'tool' or 'fork')");
            };
        }

        public abstract Function<List<String>, ? extends ProcessHandler> apply(String tool, String fork);
    }

    final class OfTool implements ProcessHandler {

        private static final Lock IMAGE = new ReentrantLock();

        private final ToolProvider toolProvider;

        private final Lock exclusive;

        private final List<String> commands;

        private OfTool(ToolProvider toolProvider, Lock exclusive, List<String> commands) {
            this.toolProvider = toolProvider;
            this.exclusive = exclusive;
            this.commands = commands;
        }

        public static Function<List<String>, ProcessHandler> of(ToolProvider toolProvider) {
            return arguments -> new OfTool(toolProvider, null, arguments);
        }

        public static Function<List<String>, ProcessHandler> of(String name) {
            ToolProvider toolProvider = ToolProvider.findFirst(name)
                    .orElseThrow(() -> new IllegalArgumentException("No tool: " + name));
            Lock exclusive = switch (name) {
                case "jlink", "jpackage" -> Runtime.version().feature() < 28 ? IMAGE : null;
                default -> null;
            };
            return arguments -> new OfTool(toolProvider, exclusive, arguments);
        }

        @Override
        public List<String> commands() {
            return Stream.concat(Stream.of(toolProvider.name()), commands.stream()).toList();
        }

        @Override
        public boolean external() {
            return false;
        }

        @Override
        public int execute(Path output, Path error, Tee tee) throws IOException {
            if (exclusive == null) {
                return run(output, error, tee);
            }
            exclusive.lock();
            try {
                return run(output, error, tee);
            } finally {
                exclusive.unlock();
            }
        }

        private int run(Path output, Path error, Tee tee) throws IOException {
            if (tee == null) {
                try (PrintWriter out = new PrintWriter(writer(output));
                     PrintWriter err = new PrintWriter(writer(error))) {
                    return toolProvider.run(out, err, commands.toArray(String[]::new));
                }
            }
            try (PrintWriter out = new PrintWriter(new LineTee(writer(output), tee.out()), true);
                 PrintWriter err = new PrintWriter(new LineTee(writer(error), tee.err()), true)) {
                return toolProvider.run(out, err, commands.toArray(String[]::new));
            }
        }

        private static final class LineTee extends Writer {

            private final Writer delegate;
            private final Consumer<String> consumer;
            private final StringBuilder line = new StringBuilder();

            private LineTee(Writer delegate, Consumer<String> consumer) {
                this.delegate = delegate;
                this.consumer = consumer;
            }

            @Override
            public void write(char[] buffer, int offset, int length) throws IOException {
                delegate.write(buffer, offset, length);
                for (int index = 0; index < length; index++) {
                    char character = buffer[offset + index];
                    if (character == '\n') {
                        consumer.accept(line.toString());
                        line.setLength(0);
                    } else if (character != '\r') {
                        line.append(character);
                    }
                }
            }

            @Override
            public void flush() throws IOException {
                delegate.flush();
            }

            @Override
            public void close() throws IOException {
                try {
                    if (!line.isEmpty()) {
                        consumer.accept(line.toString());
                        line.setLength(0);
                    }
                } finally {
                    delegate.close();
                }
            }
        }
    }

    final class OfProcess implements ProcessHandler {

        private static final boolean WINDOWS = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        private static final Set<String> PLATFORM = Set.of("PATH", "HOME", "LANG", "TMPDIR",
                "SYSTEMROOT", "SYSTEMDRIVE", "WINDIR", "COMSPEC", "PATHEXT", "TEMP", "TMP", "USERPROFILE",
                "PROGRAMFILES", "PROGRAMFILES(X86)", "PROGRAMW6432", "PROGRAMDATA", "APPDATA", "LOCALAPPDATA");
        private static final Map<String, Path> DRIVERS = new ConcurrentHashMap<>();
        private static final Set<Path> TEMPORARY = ConcurrentHashMap.newKeySet();

        static {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> TEMPORARY.forEach(folder -> {
                try (Stream<Path> files = Files.walk(folder)) {
                    files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
                } catch (IOException | UncheckedIOException _) {
                }
            })));
        }

        private final List<String> commands;
        private final SortedMap<String, String> environment;
        private final Charset output, error;
        private final boolean main, daemon, scoped, shared;
        private final Path aot;

        private OfProcess(List<String> commands) {
            this(commands, encoding(), encoding());
        }

        private OfProcess(List<String> commands, Charset output, Charset error) {
            SortedMap<String, String> environment = new TreeMap<>(WINDOWS ? String.CASE_INSENSITIVE_ORDER : null);
            System.getenv().forEach((name, value) -> {
                if (name.startsWith("LC_") || PLATFORM.contains(WINDOWS ? name.toUpperCase(Locale.ROOT) : name)) {
                    environment.put(name, value);
                }
            });
            this(commands, environment, output, error, false, false, false, false, null);
        }

        private OfProcess(List<String> commands,
                          SortedMap<String, String> environment,
                          Charset output,
                          Charset error,
                          boolean main,
                          boolean daemon,
                          boolean scoped,
                          boolean shared,
                          Path aot) {
            this.commands = commands;
            this.environment = Collections.unmodifiableSortedMap(environment);
            this.output = output;
            this.error = error;
            this.main = main;
            this.daemon = daemon;
            this.scoped = scoped;
            this.shared = shared;
            this.aot = aot;
        }

        public static Function<List<String>, OfProcess> ofJavaHome(String command) {
            String home = System.getProperty("java.home");
            if (home == null) {
                home = System.getenv("JAVA_HOME");
            }
            if (home == null) {
                throw new IllegalStateException("Neither JAVA_HOME environment or java.home property set");
            } else {
                File program = new File(home, command + (WINDOWS ? ".exe" : ""));
                if (program.isFile()) {
                    String option = command.equals("bin/java") ? "-D" : "-J-D";
                    return arguments -> {
                        List<String> commands = new ArrayList<>(List.of(program.getPath()));
                        Charset output = printing(arguments, option + "stdout.encoding=", commands),
                                error = printing(arguments, option + "stderr.encoding=", commands);
                        commands.addAll(arguments);
                        return new OfProcess(List.copyOf(commands), output, error);
                    };
                } else {
                    throw new IllegalStateException("Could not find command " + program.getPath() + " in " + home);
                }
            }
        }

        public static Function<List<String>, OfProcess> ofJavaMain() {
            Function<List<String>, OfProcess> java = ofJavaHome("bin/java");
            return arguments -> {
                OfProcess process = java.apply(arguments);
                return new OfProcess(process.commands,
                        new TreeMap<>(process.environment),
                        process.output,
                        process.error,
                        true,
                        true,
                        false,
                        false,
                        null);
            };
        }

        public static Path temporary(String prefix) throws IOException {
            Path folder = Files.createTempDirectory(prefix);
            TEMPORARY.add(folder);
            return folder;
        }

        public static Path driver(Class<?> type) throws IOException {
            SequencedMap<String, byte[]> classes = new TreeMap<>();
            for (Class<?> member : type.getNestMembers()) {
                String name = member.getName().replace('.', '/') + ".class";
                try (InputStream input = member.getResourceAsStream("/" + name)) {
                    if (input == null) {
                        throw new IllegalStateException("Cannot read the class file of " + member.getName()
                                + " to hand it to a JVM - load the driver from a jar or a folder");
                    }
                    classes.put(name, input.readAllBytes());
                }
            }
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            classes.forEach((name, bytes) -> {
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update(bytes);
            });
            try {
                return DRIVERS.computeIfAbsent(HexFormat.of().formatHex(digest.digest()), _ -> {
                    try {
                        Path folder = temporary("jenesis-driver");
                        for (Map.Entry<String, byte[]> entry : classes.entrySet()) {
                            Path file = folder.resolve(entry.getKey());
                            Files.createDirectories(file.getParent());
                            Files.write(file, entry.getValue());
                        }
                        return folder;
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });
            } catch (UncheckedIOException e) {
                throw e.getCause();
            }
        }

        public static Function<List<String>, OfProcess> ofCommand(String command) {
            return arguments -> {
                String located = locate(command);
                Stream<String> program = WINDOWS && (located.endsWith(".cmd") || located.endsWith(".bat"))
                        ? Stream.of("cmd.exe", "/c", located)
                        : Stream.of(located);
                return new OfProcess(Stream.concat(program, arguments.stream()).toList());
            };
        }

        private static Charset printing(List<String> arguments, String option, List<String> commands) {
            for (String argument : arguments) {
                if (argument.startsWith(option)) {
                    String name = argument.substring(option.length());
                    try {
                        return Charset.forName(name);
                    } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException(argument + " names no charset this JVM knows,"
                                + " so what the tool prints cannot be read: name one such as UTF-8, the default", e);
                    }
                }
            }
            commands.add(option + "UTF-8");
            return StandardCharsets.UTF_8;
        }

        private static String locate(String command) {
            List<String> names = new ArrayList<>();
            if (WINDOWS) {
                names.add(command + ".exe");
                names.add(command + ".cmd");
                names.add(command + ".bat");
            } else {
                names.add(command);
            }
            if (command.indexOf('/') != -1 || command.indexOf(File.separatorChar) != -1) {
                File direct = new File(command);
                if (direct.isFile()) {
                    return direct.getPath();
                }
                for (String name : names) {
                    File program = new File(name);
                    if (program.isFile()) {
                        return program.getPath();
                    }
                }
                throw new IllegalStateException("Could not locate '"
                        + command
                        + "': it names a path, but no executable file is there");
            }
            List<String> homes = new ArrayList<>();
            String graalvm = System.getenv("GRAALVM_HOME");
            if (graalvm != null) {
                homes.add(graalvm);
            }
            String java = System.getProperty("java.home");
            if (java != null) {
                homes.add(java);
            }
            for (String home : homes) {
                for (String name : names) {
                    File program = new File(new File(home, "bin"), name);
                    if (program.isFile()) {
                        return program.getPath();
                    }
                }
            }
            String path = System.getenv("PATH");
            if (path != null) {
                for (String entry : path.split(File.pathSeparator)) {
                    for (String name : names) {
                        File program = new File(entry, name);
                        if (program.isFile() && program.canExecute()) {
                            return program.getPath();
                        }
                    }
                }
            }
            throw new IllegalStateException("Could not locate '"
                    + command
                    + "' in GRAALVM_HOME, java.home/bin, or PATH");
        }

        public static Function<List<String>, OfProcess> of(List<String> program) {
            return arguments -> new OfProcess(Stream.concat(program.stream(), arguments.stream()).toList());
        }

        public static Staged ofStaged() {
            return OfProcess::new;
        }

        @Override
        public List<String> commands() {
            return commands;
        }

        public SortedMap<String, String> environment() {
            return environment;
        }

        public OfProcess environment(SortedMap<String, String> environment) {
            return new OfProcess(commands, new TreeMap<>(environment), output, error, main, daemon, scoped, shared, aot);
        }

        public boolean daemon() {
            return daemon;
        }

        public OfProcess daemon(boolean daemon) {
            return new OfProcess(commands, new TreeMap<>(environment), output, error, main, daemon, scoped, shared, aot);
        }

        public boolean scoped() {
            return scoped;
        }

        public OfProcess scoped(boolean scoped) {
            return new OfProcess(commands, new TreeMap<>(environment), output, error, main, daemon, scoped, shared, aot);
        }

        public boolean shared() {
            return shared;
        }

        public OfProcess shared(boolean shared) {
            return new OfProcess(commands, new TreeMap<>(environment), output, error, main, daemon, scoped, shared, aot);
        }

        public Path aot() {
            return aot;
        }

        public OfProcess aot(Path aot) {
            return new OfProcess(commands, new TreeMap<>(environment), output, error, main, daemon, scoped, shared, aot);
        }

        @Override
        public boolean external() {
            return true;
        }

        @Override
        public int execute(Path output, Path error, Tee tee) throws IOException {
            JavaWorker.Call call = main || daemon || scoped ? JavaWorker.call(commands) : null;
            if (call != null && (daemon || scoped && (shared || call.main().equals(Worker.TOOL)))) {
                return JavaWorker.execute(call, shared, daemon, environment, output, error, tee);
            }
            JavaWorker.Once once = call == null || !main || call.main().endsWith(".java")
                    ? null
                    : JavaWorker.once(call, aot);
            ProcessBuilder builder = new ProcessBuilder(once == null ? commands : once.commands());
            builder.environment().clear();
            builder.environment().putAll(environment);
            builder.environment().putIfAbsent("COLUMNS", "80");
            builder.environment().putIfAbsent("LINES", "24");
            builder.environment().putIfAbsent("TERM", "dumb");
            Process process = builder.start();
            process.getOutputStream().close();
            CompletableFuture<Void> errored, printed;
            if (tee == null) {
                errored = drain(Thread.ofVirtual()::start, process.getErrorStream(), this.error, error, null);
                printed = drain(Thread.ofVirtual()::start, process.getInputStream(), this.output, output, null);
            } else {
                errored = drain(tee.executor(), process.getErrorStream(), this.error, error, tee.err());
                printed = null;
            }
            try {
                if (tee != null) {
                    drain(process.getInputStream(), this.output, output, tee.out());
                }
                int code = process.waitFor();
                if (printed != null) {
                    printed.join();
                }
                errored.join();
                if (once != null) {
                    once.complete();
                }
                return code;
            } catch (InterruptedException e) {
                process.destroyForcibly();
                try {
                    process.waitFor(5, TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                }
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        private static CompletableFuture<Void> drain(Executor executor,
                                                     InputStream stream,
                                                     Charset encoding,
                                                     Path file,
                                                     Consumer<String> consumer) {
            CompletableFuture<Void> drained = new CompletableFuture<>();
            executor.execute(() -> {
                try {
                    drain(stream, encoding, file, consumer);
                    drained.complete(null);
                } catch (Throwable t) {
                    drained.completeExceptionally(t);
                }
            });
            return drained;
        }

        private static void drain(InputStream stream, Charset encoding, Path file, Consumer<String> consumer)
                throws IOException {
            try (Reader reader = new InputStreamReader(stream, encoding);
                 Writer writer = writer(file)) {
                StringBuilder line = new StringBuilder();
                char[] buffer = new char[8192];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    writer.write(buffer, 0, read);
                    if (consumer == null) {
                        continue;
                    }
                    for (int index = 0; index < read; index++) {
                        if (buffer[index] == '\n') {
                            consumer.accept(line.toString());
                            line.setLength(0);
                        } else if (buffer[index] != '\r') {
                            line.append(buffer[index]);
                        }
                    }
                }
                if (consumer != null && !line.isEmpty()) {
                    consumer.accept(line.toString());
                }
            }
        }
    }
}

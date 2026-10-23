package build.jenesis.step;

import module java.base;
import build.jenesis.daemon.Worker;

final class JavaWorker {

    private static final Set<String> TAKING = Set.of("-p",
            "--module-path",
            "--upgrade-module-path",
            "--add-modules",
            "--limit-modules",
            "-m",
            "--module",
            "--add-reads",
            "--add-exports",
            "--add-opens",
            "--patch-module",
            "--enable-native-access",
            "--source",
            "-d",
            "--describe-module",
            "-jar");
    private static final Set<String> CLASS_PATH = Set.of("-cp", "-classpath", "--class-path");
    private static final Map<Key, Deque<Running>> IDLE = new ConcurrentHashMap<>();
    private static final Map<Key, Shared> SHARED = new HashMap<>();
    private static final List<Closeable> NESTED = new ArrayList<>();
    private static final AtomicBoolean HOOKED = new AtomicBoolean();
    private static Path compiled;
    private static int scopes;

    private JavaWorker() {
    }

    static Call call(List<String> commands) {
        Path launcher = Path.of(commands.getFirst());
        String name = launcher.getFileName().toString(), tool = name.replaceFirst("\\.exe$", "");
        Path java = launcher.resolveSibling(name.endsWith(".exe") ? "java.exe" : "java");
        if (!tool.equals("java") && launcher.getParent() != null && Files.isRegularFile(java)) {
            List<String> options = new ArrayList<>(), arguments = new ArrayList<>(List.of(tool));
            for (String command : commands.subList(1, commands.size())) {
                if (command.startsWith("-J")) {
                    options.add(command.substring(2));
                } else {
                    arguments.add(command);
                }
            }
            return new Call(java.toString(), List.copyOf(options), "", Worker.TOOL, List.copyOf(arguments));
        }
        List<String> options = new ArrayList<>();
        String classpath = null;
        int index = 1;
        for (; index < commands.size(); index++) {
            String command = commands.get(index);
            if (CLASS_PATH.contains(command)) {
                if (classpath != null || index + 1 >= commands.size()) {
                    return null;
                }
                classpath = commands.get(++index);
            } else if (command.startsWith("--class-path=")) {
                if (classpath != null) {
                    return null;
                }
                classpath = command.substring("--class-path=".length());
            } else if (!command.startsWith("-")) {
                break;
            } else if (TAKING.contains(command) || command.startsWith("@")) {
                return null;
            } else {
                options.add(command);
            }
        }
        if (classpath == null || index >= commands.size() || commands.get(index).startsWith("@")) {
            return null;
        }
        return new Call(commands.getFirst(),
                List.copyOf(options),
                classpath,
                commands.get(index),
                List.copyOf(commands.subList(index + 1, commands.size())));
    }

    static int execute(Call call,
                       boolean shared,
                       boolean lasting,
                       SortedMap<String, String> environment,
                       Path output,
                       Path error,
                       ProcessHandler.Tee tee) throws IOException {
        List<String> entries = call.classpath().isEmpty()
                ? List.of()
                : List.of(call.classpath().split(Pattern.quote(File.pathSeparator), -1));
        List<Object> identities = new ArrayList<>();
        for (String entry : entries) {
            Path path = Path.of(entry).toAbsolutePath();
            if (Files.isRegularFile(path)) {
                Object identity = Files.readAttributes(path, BasicFileAttributes.class).fileKey();
                if (identity == null) {
                    try (InputStream input = Files.newInputStream(path)) {
                        MessageDigest digest = MessageDigest.getInstance("SHA-256");
                        input.transferTo(new DigestOutputStream(OutputStream.nullOutputStream(), digest));
                        identity = HexFormat.of().formatHex(digest.digest());
                    } catch (NoSuchAlgorithmException e) {
                        throw new IllegalStateException(e);
                    }
                }
                identities.add(identity);
            } else {
                identities.add(path.toString());
            }
        }
        Key key = new Key(call.java(), call.options(), List.copyOf(identities), new TreeMap<>(environment));
        List<String> request = new ArrayList<>();
        if (shared && !call.main().equals(Worker.TOOL)) {
            request.add(Worker.SHARED);
        }
        request.add(call.main());
        request.addAll(call.arguments());
        if (shared || call.main().equals(Worker.TOOL)) {
            Shared worker;
            synchronized (SHARED) {
                worker = SHARED.get(key);
                if (worker == null || worker.ended) {
                    worker = new Shared(start(key, entries));
                    SHARED.put(key, worker);
                }
                worker.lasting |= lasting;
            }
            try (OutputStream out = Files.newOutputStream(output); OutputStream err = Files.newOutputStream(error)) {
                Lines printed = new Lines(out, tee == null ? null : tee.out()),
                        errored = new Lines(err, tee == null ? null : tee.err());
                int code = worker.execute(request, printed, errored);
                printed.close();
                errored.close();
                return code;
            }
        }
        Deque<Running> idle = IDLE.computeIfAbsent(key, _ -> new ConcurrentLinkedDeque<>());
        Running worker;
        do {
            worker = idle.pollFirst();
        } while (worker != null && !worker.process().isAlive());
        if (worker == null) {
            worker = start(key, entries);
        }
        boolean alive = false;
        try (OutputStream out = Files.newOutputStream(output); OutputStream err = Files.newOutputStream(error)) {
            Lines printed = new Lines(out, tee == null ? null : tee.out()),
                    errored = new Lines(err, tee == null ? null : tee.err());
            int code;
            try {
                Worker.request(worker.requests(), 0, request);
                code = Worker.receive(worker.frames(), printed, errored);
                alive = true;
            } catch (EOFException _) {
                try {
                    code = worker.process().waitFor();
                } catch (InterruptedException e) {
                    worker.process().destroyForcibly();
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(e);
                }
            }
            printed.close();
            errored.close();
            return code;
        } finally {
            if (alive) {
                idle.offerFirst(worker);
            } else {
                worker.process().destroyForcibly();
            }
        }
    }

    static Once once(Call call, Path aot) throws IOException {
        List<String> commands = new ArrayList<>(), classpath = new ArrayList<>();
        commands.add(call.java());
        commands.addAll(call.options());
        List<Path> entries = new ArrayList<>(List.of(classes()));
        if (!call.classpath().isEmpty()) {
            for (String entry : call.classpath().split(Pattern.quote(File.pathSeparator), -1)) {
                entries.add(Path.of(entry).toAbsolutePath());
            }
        }
        Path trained = null, cache = null;
        if (aot == null) {
            entries.forEach(entry -> classpath.add(entry.toString()));
        } else {
            MessageDigest digest;
            try {
                digest = MessageDigest.getInstance("SHA-256");
            } catch (NoSuchAlgorithmException e) {
                throw new IllegalStateException(e);
            }
            for (String value : Stream.concat(Stream.of(call.java(), Runtime.version().toString(), call.main()),
                    call.options().stream()).toList()) {
                digest.update(value.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
            }
            SequencedMap<String, Path> named = new LinkedHashMap<>();
            for (Path entry : entries) {
                if (!Files.exists(entry)) {
                    continue;
                }
                String name = named.size() + (Files.isDirectory(entry) ? ".jar" : "-" + entry.getFileName());
                named.put(name, entry);
                digest.update(name.getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                if (Files.isDirectory(entry)) {
                    try (Stream<Path> files = Files.walk(entry)) {
                        for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                            digest.update(entry.relativize(file).toString().getBytes(StandardCharsets.UTF_8));
                            digest.update(Files.readAllBytes(file));
                        }
                    }
                } else {
                    BasicFileAttributes attributes = Files.readAttributes(entry, BasicFileAttributes.class);
                    Object identity = attributes.fileKey();
                    digest.update(((identity == null ? entry.toString() : identity.toString())
                            + ":" + attributes.size()
                            + ":" + attributes.lastModifiedTime().toMillis()).getBytes(StandardCharsets.UTF_8));
                }
            }
            Path folder = aot.resolve(HexFormat.of().formatHex(digest.digest()).substring(0, 32));
            if (!Files.isDirectory(folder)) {
                Path written = Files.createTempDirectory(Files.createDirectories(aot), "linked");
                for (Map.Entry<String, Path> entry : named.entrySet()) {
                    Path target = written.resolve(entry.getKey());
                    if (Files.isDirectory(entry.getValue())) {
                        try (JarOutputStream out = new JarOutputStream(Files.newOutputStream(target));
                             Stream<Path> files = Files.walk(entry.getValue())) {
                            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                                out.putNextEntry(new JarEntry(entry.getValue().relativize(file).toString()
                                        .replace(File.separatorChar, '/')));
                                Files.copy(file, out);
                                out.closeEntry();
                            }
                        }
                    } else {
                        try {
                            Files.createLink(target, entry.getValue());
                        } catch (IOException | UnsupportedOperationException _) {
                            Files.copy(entry.getValue(), target);
                        }
                    }
                }
                try {
                    Files.move(written, folder, StandardCopyOption.ATOMIC_MOVE);
                } catch (FileAlreadyExistsException | DirectoryNotEmptyException _) {
                    try (Stream<Path> files = Files.walk(written)) {
                        for (Path file : files.sorted(Comparator.reverseOrder()).toList()) {
                            Files.delete(file);
                        }
                    }
                }
            }
            named.sequencedKeySet().forEach(name -> classpath.add(folder.resolve(name).toString()));
            cache = folder.resolve("cache.aot");
            if (Files.isRegularFile(cache)) {
                commands.add("-XX:AOTCache=" + cache);
            } else {
                trained = folder.resolve("cache-" + UUID.randomUUID() + ".aot");
                commands.add("-XX:AOTCacheOutput=" + trained);
                commands.add("-XX:+UnlockDiagnosticVMOptions");
                commands.add("-XX:-DisplayVMOutput");
            }
            commands.add("-Xlog:aot=off");
            commands.add("-Xlog:cds=off");
        }
        commands.add("-cp");
        commands.add(String.join(File.pathSeparator, classpath));
        commands.add(Worker.class.getName());
        commands.add(Worker.ONCE);
        commands.add(call.main());
        commands.addAll(call.arguments());
        return new Once(List.copyOf(commands), trained, cache);
    }

    private static synchronized Path classes() throws IOException {
        if (compiled == null) {
            Path copied = ProcessHandler.OfProcess.temporary("jenesis-java-worker");
            for (Class<?> member : Worker.class.getNestMembers()) {
                String name = member.getName().replace('.', '/') + ".class";
                try (InputStream input = Worker.class.getResourceAsStream("/" + name)) {
                    if (input == null) {
                        throw new IllegalStateException("Cannot read " + name + " of the engine to run a tool's main"
                                + " method in a JVM of its own");
                    }
                    Path target = copied.resolve(name);
                    Files.createDirectories(target.getParent());
                    Files.copy(input, target);
                }
            }
            compiled = copied;
        }
        return compiled;
    }

    record Once(List<String> commands, Path trained, Path cache) {

        void complete() throws IOException {
            if (trained != null) {
                Files.deleteIfExists(trained.resolveSibling(trained.getFileName() + ".config"));
                if (Files.isRegularFile(trained)) {
                    Files.move(trained, cache, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
            }
        }
    }

    private static Running start(Key key, List<String> entries) throws IOException {
        Path folder = classes();
        Path linked = Files.createTempDirectory(folder, "classpath");
        StringJoiner classpath = new StringJoiner(File.pathSeparator).add(folder.toString());
        for (int index = 0; index < entries.size(); index++) {
            Path path = Path.of(entries.get(index)).toAbsolutePath();
            if (Files.isRegularFile(path)) {
                Path target = linked.resolve(index + "-" + path.getFileName());
                try {
                    Files.createLink(target, path);
                } catch (IOException | UnsupportedOperationException _) {
                    Files.copy(path, target);
                }
                classpath.add(target.toString());
            } else {
                classpath.add(path.toString());
            }
        }
        List<String> commands = new ArrayList<>();
        commands.add(key.java());
        commands.addAll(key.options());
        commands.add("-cp");
        commands.add(classpath.toString());
        commands.add(Worker.class.getName());
        ProcessBuilder builder = new ProcessBuilder(commands).redirectError(ProcessBuilder.Redirect.DISCARD);
        builder.environment().clear();
        builder.environment().putAll(key.environment());
        builder.environment().putIfAbsent("COLUMNS", "80");
        builder.environment().putIfAbsent("LINES", "24");
        builder.environment().putIfAbsent("TERM", "dumb");
        Process process = builder.start();
        if (HOOKED.compareAndSet(false, true)) {
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                IDLE.values().forEach(workers -> workers.forEach(worker -> worker.process()
                        .destroyForcibly()
                        .onExit()
                        .join()));
                synchronized (SHARED) {
                    SHARED.values().forEach(shared -> shared.running.process().destroyForcibly().onExit().join());
                }
            }));
        }
        return new Running(process,
                new DataOutputStream(new BufferedOutputStream(process.getOutputStream())),
                new DataInputStream(new BufferedInputStream(process.getInputStream())));
    }

    static Closeable scope() {
        synchronized (SHARED) {
            scopes++;
        }
        AtomicBoolean closed = new AtomicBoolean();
        return () -> {
            if (!closed.compareAndSet(false, true)) {
                return;
            }
            List<Shared> ending = new ArrayList<>();
            List<Closeable> nested = new ArrayList<>();
            synchronized (SHARED) {
                if (--scopes == 0) {
                    SHARED.values().removeIf(worker -> !worker.lasting && ending.add(worker));
                    nested.addAll(NESTED);
                    NESTED.clear();
                }
            }
            ending.forEach(worker -> worker.running.process().destroy());
            IOException failure = null;
            for (Closeable closeable : nested) {
                try {
                    closeable.close();
                } catch (IOException e) {
                    if (failure == null) {
                        failure = e;
                    } else {
                        failure.addSuppressed(e);
                    }
                }
            }
            if (failure != null) {
                throw failure;
            }
        };
    }

    static boolean nest(Closeable nested) {
        synchronized (SHARED) {
            if (scopes > 0) {
                NESTED.add(nested);
                return true;
            }
        }
        return false;
    }

    record Call(String java, List<String> options, String classpath, String main, List<String> arguments) {
    }

    private record Key(String java, List<String> options, List<Object> classpath, SortedMap<String, String> environment) {
    }

    private record Running(Process process, DataOutputStream requests, DataInputStream frames) {
    }

    private static final class Shared implements Worker.Calls {

        private final Running running;
        private final AtomicInteger calls = new AtomicInteger();
        private final Map<Integer, Pending> pending = new ConcurrentHashMap<>();
        private volatile boolean ended;
        private boolean lasting;

        private Shared(Running running) {
            this.running = running;
            Thread.ofPlatform().daemon().name("jenesis-java-worker-frames").start(() -> {
                try {
                    Worker.dispatch(running.frames(), this);
                } catch (IOException _) {
                }
                ended = true;
                int code;
                try {
                    code = running.process().waitFor();
                } catch (InterruptedException _) {
                    running.process().destroyForcibly();
                    code = 1;
                }
                for (Pending call : pending.values()) {
                    call.done().complete(code);
                }
            });
        }

        private int execute(List<String> request, OutputStream out, OutputStream err) throws IOException {
            int call = calls.incrementAndGet();
            Pending registered = new Pending(out, err, new CompletableFuture<>());
            pending.put(call, registered);
            try {
                if (ended) {
                    return running.process().waitFor();
                }
                synchronized (running.requests()) {
                    Worker.request(running.requests(), call, request);
                }
                return registered.done().join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            } finally {
                pending.remove(call);
            }
        }

        @Override
        public OutputStream out(int call) {
            Pending target = pending.get(call);
            return target == null ? OutputStream.nullOutputStream() : target.out();
        }

        @Override
        public OutputStream err(int call) {
            Pending target = pending.get(call);
            return target == null ? OutputStream.nullOutputStream() : target.err();
        }

        @Override
        public boolean exit(int call, int code) {
            Pending target = pending.get(call);
            if (target != null) {
                target.done().complete(code);
            }
            return true;
        }
    }

    private record Pending(OutputStream out, OutputStream err, CompletableFuture<Integer> done) {
    }

    private static final class Lines extends OutputStream {

        private final OutputStream file;
        private final Consumer<String> consumer;
        private final ByteArrayOutputStream line = new ByteArrayOutputStream();

        private Lines(OutputStream file, Consumer<String> consumer) {
            this.file = file;
            this.consumer = consumer;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] { (byte) value }, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            file.write(bytes, offset, length);
            if (consumer == null) {
                return;
            }
            for (int index = offset; index < offset + length; index++) {
                if (bytes[index] == '\n') {
                    consumer.accept(line.toString(StandardCharsets.UTF_8));
                    line.reset();
                } else if (bytes[index] != '\r') {
                    line.write(bytes[index]);
                }
            }
        }

        @Override
        public void close() {
            if (consumer != null && line.size() > 0) {
                consumer.accept(line.toString(StandardCharsets.UTF_8));
            }
        }
    }
}

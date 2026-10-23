package build.jenesis.daemon;

import module java.base;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

public final class Worker {

    public static final String PORT = "daemon.port",
            TOKEN = "daemon.token",
            TOOL = "--tool",
            SHARED = "--shared",
            ONCE = "--once";
    public static final byte ACCEPTED = 'A', REJECTED = 'T', STALE = 'S', BUSY = 'B';
    private static final byte OUT = 'O', ERR = 'E', EXIT = 'X';
    private static final long IDLE = 600_000L;

    private Worker() {
    }

    public static void main(String... arguments) throws Exception {
        if (arguments.length == 0) {
            piped();
        } else if (arguments.length == 4 && arguments[0].equals("listen")) {
            listen(Path.of(arguments[1]), arguments[2], Duration.ofSeconds(Long.parseLong(arguments[3])));
        } else if (arguments.length > 1 && arguments[0].equals(ONCE)) {
            int code = call(List.of(arguments).subList(1, arguments.length), new HashMap<>());
            System.out.flush();
            System.err.flush();
            System.exit(code);
        } else {
            throw new IllegalArgumentException("Expected no argument, to serve calls on the standard streams, "
                    + ONCE + " <main class> <argument>..., to run one call, or listen <folder> <fingerprint>"
                    + " <idle seconds>, but got " + List.of(arguments));
        }
    }

    public static void request(DataOutputStream out, int call, List<String> request) throws IOException {
        out.writeInt(call);
        out.writeInt(request.size());
        for (String value : request) {
            byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
            out.writeInt(bytes.length);
            out.write(bytes);
        }
        out.flush();
    }

    public static int receive(DataInputStream in, OutputStream out, OutputStream err) throws IOException {
        int[] code = new int[1];
        dispatch(in, new Calls() {
            @Override
            public OutputStream out(int call) {
                return out;
            }

            @Override
            public OutputStream err(int call) {
                return err;
            }

            @Override
            public boolean exit(int call, int exit) {
                code[0] = exit;
                return false;
            }
        });
        return code[0];
    }

    public static void dispatch(DataInputStream in, Calls calls) throws IOException {
        while (true) {
            byte kind = in.readByte();
            int call = in.readInt();
            if (kind == EXIT) {
                if (!calls.exit(call, in.readInt())) {
                    return;
                }
                continue;
            }
            byte[] bytes = new byte[in.readInt()];
            in.readFully(bytes);
            switch (kind) {
                case OUT -> calls.out(call).write(bytes);
                case ERR -> calls.err(call).write(bytes);
                default -> throw new IllegalStateException("A warm JVM sent an unknown frame: " + kind);
            }
        }
    }

    public interface Calls {

        OutputStream out(int call) throws IOException;

        OutputStream err(int call) throws IOException;

        boolean exit(int call, int code) throws IOException;
    }

    private static void piped() throws IOException {
        DataInputStream requests = new DataInputStream(new BufferedInputStream(new FileInputStream(FileDescriptor.in)));
        DataOutputStream frames = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(FileDescriptor.out)));
        Routed out = new Routed(new Frames(frames, OUT, 0)), err = new Routed(new Frames(frames, ERR, 0));
        System.setOut(new PrintStream(out, true, StandardCharsets.UTF_8));
        System.setErr(new PrintStream(err, true, StandardCharsets.UTF_8));
        System.setIn(InputStream.nullInputStream());
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.flush();
            System.err.flush();
            synchronized (frames) {
                try {
                    frames.flush();
                } catch (IOException _) {
                }
            }
        }));
        AtomicLong last = new AtomicLong(System.currentTimeMillis());
        AtomicInteger busy = new AtomicInteger();
        Thread.ofPlatform().daemon().start(() -> {
            while (true) {
                try {
                    Thread.sleep(10_000L);
                } catch (InterruptedException _) {
                    return;
                }
                if (busy.get() == 0 && System.currentTimeMillis() - last.get() > IDLE) {
                    Runtime.getRuntime().halt(0);
                }
            }
        });
        Map<String, Class<?>> compiled = new ConcurrentHashMap<>();
        while (true) {
            int call;
            List<String> request;
            try {
                call = requests.readInt();
                request = read(requests);
            } catch (EOFException _) {
                Runtime.getRuntime().halt(0);
                return;
            }
            busy.incrementAndGet();
            PrintStream printed = new PrintStream(new Frames(frames, OUT, call), true, StandardCharsets.UTF_8),
                    errored = new PrintStream(new Frames(frames, ERR, call), true, StandardCharsets.UTF_8);
            Runnable respond = () -> {
                out.calls.set(printed);
                err.calls.set(errored);
                int code;
                if (request.getFirst().equals(TOOL)) {
                    try {
                        code = java.util.spi.ToolProvider.findFirst(request.get(1))
                                .orElseThrow(() -> new IllegalArgumentException("This JDK provides no tool named "
                                        + request.get(1)))
                                .run(printed, errored, request.subList(2, request.size()).toArray(String[]::new));
                    } catch (Throwable t) {
                        t.printStackTrace(errored);
                        code = 1;
                    }
                } else {
                    code = call(request.getFirst().equals(SHARED) ? request.subList(1, request.size()) : request, compiled);
                }
                System.out.flush();
                System.err.flush();
                out.calls.remove();
                err.calls.remove();
                try {
                    synchronized (frames) {
                        frames.writeByte(EXIT);
                        frames.writeInt(call);
                        frames.writeInt(code);
                        frames.flush();
                    }
                } catch (IOException _) {
                    Runtime.getRuntime().halt(1);
                }
                last.set(System.currentTimeMillis());
                busy.decrementAndGet();
            };
            if (request.getFirst().equals(TOOL) || request.getFirst().equals(SHARED)) {
                Thread.ofPlatform().start(respond);
            } else {
                respond.run();
            }
        }
    }

    private static void listen(Path folder, String fingerprint, Duration idle) throws IOException {
        Files.createDirectories(folder);
        byte[] token = new byte[32];
        new SecureRandom().nextBytes(token);
        ReentrantLock building = new ReentrantLock();
        Semaphore collect = new Semaphore(0);
        Map<String, Class<?>> compiled = new ConcurrentHashMap<>();
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            socket.setSoTimeout(Math.toIntExact(idle.toMillis()));
            Thread.ofPlatform().daemon().name("jenesis-worker-collector").start(() -> {
                while (true) {
                    try {
                        collect.acquire();
                    } catch (InterruptedException _) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                    collect.drainPermits();
                    System.gc();
                }
            });
            Path port = folder.resolve(PORT), secret = folder.resolve(TOKEN);
            write(secret, HexFormat.of().formatHex(token));
            write(port, Integer.toString(socket.getLocalPort()));
            try {
                while (!socket.isClosed()) {
                    Socket accepted;
                    try {
                        accepted = socket.accept();
                    } catch (SocketTimeoutException _) {
                        if (building.isLocked()) {
                            continue;
                        }
                        break;
                    } catch (SocketException _) {
                        break;
                    }
                    Thread.ofPlatform().start(() -> {
                        try {
                            exchange(socket, accepted, token, fingerprint, building, compiled);
                        } catch (IOException _) {
                            return;
                        }
                        collect.release();
                    });
                }
            } finally {
                discard(port, Integer.toString(socket.getLocalPort()));
                discard(secret, HexFormat.of().formatHex(token));
            }
        }
    }

    private static void exchange(ServerSocket server,
                                 Socket socket,
                                 byte[] token,
                                 String fingerprint,
                                 ReentrantLock building,
                                 Map<String, Class<?>> compiled) throws IOException {
        try (socket;
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()))) {
            if (!MessageDigest.isEqual(HexFormat.of().parseHex(in.readUTF()), token)) {
                out.writeByte(REJECTED);
                out.flush();
                return;
            }
            if (!in.readUTF().equals(fingerprint)) {
                out.writeByte(STALE);
                out.flush();
                server.close();
                return;
            }
            if (in.readBoolean()) {
                out.writeByte(ACCEPTED);
                out.writeByte(EXIT);
                out.writeInt(0);
                out.writeInt(0);
                out.writeLong(ProcessHandle.current().pid());
                out.flush();
                server.close();
                return;
            }
            int call = in.readInt();
            List<String> request = read(in);
            if (!building.tryLock()) {
                out.writeByte(BUSY);
                out.flush();
                return;
            }
            try {
                out.writeByte(ACCEPTED);
                PrintStream systemOut = System.out, systemErr = System.err;
                PrintStream printed = new PrintStream(new Frames(out, OUT, call), true, StandardCharsets.UTF_8),
                        errored = new PrintStream(new Frames(out, ERR, call), true, StandardCharsets.UTF_8);
                System.setOut(printed);
                System.setErr(errored);
                int code;
                try {
                    code = call(request, compiled);
                } finally {
                    printed.flush();
                    errored.flush();
                    System.setOut(systemOut);
                    System.setErr(systemErr);
                }
                synchronized (out) {
                    out.writeByte(EXIT);
                    out.writeInt(call);
                    out.writeInt(code);
                    out.flush();
                }
            } finally {
                building.unlock();
            }
        }
    }

    private static List<String> read(DataInputStream in) throws IOException {
        List<String> request = new ArrayList<>();
        for (int count = in.readInt(); count > 0; count--) {
            byte[] bytes = new byte[in.readInt()];
            in.readFully(bytes);
            request.add(new String(bytes, StandardCharsets.UTF_8));
        }
        return request;
    }

    private static int call(List<String> request, Map<String, Class<?>> compiled) {
        try {
            Object result = invoke(request.getFirst(),
                    request.subList(1, request.size()).toArray(String[]::new),
                    compiled);
            return result instanceof Integer code ? code : 0;
        } catch (InvocationTargetException e) {
            e.getCause().printStackTrace();
            return 1;
        } catch (Throwable t) {
            t.printStackTrace();
            return 1;
        }
    }

    private static Object invoke(String main, String[] arguments, Map<String, Class<?>> compiled) throws Exception {
        Class<?> type;
        if (main.endsWith(".java")) {
            Path source = Path.of(main);
            String key = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(source)))
                    + source.getFileName();
            type = compiled.get(key);
            if (type == null) {
                Path folder = Files.createTempDirectory("jenesis-java-worker-launcher");
                JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
                Map<String, byte[]> classes = new HashMap<>();
                try {
                    if (compiler.run(null,
                            null,
                            null,
                            "-d",
                            folder.toString(),
                            "-cp",
                            System.getProperty("java.class.path"),
                            source.toString()) != 0) {
                        throw new IllegalStateException("Cannot compile " + source);
                    }
                    try (Stream<Path> files = Files.walk(folder)) {
                        for (Path file : files.filter(file -> file.toString().endsWith(".class")).toList()) {
                            String binary = folder.relativize(file).toString().replace(File.separatorChar, '.');
                            classes.put(binary.substring(0, binary.length() - ".class".length()),
                                    Files.readAllBytes(file));
                        }
                    }
                } finally {
                    try (Stream<Path> files = Files.walk(folder)) {
                        files.sorted(Comparator.reverseOrder()).forEach(file -> file.toFile().delete());
                    }
                }
                String declared = source.getFileName().toString().replace(".java", "");
                String name = classes.containsKey(declared) ? declared : classes.keySet().stream()
                        .filter(candidate -> !candidate.contains("$"))
                        .sorted()
                        .findFirst()
                        .orElse(declared);
                ClassLoader loader = new ClassLoader(ClassLoader.getSystemClassLoader()) {
                    @Override
                    protected Class<?> findClass(String binary) throws ClassNotFoundException {
                        byte[] bytes = classes.get(binary);
                        if (bytes == null) {
                            throw new ClassNotFoundException(binary);
                        }
                        return defineClass(binary, bytes, 0, bytes.length);
                    }
                };
                type = loader.loadClass(name);
                compiled.put(key, type);
            }
        } else {
            type = Class.forName(main, false, ClassLoader.getSystemClassLoader());
        }
        Method method = null;
        for (Class<?>[] parameters : List.of(new Class<?>[] { String[].class }, new Class<?>[0])) {
            for (Class<?> current = type; current != null && method == null; current = current.getSuperclass()) {
                try {
                    Method candidate = current.getDeclaredMethod("main", parameters);
                    if (!Modifier.isPrivate(candidate.getModifiers())) {
                        method = candidate;
                    }
                } catch (NoSuchMethodException _) {
                }
            }
            if (method != null) {
                break;
            }
        }
        if (method == null) {
            throw new IllegalStateException(type.getName() + " declares no main method");
        }
        method.setAccessible(true);
        Object target = null;
        if (!Modifier.isStatic(method.getModifiers())) {
            Constructor<?> constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            target = constructor.newInstance();
        }
        return method.getParameterCount() == 1 ? method.invoke(target, (Object) arguments) : method.invoke(target);
    }

    private static void discard(Path file, String content) throws IOException {
        if (Files.isRegularFile(file) && Files.readString(file).equals(content)) {
            Files.deleteIfExists(file);
        }
    }

    private static void write(Path file, String content) throws IOException {
        Path written = Files.createTempFile(file.getParent(), file.getFileName().toString(), ".tmp");
        try {
            restrict(written);
            Files.writeString(written, content);
            Files.move(written, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(written);
        }
    }

    private static void restrict(Path file) throws IOException {
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE));
            return;
        } catch (UnsupportedOperationException _) {
            AclFileAttributeView acl = Files.getFileAttributeView(file, AclFileAttributeView.class);
            if (acl != null) {
                acl.setAcl(List.of(AclEntry.newBuilder()
                        .setType(AclEntryType.ALLOW)
                        .setPrincipal(Files.getOwner(file))
                        .setPermissions(EnumSet.allOf(AclEntryPermission.class))
                        .build()));
                return;
            }
        }
        throw new IOException("Cannot restrict " + file + " to its owner, refusing to expose the warm JVM"
                + " - this filesystem supports neither POSIX permissions nor access control lists");
    }

    private static final class Routed extends OutputStream {

        private final InheritableThreadLocal<OutputStream> calls = new InheritableThreadLocal<>();
        private final OutputStream fallback;

        private Routed(OutputStream fallback) {
            this.fallback = fallback;
        }

        private OutputStream target() {
            OutputStream target = calls.get();
            return target == null ? fallback : target;
        }

        @Override
        public void write(int value) throws IOException {
            target().write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            target().write(bytes, offset, length);
        }

        @Override
        public void flush() throws IOException {
            target().flush();
        }
    }

    private static final class Frames extends OutputStream {

        private final DataOutputStream frames;
        private final byte kind;
        private final int call;

        private Frames(DataOutputStream frames, byte kind, int call) {
            this.frames = frames;
            this.kind = kind;
            this.call = call;
        }

        @Override
        public void write(int value) throws IOException {
            write(new byte[] { (byte) value }, 0, 1);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) throws IOException {
            if (length == 0) {
                return;
            }
            synchronized (frames) {
                frames.writeByte(kind);
                frames.writeInt(call);
                frames.writeInt(length);
                frames.write(bytes, offset, length);
            }
        }

        @Override
        public void flush() throws IOException {
            synchronized (frames) {
                frames.flush();
            }
        }
    }
}

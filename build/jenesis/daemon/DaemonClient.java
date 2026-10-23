package build.jenesis.daemon;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.HashDigestFunction;
import build.jenesis.Make;
import build.jenesis.SequencedProperties;

public final class DaemonClient {

    private static final Set<String> VOLATILE = Set.of("_",
            "PWD",
            "OLDPWD",
            "SHLVL",
            "COLUMNS",
            "LINES",
            "WINDOWID",
            "TERM_SESSION_ID",
            "SHELL_SESSION_ID");

    private final Path root;
    private final Path folder;
    private final List<String> path;
    private final String mainClass;
    private final SequencedMap<String, String> supplied;
    private final String options;
    private final long idle;
    private final SequencedMap<String, String> outputs;

    public DaemonClient(Path root, List<String> path, String mainClass, SequencedMap<String, String> supplied) {
        this(root,
                path,
                mainClass,
                supplied,
                new Environment(Make.keys(effective(supplied))).value("daemon.options", "-Xmx2g"),
                new Environment(Make.keys(effective(supplied))).number("daemon.idle", 10_800L),
                null);
    }

    private DaemonClient(Path root,
                         List<String> path,
                         String mainClass,
                         SequencedMap<String, String> supplied,
                         String options,
                         long idle,
                         SequencedMap<String, String> outputs) {
        this.root = root.toAbsolutePath().normalize();
        this.path = path;
        this.mainClass = mainClass;
        this.supplied = supplied;
        this.options = options;
        this.idle = idle;
        this.outputs = outputs;
        folder = this.root.resolve(".jenesis");
    }

    public DaemonClient options(String options) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, outputs);
    }

    public DaemonClient idle(long idle) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, outputs);
    }

    public DaemonClient outputs(SequencedMap<String, String> outputs) {
        return new DaemonClient(root, path, mainClass, supplied, options, idle, outputs);
    }

    private static SequencedMap<String, String> effective(SequencedMap<String, String> supplied) {
        SequencedMap<String, String> effective = new LinkedHashMap<>(supplied);
        for (String name : System.getProperties().stringPropertyNames()) {
            if (name.startsWith("jenesis.")) {
                effective.put(name, System.getProperty(name));
            }
        }
        return effective;
    }

    public static int doDispatch(Path root,
                                 List<String> path,
                                 String mainClass,
                                 String seed,
                                 SequencedMap<String, String> supplied,
                                 SequencedMap<String, String> outputs,
                                 String... selectors) throws IOException, InterruptedException {
        return new DaemonClient(root, path, mainClass, supplied).outputs(outputs).dispatch(seed, selectors);
    }

    public int dispatch(String seed, String... selectors) throws IOException, InterruptedException {
        boolean stop = selectors.length == 1 && selectors[0].equals("--stop");
        String[] arguments = stop ? new String[0] : selectors;
        SequencedMap<String, String> environment = new TreeMap<>(System.getenv())
                .entrySet()
                .stream()
                .filter(entry -> !VOLATILE.contains(entry.getKey()))
                .collect(LinkedHashMap::new, (map, entry) -> map.put(entry.getKey(), entry.getValue()), Map::putAll);
        for (String argument : ProcessHandle.current().info().arguments().orElse(new String[0])) {
            if (argument.startsWith("-D") && !argument.startsWith("-Djenesis.")
                    || argument.startsWith("-X")
                    || argument.equals("-ea")) {
                environment.put(argument, "");
            }
        }
        String fingerprint = fingerprint(seed.isBlank()
                ? Stream.of(path.getLast().split(File.pathSeparator)).map(Path::of).toList()
                : List.of(), seed, environment);
        for (int attempt = 0; attempt < 2; attempt++) {
            Integer code = request(fingerprint, stop, arguments);
            if (code != null) {
                return code;
            }
            if (stop) {
                return 0;
            }
            start(fingerprint);
        }
        throw new IllegalStateException("The build daemon under " + folder + " refused two consecutive requests"
                + " - see daemon.log there, or set -Djenesis.make.mode=compiled to build without it");
    }

    private static String fingerprint(Collection<Path> files, String seed, SequencedMap<String, String> environment)
            throws IOException {
        HashDigestFunction function = new HashDigestFunction("SHA-256");
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        digest.update(seed.getBytes(StandardCharsets.UTF_8));
        digest.update(Runtime.version().toString().getBytes(StandardCharsets.UTF_8));
        environment.forEach((name, value) -> {
            digest.update(name.getBytes(StandardCharsets.UTF_8));
            digest.update(value.getBytes(StandardCharsets.UTF_8));
        });
        for (Path file : new TreeSet<>(files)) {
            digest.update(file.toString().getBytes(StandardCharsets.UTF_8));
            if (Files.isDirectory(file)) {
                try (Stream<Path> walk = Files.walk(file, FileVisitOption.FOLLOW_LINKS)) {
                    for (Path nested : walk.filter(Files::isRegularFile).sorted().toList()) {
                        digest.update(file.relativize(nested).toString().getBytes(StandardCharsets.UTF_8));
                        digest.update(function.hash(nested));
                    }
                }
            } else if (Files.isRegularFile(file)) {
                digest.update(function.hash(file));
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private Integer request(String fingerprint, boolean stop, String... selectors)
            throws IOException, InterruptedException {
        if (outputs != null) {
            outputs.clear();
        }
        Path port = folder.resolve(Worker.PORT), secret = folder.resolve(Worker.TOKEN);
        if (!Files.isRegularFile(port) || !Files.isRegularFile(secret)) {
            return null;
        }
        Socket socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(
                    InetAddress.getLoopbackAddress(),
                    Integer.parseInt(Files.readString(port).trim())), 2_000);
        } catch (IOException _) {
            socket.close();
            return null;
        }
        Path produced = outputs == null || stop ? null : Files.createTempFile(folder, "daemon", ".outputs");
        try (socket;
             DataOutputStream out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
             DataInputStream in = new DataInputStream(new BufferedInputStream(socket.getInputStream()))) {
            out.writeUTF(Files.readString(secret).trim());
            out.writeUTF(fingerprint);
            out.writeBoolean(stop);
            if (!stop) {
                List<String> request = new ArrayList<>(List.of(DaemonBuild.class.getName(),
                        root.toString(),
                        mainClass,
                        produced == null ? "" : produced.toString()));
                effective(supplied).forEach((key, value) -> request.add("-D" + key + "=" + value));
                request.add("--");
                request.addAll(List.of(selectors));
                Worker.request(out, 0, request);
            }
            out.flush();
            byte status;
            try {
                status = in.readByte();
            } catch (EOFException _) {
                return null;
            }
            switch (status) {
                case Worker.REJECTED -> throw new IllegalStateException("The build daemon rejected this client's token"
                        + " - delete .jenesis/daemon.token and .jenesis/daemon.port, then build again");
                case Worker.STALE -> {
                    return null;
                }
                case Worker.BUSY -> throw new IllegalStateException("Another build process is already building "
                        + root.resolve(effective(supplied).getOrDefault("jenesis.project.target", "target"))
                                .toAbsolutePath()
                                .normalize()
                        + ": concurrent builds over one target folder interfere with each other's staged steps");
                case Worker.ACCEPTED -> {
                }
                default -> throw new IllegalStateException("The build daemon answered with an unknown status: " + status);
            }
            int code;
            try {
                code = Worker.receive(in, new Forward(System.out), new Forward(System.err));
            } catch (EOFException e) {
                throw new IllegalStateException("The build daemon died while the build was running"
                        + " - its output above is incomplete and the build did not finish", e);
            }
            if (stop) {
                long pid = in.readLong();
                Optional<ProcessHandle> daemon = ProcessHandle.of(pid);
                if (daemon.isPresent()) {
                    try {
                        daemon.get().onExit().get(30, TimeUnit.SECONDS);
                    } catch (ExecutionException | TimeoutException e) {
                        throw new IllegalStateException("The build daemon under " + folder + " acknowledged the stop"
                                + " but its process " + pid + " did not end within 30 seconds - end it, and see"
                                + " daemon.log there", e);
                    }
                }
            }
            if (produced != null && code == 0 && Files.size(produced) > 0) {
                SequencedProperties.ofFiles(produced).forEachProperty(outputs::put);
            }
            return code;
        } finally {
            if (produced != null) {
                Files.deleteIfExists(produced);
            }
        }
    }

    private void start(String fingerprint) throws IOException, InterruptedException {
        Path port = folder.resolve(Worker.PORT);
        Files.createDirectories(folder);
        if (Files.isRegularFile(port)) {
            request(fingerprint, true);
        }
        Files.deleteIfExists(port);
        List<String> command = new ArrayList<>();
        command.add(ProcessHandle.current().info().command().orElseThrow(() -> new IllegalStateException(
                "Cannot resolve the running Java executable to start a build daemon"
                        + " - set -Djenesis.make.mode=compiled to build without one")));
        if (!options.isBlank()) {
            command.addAll(List.of(options.trim().split("\\s+")));
        }
        command.addAll(path);
        if (path.getFirst().equals("-p")) {
            command.addAll(List.of("-m", Worker.class.getModule().getName() + "/" + Worker.class.getName()));
        } else {
            command.add(Worker.class.getName());
        }
        command.addAll(List.of("listen", folder.toString(), fingerprint, Long.toString(idle)));
        new ProcessBuilder(command)
                .directory(root.toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.appendTo(folder.resolve("daemon.log").toFile()))
                .start();
        for (int attempt = 0; attempt < 400 && !Files.isRegularFile(port); attempt++) {
            Thread.sleep(25);
        }
        if (!Files.isRegularFile(port)) {
            throw new IllegalStateException("A build daemon was started but never announced a port in " + port
                    + " - see " + folder.resolve("daemon.log"));
        }
    }

    private static final class Forward extends OutputStream {

        private final PrintStream stream;

        private Forward(PrintStream stream) {
            this.stream = stream;
        }

        @Override
        public void write(int value) {
            stream.write(value);
        }

        @Override
        public void write(byte[] bytes, int offset, int length) {
            stream.write(bytes, offset, length);
            stream.flush();
        }
    }
}

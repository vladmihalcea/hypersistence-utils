package build.jenesis.step;

import module java.base;
import module java.xml;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;
import build.jenesis.Palette;
import build.jenesis.Permits;
import build.jenesis.SequencedProperties;

public abstract class ProcessBuildStep implements BuildStep {

    public static final String PROCESS = "process/", ENVIRONMENT = "environment/", INDEX = "index.properties";
    private static final SAXParserFactory REPORTS = reports();
    private static final ConcurrentMap<Integer, Permits> PERMITS = new ConcurrentHashMap<>();
    private static final int TAIL = 200;
    private static final int KILLED = 128 + 9;
    private static final Pattern UNQUOTED = Pattern.compile("[A-Za-z0-9_@%+=:,./-]+");

    static {
        if (System.getProperty("java.home") == null) {
            String home = System.getenv("JAVA_HOME");
            if (home == null) {
                throw new IllegalStateException("Neither java.home or JAVA_HOME available");
            }
            System.setProperty("java.home", home);
        }
    }

    protected final transient Function<List<String>, ? extends ProcessHandler> factory;
    private final String command;
    protected final transient Terms terms;

    protected ProcessBuildStep(String command, Function<List<String>, ? extends ProcessHandler> factory) {
        this(command, factory, new Terms());
    }

    protected ProcessBuildStep(String command,
                               Function<List<String>, ? extends ProcessHandler> factory,
                               Terms terms) {
        this.command = command;
        this.factory = factory;
        this.terms = terms;
    }

    public record Terms(BiConsumer<Boolean, String> printing,
                        Permits permits,
                        Consumer<String> announcing,
                        Consumer<String> reporting,
                        int tail,
                        boolean daemon,
                        boolean shared,
                        Path aot) {

        public Terms() {
            this(null,
                    PERMITS.computeIfAbsent(Runtime.getRuntime().availableProcessors(), Permits::of),
                    null,
                    null,
                    TAIL,
                    false,
                    true,
                    null);
        }

        public static Terms ofEnvironment(Environment environment, String command) {
            return ofEnvironment(environment, command, false);
        }

        public static Terms ofEnvironment(Environment environment,
                                   String command,
                                   boolean printing) {
            int tail = environment.number("process.tail", TAIL);
            if (tail < 0) {
                throw new IllegalArgumentException("jenesis.process.tail is " + tail
                        + ", but names how many lines of a failed tool's output to print: 0 or more, 0 printing all");
            }
            boolean streamed = environment.flag("print." + command,
                    environment.flag("print.process", printing));
            String mode = environment.value("process.mode", "fork").toLowerCase(Locale.ROOT);
            if (!List.of("fork", "aot", "daemon").contains(mode)) {
                throw new IllegalArgumentException("Malformed value for jenesis.process.mode: '" + mode
                        + "' (expected fork, aot or daemon)");
            }
            Consumer<String> out = environment.out();
            Palette palette = Palette.ofEnvironment(environment);
            return new Terms(streamed
                    ? (error, line) -> out.accept((error ? palette.error() : palette.output())
                            + command + " >>>> " + line + palette.reset())
                    : null,
                    ProcessBuildStep.permits(environment),
                    environment.flag("print.command")
                            ? executed -> out.accept("%s%-11s%s %s".formatted(
                                    palette.info(), "[EXECUTED]", palette.reset(), executed))
                            : null,
                    environment.flag("print.findings", true)
                            ? found -> out.accept("%s%-11s%s %s".formatted(
                                    palette.warning(), "[FINDINGS]", palette.reset(), found))
                            : null,
                    tail,
                    mode.equals("daemon"),
                    environment.flag("process.shared", true),
                    mode.equals("aot")
                            ? Path.of(environment.value("aot.folder", ".jenesis/aot")).toAbsolutePath()
                            : null);
        }

        public Terms printing(BiConsumer<Boolean, String> printing) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }

        public Terms permits(Permits permits) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }

        public Terms announcing(Consumer<String> announcing) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }

        public Terms reporting(Consumer<String> reporting) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }

        public Terms tail(int tail) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }

        public Terms daemon(boolean daemon) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }

        public Terms shared(boolean shared) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }

        public Terms aot(Path aot) {
            return new Terms(printing, permits, announcing, reporting, tail, daemon, shared, aot);
        }
    }

    protected List<String> configurations() {
        return factory instanceof ProcessHandler.Staged ? List.of() : List.of(command);
    }

    protected ProcessHandler handler(BuildStepContext context, List<String> commands) throws IOException {
        ProcessHandler handler = factory.apply(commands);
        return handler instanceof ProcessHandler.OfProcess process
                ? process.daemon(process.daemon() && terms.daemon()).aot(terms.aot())
                : handler;
    }

    protected ProcessHandler environment(ProcessHandler handler, SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> variables = variables(arguments);
        if (!variables.isEmpty()) {
            throw new IllegalStateException("An environment file hands " + variables.keySet() + " to " + command
                    + ", which takes no environment: only a program the build runs in a process of its own does"
                    + " - a forked JVM, the test run, PIT and native-image");
        }
        return handler;
    }

    protected SequencedMap<String, String> variables(SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> variables = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (String name : configurations()) {
                Path file = locate(argument.folder(), ENVIRONMENT, name);
                if (file != null) {
                    SequencedProperties.ofFiles(file).forEachProperty(variables::put);
                }
            }
        }
        return variables;
    }

    protected int execute(ProcessHandler handler, Path output, Path error, ProcessHandler.Tee tee)
            throws IOException, InterruptedException {
        if (handler instanceof ProcessHandler.OfProcess process && process.shared()) {
            handler = process.scoped(terms.shared());
        }
        Permits permits = terms.permits();
        if (permits == null) {
            return handler.execute(output, error, tee);
        }
        permits.acquire();
        try {
            return handler.execute(output, error, tee);
        } finally {
            permits.release();
        }
    }

    protected ProcessHandler.Tee tee(Executor executor, ProcessHandler handler) {
        BiConsumer<Boolean, String> printing = terms.printing();
        if (printing == null) {
            return null;
        }
        printing.accept(false, shell(handler.commands()));
        return new ProcessHandler.Tee(executor,
                line -> printing.accept(false, line),
                line -> printing.accept(true, line));
    }

    protected abstract CompletionStage<List<String>> process(Executor executor,
                                                             BuildStepContext context,
                                                             SequencedMap<String, BuildStepArgument> arguments,
                                                             SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException;

    protected SequencedMap<String, SequencedMap<String, String>> properties(
            SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        SequencedMap<String, SequencedMap<String, String>> properties = new LinkedHashMap<>();
        for (Map.Entry<String, BuildStepArgument> entry : arguments.entrySet()) {
            if (entry.getValue().removed()) {
                continue;
            }
            SequencedMap<String, String> folderMap = new LinkedHashMap<>();
            for (String name : configurations()) {
                Path file = locate(entry.getValue().folder(), PROCESS, name);
                if (file != null) {
                    SequencedProperties.ofFiles(file).forEachProperty(folderMap::put);
                }
            }
            properties.put(entry.getKey(), folderMap);
        }
        return properties;
    }

    public static Permits permits(Environment environment) {
        int concurrency = environment.number("process.concurrency", Runtime.getRuntime().availableProcessors());
        if (concurrency < 0) {
            throw new IllegalArgumentException("Process concurrency must not be negative: " + concurrency);
        }
        return concurrency == 0 ? null : PERMITS.computeIfAbsent(concurrency, Permits::of);
    }

    public static Path locate(Path folder, String kind, String tool) throws IOException {
        Path index = folder.resolve(kind).resolve(INDEX);
        if (!Files.isRegularFile(index)) {
            return null;
        }
        String file = SequencedProperties.ofFiles(index).getProperty(tool);
        return file == null ? null : folder.resolve(kind).resolve(file);
    }

    public static void store(Path folder, String kind, String tool, SequencedProperties properties) throws IOException {
        Path target = Files.createDirectories(folder.resolve(kind)), index = target.resolve(INDEX);
        SequencedProperties tools = Files.isRegularFile(index) ? SequencedProperties.ofFiles(index) : new SequencedProperties();
        String file = tools.getProperty(tool, tool + ".properties");
        properties.store(target.resolve(file));
        tools.setProperty(tool, file);
        tools.store(index);
    }

    protected static ForkedOptions forked(SequencedMap<String, SequencedMap<String, String>> properties) {
        List<String> jvm = new ArrayList<>(), program = new ArrayList<>();
        for (String option : prepended(properties)) {
            if (option.startsWith("-J")) {
                jvm.add(option.substring(2));
            } else if (option.startsWith("-D")) {
                jvm.add(option);
            } else {
                program.add(option);
            }
        }
        properties.values().forEach(Map::clear);
        return new ForkedOptions(jvm, program);
    }

    protected record ForkedOptions(List<String> jvm, List<String> program) {
    }

    protected static List<String> prepended(SequencedMap<String, SequencedMap<String, String>> properties) {
        List<String> prepended = new ArrayList<>();
        for (SequencedMap<String, String> folderMap : properties.values()) {
            for (Map.Entry<String, String> entry : folderMap.entrySet()) {
                for (String value : entry.getValue().split("\n")) {
                    prepended.add(entry.getKey());
                    if (!value.isEmpty()) {
                        prepended.addAll(List.of(value.split("\t")));
                    }
                }
            }
        }
        return prepended;
    }

    private static SAXParserFactory reports() {
        SAXParserFactory factory = SAXParserFactory.newDefaultInstance();
        try {
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        } catch (ParserConfigurationException | SAXException e) {
            throw new IllegalStateException(e);
        }
        return factory;
    }

    protected static boolean parsed(Path report, DefaultHandler handler) throws IOException {
        try {
            REPORTS.newSAXParser().parse(report.toFile(), handler);
            return true;
        } catch (SAXException _) {
            return false;
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException(e);
        }
    }

    protected Optional<String> diagnosis(BuildStepContext context) {
        return Optional.empty();
    }

    public boolean acceptableExitCode(int code,
                                      Executor executor,
                                      BuildStepContext context,
                                      SequencedMap<String, BuildStepArgument> arguments) throws IOException {
        return code == 0;
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, SequencedMap<String, String>> properties = properties(arguments);
        AtomicReference<Thread> worker = new AtomicReference<>();
        CompletableFuture<BuildStepResult> result = process(executor, context, arguments, properties).thenComposeAsync(processed -> {
            if (processed == null) {
                return CompletableFuture.completedStage(new BuildStepResult(true));
            }
            CompletableFuture<BuildStepResult> future = new CompletableFuture<>();
            try {
                List<String> commands = prepended(properties);
                commands.addAll(processed);
                Path output = context.supplement().resolve("output"), error = context.supplement().resolve("error");
                ProcessHandler handler = environment(handler(context, commands), arguments);
                String executed = shell(handler.commands());
                Files.writeString(context.supplement().resolve("command"), executed);
                ProcessHandler.Tee tee = tee(executor, handler);
                Consumer<String> announcing = terms.announcing();
                if (announcing != null) {
                    announcing.accept(executed);
                }
                executor.execute(() -> {
                    worker.set(Thread.currentThread());
                    try {
                        int exitCode = execute(handler, output, error, tee);
                        if (acceptableExitCode(exitCode, executor, context, arguments)) {
                            future.complete(new BuildStepResult(true));
                        } else {
                            throw new IllegalStateException("Unexpected exit code: " + exitCode + "\n"
                                    + (exitCode == KILLED && handler.external()
                                            ? "The process was killed with SIGKILL, possibly by the kernel's"
                                                    + " out-of-memory killer: lower jenesis.process.concurrency, or the"
                                                    + " heap of the JVM it runs, as -Xmx in process-test.properties for"
                                                    + " the tests\n"
                                            : "")
                                    + diagnosis(context).map(diagnosis -> diagnosis + "\n").orElse("")
                                    + "To reproduce, execute:\n "
                                    + executed
                                    + tail("Output", output)
                                    + tail("Error", error));
                        }
                    } catch (Throwable t) {
                        future.completeExceptionally(t);
                    } finally {
                        worker.set(null);
                    }
                });
                return future;
            } catch (Throwable t) {
                future.completeExceptionally(t);
            }
            return future;
        }).toCompletableFuture();
        result.whenComplete((_, throwable) -> {
            if (throwable != null) {
                Thread running = worker.get();
                if (running != null && running != Thread.currentThread()) {
                    running.interrupt();
                }
            }
        });
        return result;
    }

    protected String tail(String label, Path file) throws IOException {
        if (!Files.exists(file)) {
            return "";
        }
        Deque<String> lines = new ArrayDeque<>();
        long total = 0;
        boolean blank = true;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Files.newInputStream(file),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                total++;
                blank &= line.isBlank();
                lines.addLast(line);
                if (terms.tail() > 0 && lines.size() > terms.tail()) {
                    lines.removeFirst();
                }
            }
        }
        if (blank) {
            return "";
        }
        return "\n\n" + label
                + (lines.size() < total
                        ? ", the last " + lines.size() + " out of " + total + " lines - " + file + " holds all of them"
                        : "")
                + ":\n" + String.join("\n", lines) + "\n";
    }

    protected static String shell(List<String> words) {
        return words.stream()
                .map(word -> UNQUOTED.matcher(word).matches() ? word : "'" + word.replace("'", "'\\''") + "'")
                .collect(Collectors.joining(" "));
    }

    public static List<String> argumentFile(Path file, SequencedMap<String, String> options) throws IOException {
        List<String> arguments = new ArrayList<>();
        options.forEach((option, value) -> {
            if (value != null && !value.isEmpty()) {
                arguments.add(option);
                arguments.add(value);
            }
        });
        if (arguments.isEmpty()) {
            return List.of();
        }
        return List.of("@" + argumentFile(file, arguments));
    }

    public static Path argumentFile(Path file, List<String> arguments) throws IOException {
        StringBuilder args = new StringBuilder();
        for (String argument : arguments) {
            args.append('"')
                    .append(argument.replace("\\", "\\\\").replace("\"", "\\\""))
                    .append("\"\n");
        }
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(file, args.toString());
        return file;
    }
}

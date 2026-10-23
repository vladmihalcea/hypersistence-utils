package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;

public class JMod extends ProcessBuildStep {

    public static final String JMODS = "jmods/",
            CONFIG = "jmodconfig/",
            LIBRARIES = "jmodlibs/",
            COMMANDS = "jmodcmds/";

    private final OffsetDateTime timestamp;

    public JMod(ProcessHandler.Factory factory) {
        this(factory.apply("jmod", "bin/jmod"),
             BuildStep.timestamp(),
             new Terms());
    }

    public static JMod ofEnvironment(Environment environment,
                                     ProcessHandler.Factory factory) {
        return new JMod(factory.apply("jmod", "bin/jmod"),
                BuildStep.timestamp(environment),
                Terms.ofEnvironment(environment, "jmod"));
    }

    private JMod(Function<List<String>, ? extends ProcessHandler> factory,
                 OffsetDateTime timestamp,
                 Terms terms) {
        super("jmod", factory, terms);
        this.timestamp = timestamp;
    }

    public JMod verbose(BiConsumer<Boolean, String> printing) {
        return new JMod(factory, timestamp, terms.printing(printing));
    }

    public JMod timestamp(OffsetDateTime timestamp) {
        return new JMod(factory, timestamp, terms);
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        List<String> classPath = new ArrayList<>(),
                config = new ArrayList<>(),
                libs = new ArrayList<>(),
                cmds = new ArrayList<>(),
                legal = new ArrayList<>();
        String moduleName = null;
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path classes = argument.folder().resolve(BuildStep.CLASSES);
            if (Files.isDirectory(classes)) {
                classPath.add(classes.toString());
                Path moduleInfo = classes.resolve("module-info.class");
                if (moduleName == null && Files.exists(moduleInfo)) {
                    try (InputStream in = Files.newInputStream(moduleInfo)) {
                        moduleName = ModuleDescriptor.read(in).name();
                    }
                }
            }
            collect(argument.folder().resolve(BuildStep.RESOURCES), classPath);
            collect(argument.folder().resolve(CONFIG), config);
            collect(argument.folder().resolve(LIBRARIES), libs);
            collect(argument.folder().resolve(COMMANDS), cmds);
            collect(argument.folder().resolve(Legal.LEGAL), legal);
        }
        if (moduleName == null) {
            return CompletableFuture.completedStage(null);
        }
        List<String> commands = new ArrayList<>(List.of("create"));
        if (timestamp != null) {
            commands.add("--date=" + timestamp);
        }
        option(commands, "--class-path", classPath);
        option(commands, "--config", config);
        option(commands, "--libs", libs);
        option(commands, "--cmds", cmds);
        option(commands, "--legal-notices", legal);
        commands.add(Files.createDirectory(context.next().resolve(JMODS)).resolve(moduleName + ".jmod").toString());
        return CompletableFuture.completedStage(commands);
    }

    private static void collect(Path folder, List<String> into) {
        if (Files.isDirectory(folder)) {
            into.add(folder.toString());
        }
    }

    private static void option(List<String> commands, String name, List<String> paths) {
        if (paths.isEmpty()) {
            return;
        }
        for (String entry : paths) {
            if (entry.indexOf(File.pathSeparatorChar) != -1) {
                throw new IllegalArgumentException(
                        "Path entry contains separator '" + File.pathSeparator + "': " + entry);
            }
        }
        commands.add(name);
        commands.add(String.join(File.pathSeparator, paths));
    }
}

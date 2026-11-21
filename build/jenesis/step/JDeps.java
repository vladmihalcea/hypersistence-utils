package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;

public class JDeps extends ProcessBuildStep {

    public static final String ANALYZED = "analyzed/", MODULES = "modules/", DESCRIPTORS = "descriptors/";

    public JDeps(ProcessHandler.Factory factory) {
        this(factory.apply("jdeps", "bin/jdeps"), new Terms());
    }

    public static JDeps ofEnvironment(Environment environment,
                                      ProcessHandler.Factory factory) {
        return new JDeps(factory.apply("jdeps", "bin/jdeps"), Terms.ofEnvironment(environment, "jdeps"));
    }

    private JDeps(Function<List<String>, ? extends ProcessHandler> factory, Terms terms) {
        super("jdeps", factory, terms);
    }

    public JDeps verbose(BiConsumer<Boolean, String> printing) {
        return new JDeps(factory, terms.printing(printing));
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        SequencedSet<String> analyzed = new TreeSet<>(), modules = new LinkedHashSet<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path candidates = argument.folder().resolve(ANALYZED);
            if (Files.isDirectory(candidates)) {
                try (DirectoryStream<Path> files = Files.newDirectoryStream(candidates)) {
                    for (Path file : files) {
                        if (file.getFileName().toString().endsWith(".jar")) {
                            analyzed.add(file.toString());
                        }
                    }
                }
            }
            Path observable = argument.folder().resolve(MODULES);
            if (Files.isDirectory(observable)) {
                modules.add(observable.toString());
            }
        }
        if (analyzed.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        List<String> commands = new ArrayList<>();
        if (!modules.isEmpty()) {
            for (String entry : modules) {
                if (entry.indexOf(File.pathSeparatorChar) != -1) {
                    throw new IllegalArgumentException(
                            "Path entry contains separator '" + File.pathSeparator + "': " + entry);
                }
            }
            commands.add("--module-path");
            commands.add(String.join(File.pathSeparator, modules));
        }
        commands.add("--generate-module-info");
        commands.add(Files.createDirectory(context.next().resolve(DESCRIPTORS)).toString());
        commands.addAll(analyzed);
        return CompletableFuture.completedStage(commands);
    }
}

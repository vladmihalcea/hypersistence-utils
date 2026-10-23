package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.ChecksumStatus;
import build.jenesis.HashDigestFunction;
import build.jenesis.HashFunction;

public abstract class FormatBuildStep extends ProcessBuildStep {

    private static final String FORMATTED = "formatted.properties";

    private final String tool;
    private final boolean verify;

    protected FormatBuildStep(String command, String tool, boolean verify) {
        this(command, tool, verify, new Terms());
    }

    protected FormatBuildStep(String command,
                              String tool,
                              boolean verify,
                              Terms terms) {
        super(command, ProcessHandler.OfProcess.ofJavaMain(), terms);
        this.tool = tool;
        this.verify = verify;
    }

    protected abstract boolean isFormattable(Path file);

    protected abstract List<String> command(BuildStepContext context,
                                            List<String> jars,
                                            Path config,
                                            List<String> files,
                                            boolean verify) throws IOException;

    protected Path config(Path folder) {
        return null;
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return !verify || arguments.values().stream().anyMatch(BuildStepArgument::hasChanged);
    }

    @Override
    public boolean acceptableExitCode(int code,
                                      Executor executor,
                                      BuildStepContext context,
                                      SequencedMap<String, BuildStepArgument> arguments) {
        return !verify || code == 0;
    }

    @Override
    protected CompletionStage<List<String>> process(Executor executor,
                                                    BuildStepContext context,
                                                    SequencedMap<String, BuildStepArgument> arguments,
                                                    SequencedMap<String, SequencedMap<String, String>> properties)
            throws IOException {
        HashFunction hash = new HashDigestFunction("SHA-256");
        List<String> jars = new ArrayList<>();
        Path config = null;
        Map<Path, byte[]> current = new LinkedHashMap<>();
        Map<Path, Path> located = new HashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            for (Path jar : Dependencies.select(argument.folder(), tool, "runtime")) {
                jars.add(jar.toString());
            }
            Path candidate = config(argument.folder());
            if (candidate != null) {
                config = candidate;
            }
            Map<Path, byte[]> formattable = formattable(argument.folder(), hash, executor);
            current.putAll(formattable);
            Path sources = argument.folder().resolve(Bind.SOURCES);
            formattable.keySet().forEach(file -> located.put(file, sources.resolve(file)));
        }
        if (current.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        boolean reconfigured = arguments.values().stream().anyMatch(argument -> argument.files().entrySet().stream()
                .anyMatch(entry -> entry.getValue().status() != ChecksumStatus.RETAINED
                        && !(entry.getKey().startsWith(Bind.SOURCES) && isFormattable(entry.getKey()))));
        Map<Path, byte[]> stored = !reconfigured
                && context.previous() != null
                && Files.exists(context.previous().resolve(FORMATTED))
                        ? HashFunction.read(context.previous().resolve(FORMATTED))
                        : Map.of();
        List<String> files = new ArrayList<>();
        for (Map.Entry<Path, byte[]> entry : current.entrySet()) {
            byte[] prior = stored.get(entry.getKey());
            if (prior == null || !Arrays.equals(prior, entry.getValue())) {
                files.add(located.get(entry.getKey()).toString());
            }
        }
        if (files.isEmpty()) {
            return CompletableFuture.completedStage(null);
        }
        if (jars.isEmpty()) {
            throw new IllegalStateException("No " + tool + " jars resolved upstream of the " + tool + " step");
        }
        files.sort(null);
        return CompletableFuture.completedStage(command(context, jars, config, files, verify));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        return super.apply(executor, context, arguments).thenCompose(result -> {
            if (!result.next()) {
                return CompletableFuture.completedStage(result);
            }
            try {
                HashFunction hash = new HashDigestFunction("SHA-256");
                Map<Path, byte[]> after = new LinkedHashMap<>();
                for (BuildStepArgument argument : arguments.values()) {
                    if (argument.removed()) {
                        continue;
                    }
                    after.putAll(formattable(argument.folder(), hash, executor));
                }
                HashFunction.write(context.next().resolve(FORMATTED), after);
                return CompletableFuture.completedStage(result);
            } catch (IOException e) {
                return CompletableFuture.failedStage(e);
            }
        });
    }

    private Map<Path, byte[]> formattable(Path folder, HashFunction hash, Executor executor) throws IOException {
        Path sources = folder.resolve(Bind.SOURCES);
        if (!Files.isDirectory(sources)) {
            return Map.of();
        }
        Map<Path, byte[]> formattable = new LinkedHashMap<>();
        for (Map.Entry<Path, byte[]> entry : HashFunction.read(sources, hash, executor).entrySet()) {
            if (isFormattable(sources.resolve(entry.getKey()))) {
                formattable.put(entry.getKey(), entry.getValue());
            }
        }
        return formattable;
    }
}

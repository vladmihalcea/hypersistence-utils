package build.jenesis.project;

import module java.base;
import build.jenesis.BuildExecutor;
import build.jenesis.BuildExecutorModule;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.Environment;
import build.jenesis.step.Dependencies;
import build.jenesis.step.Findings;
import build.jenesis.step.ProcessBuildStep;
import build.jenesis.step.ProcessHandler;

public class JDeprScanModule implements BuildExecutorModule {

    public static final String CHECK = "check";
    private static final String STRICT = "strict.jdeprscan", REPORT = BuildStep.REPORTS + "jdeprscan/jdeprscan.txt";

    private final String group;
    private final boolean strict;
    private final boolean forRemoval;
    private final Integer release;
    private final ProcessBuildStep.Terms terms;

    public JDeprScanModule() {
        this("main", false, true, null, new ProcessBuildStep.Terms());
    }

    public static JDeprScanModule ofEnvironment(Environment environment) {
        return new JDeprScanModule("main",
                environment.flag(STRICT, false),
                true,
                null,
                ProcessBuildStep.Terms.ofEnvironment(environment, "jdeprscan"));
    }

    private JDeprScanModule(String group,
                            boolean strict,
                            boolean forRemoval,
                            Integer release,
                            ProcessBuildStep.Terms terms) {
        this.group = group;
        this.strict = strict;
        this.forRemoval = forRemoval;
        this.release = release;
        this.terms = terms;
    }

    public static Path configurationFile(SequencedSet<Path> configuration) {
        return BuildStep.locate(configuration, "jdeprscan.properties");
    }

    public JDeprScanModule group(String group) {
        return new JDeprScanModule(group, strict, forRemoval, release, terms);
    }

    public JDeprScanModule strict(boolean strict) {
        return new JDeprScanModule(group, strict, forRemoval, release, terms);
    }

    public JDeprScanModule forRemoval(boolean forRemoval) {
        return new JDeprScanModule(group, strict, forRemoval, release, terms);
    }

    public JDeprScanModule release(Integer release) {
        if (release != null && release < 1) {
            throw new IllegalArgumentException("The release " + release + " is no Java release - name one as a"
                    + " number, as 21, or none for the deprecations of the JDK the build runs on");
        }
        return new JDeprScanModule(group, strict, forRemoval, release, terms);
    }

    public JDeprScanModule printing(BiConsumer<Boolean, String> printing) {
        return new JDeprScanModule(group, strict, forRemoval, release, terms.printing(printing));
    }

    @Override
    public void accept(BuildExecutor buildExecutor, SequencedMap<String, Path> inherited) {
        buildExecutor.addStep(CHECK, new Check(terms, group, strict, forRemoval, release), inherited.sequencedKeySet());
    }

    private static class Check extends ProcessBuildStep {

        private final String group;
        private final boolean strict;
        private final boolean forRemoval;
        private final Integer release;

        private Check(ProcessBuildStep.Terms terms, String group, boolean strict, boolean forRemoval, Integer release) {
            super("jdeprscan", ProcessHandler.OfProcess.ofJavaHome("bin/jdeprscan"), terms);
            this.group = group;
            this.strict = strict;
            this.forRemoval = forRemoval;
            this.release = release;
        }

        @Override
        public boolean acceptableExitCode(int code,
                                          Executor executor,
                                          BuildStepContext context,
                                          SequencedMap<String, BuildStepArgument> arguments) throws IOException {
            if (code != 0) {
                return false;
            }
            List<String> findings = new ArrayList<>(), unresolved = new ArrayList<>();
            for (String line : Files.readAllLines(context.supplement().resolve("output"))) {
                if (line.startsWith("class ") && line.contains(" deprecated ")) {
                    findings.add(line.substring("class ".length()));
                }
            }
            for (String line : Files.readAllLines(context.supplement().resolve("error"))) {
                if (line.startsWith("error: ")) {
                    unresolved.add(line.substring("error: ".length()));
                }
            }
            if (!unresolved.isEmpty()) {
                throw new IllegalStateException("jdeprscan cannot scan every class, as their dependencies are not"
                        + " all on the module's compile class path:\n  " + String.join("\n  ", unresolved));
            }
            Path report = context.next().resolve(REPORT);
            Files.createDirectories(report.getParent());
            Files.write(report, findings);
            return new Findings("jdeprscan", report, findings.size())
                    .acceptable(code, context, false, strict, STRICT, terms.reporting());
        }

        @Override
        public CompletionStage<List<String>> process(Executor executor,
                                                     BuildStepContext context,
                                                     SequencedMap<String, BuildStepArgument> arguments,
                                                     SequencedMap<String, SequencedMap<String, String>> properties)
                throws IOException {
            List<String> classes = new ArrayList<>();
            SequencedSet<String> jars = new LinkedHashSet<>();
            for (BuildStepArgument argument : arguments.values()) {
                if (argument.removed()) {
                    continue;
                }
                for (Path jar : Dependencies.select(argument.folder(), group, "compile")) {
                    jars.add(jar.toString());
                }
                Path compiled = argument.folder().resolve(BuildStep.CLASSES);
                if (Files.isDirectory(compiled)) {
                    classes.add(compiled.toString());
                }
            }
            if (classes.isEmpty()) {
                return CompletableFuture.completedStage(null);
            }
            List<String> commands = new ArrayList<>();
            if (forRemoval) {
                commands.add("--for-removal");
            }
            if (release != null) {
                commands.add("--release");
                commands.add(release.toString());
            }
            if (!jars.isEmpty()) {
                commands.add("--class-path");
                commands.add(String.join(File.pathSeparator, jars));
            }
            commands.addAll(classes);
            return CompletableFuture.completedStage(commands);
        }
    }
}

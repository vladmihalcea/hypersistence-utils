package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStepArgument;

public abstract class EnvironmentalProcessBuildStep extends ProcessBuildStep {

    protected EnvironmentalProcessBuildStep(String command, Function<List<String>, ? extends ProcessHandler> factory) {
        super(command, factory);
    }

    protected EnvironmentalProcessBuildStep(String command,
                                            Function<List<String>, ? extends ProcessHandler> factory,
                                            Terms terms) {
        super(command, factory, terms);
    }

    protected boolean inherits(String variable) {
        return false;
    }

    @Override
    protected ProcessHandler environment(ProcessHandler handler, SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        SequencedMap<String, String> variables = variables(arguments);
        if (!(handler instanceof ProcessHandler.OfProcess process)) {
            if (!variables.isEmpty()) {
                throw new IllegalStateException("An environment file hands " + variables.keySet() + " to "
                        + handler.commands().getFirst() + ", which runs through the Tools API inside the build's"
                        + " own JVM and so takes no environment");
            }
            return handler;
        }
        SortedMap<String, String> environment = new TreeMap<>(process.environment());
        System.getenv().forEach((name, value) -> {
            if (inherits(name)) {
                environment.put(name, value);
            }
        });
        variables.forEach((name, value) -> {
            String resolved = value.isEmpty() ? System.getenv(name) : value;
            if (resolved != null) {
                environment.put(name, resolved);
            }
        });
        return process.environment(environment);
    }
}

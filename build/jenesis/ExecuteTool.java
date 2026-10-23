package build.jenesis;

import module java.base;

public final class ExecuteTool extends JenesisTool {

    @Override
    public String name() {
        return "jenesis-exec";
    }

    @Override
    protected int run(Environment environment, List<String> arguments)
            throws IOException, InterruptedException {
        requireInProcess(environment);
        if (environment.flag("execute.docker")) {
            throw new IllegalStateException("A dockerized program cannot be run by the " + name()
                    + " tool, because a container replaces the process it runs in"
                    + " - unset jenesis.execute.docker, or run the " + name() + " command instead");
        }
        Path root = root(environment);
        Make.Settings settings = Make.settings(root, environment.keys());
        return Execution.run(environment.keys(settings.keys()),
                             root,
                             settings.profiles(),
                             arguments.toArray(String[]::new));
    }
}

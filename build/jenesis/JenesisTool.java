package build.jenesis;

import module java.base;

abstract class JenesisTool implements ToolProvider {

    @Override
    public int run(PrintWriter out, PrintWriter err, String... arguments) {
        SequencedMap<String, String> settings = new LinkedHashMap<>();
        List<String> remaining;
        try {
            remaining = List.of(Make.partitioned(arguments, settings));
        } catch (IOException | RuntimeException e) {
            err.println(e.getMessage());
            return 1;
        }
        String option = jvmOption(remaining);
        if (option != null) {
            err.println(name() + " run as a tool takes no option for a JVM, as the JDK's tools take none: "
                    + option + " - run the " + name() + " command to pass one");
            return 2;
        }
        try {
            return run(new Environment(Make.keys(settings), out, err), remaining);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            err.println(name() + " was interrupted running " + String.join(" ", remaining));
            return 1;
        } catch (IOException e) {
            err.println(name() + " failed: " + e.getMessage());
            return 1;
        } catch (RuntimeException e) {
            err.println(e.getMessage());
            return 1;
        }
    }

    protected abstract int run(Environment environment, List<String> arguments)
            throws IOException, InterruptedException;

    protected String jvmOption(List<String> arguments) {
        return !arguments.isEmpty() && arguments.getFirst().startsWith("-J") ? arguments.getFirst() : null;
    }

    protected Environment layered(Environment environment, Path root) throws IOException {
        return environment.keys(Make.settings(root, environment.keys()).keys());
    }

    protected Path root(Environment environment) {
        return Path.of(environment.getProperty("make.root", "")).toAbsolutePath().normalize();
    }

    protected void requireInProcess(Environment environment) {
        if (environment.getProperty("toolchain.version") != null) {
            throw new IllegalStateException("jenesis.toolchain.version cannot be honored by the "
                    + name() + " tool, because a toolchain replaces the JVM a build runs on"
                    + " - run the " + name() + " command instead");
        }
        if (environment.flag("project.docker")) {
            throw new IllegalStateException("A dockerized build cannot be run by the "
                    + name() + " tool, because a container replaces the process a build runs in"
                    + " - unset jenesis.project.docker, or run the " + name() + " command instead");
        }
    }
}

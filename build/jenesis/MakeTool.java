package build.jenesis;

import module java.base;

public final class MakeTool extends JenesisTool {

    @Override
    public String name() {
        return "jenesis-make";
    }

    @Override
    protected int run(Environment environment, List<String> selectors) throws IOException {
        requireInProcess(environment);
        if (environment.value("make.mode", "").equalsIgnoreCase("aot")) {
            throw new IllegalStateException("jenesis.make.mode=aot cannot be honored by the " + name() + " tool,"
                    + " because a cache is handed to a JVM the build relaunches into - run the " + name()
                    + " command instead");
        }
        Path root = root(environment);
        Make.Settings settings = Make.settings(root, environment.keys());
        return Project.perform(environment.keys(settings.keys()),
                               root,
                               settings.profiles(),
                               selectors.toArray(String[]::new)) == null ? 1 : 0;
    }
}

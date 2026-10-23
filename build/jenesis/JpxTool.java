package build.jenesis;

import module java.base;

public final class JpxTool extends JenesisTool {

    @Override
    public String name() {
        return "jpx";
    }

    @Override
    protected String jvmOption(List<String> arguments) {
        for (String argument : arguments) {
            if (argument.startsWith("-J")) {
                return argument;
            } else if (!argument.startsWith("--")) {
                return null;
            }
        }
        return null;
    }

    @Override
    protected int run(Environment environment, List<String> arguments)
            throws IOException, InterruptedException {
        return Jpx.run(environment, arguments.toArray(String[]::new));
    }
}

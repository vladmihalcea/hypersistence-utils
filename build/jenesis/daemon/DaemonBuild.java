package build.jenesis.daemon;

import module java.base;
import build.jenesis.Environment;
import build.jenesis.Make;
import build.jenesis.Project;
import build.jenesis.SequencedProperties;

public final class DaemonBuild {

    private DaemonBuild() {
    }

    public static int main(String... arguments) throws IOException {
        if (arguments.length < 3) {
            throw new IllegalArgumentException("Expected a project root, an entry point, a file for the outputs, the"
                    + " -Djenesis.<key>=<value> settings, -- and the selectors, but got " + List.of(arguments)
                    + " - this class is run by the build daemon, not by hand");
        }
        Path root = Path.of(arguments[0]).toAbsolutePath().normalize();
        String mainClass = arguments[1];
        Map<String, String> requested = new LinkedHashMap<>();
        int index = 3;
        for (; index < arguments.length && !arguments[index].equals("--"); index++) {
            int separator = arguments[index].indexOf('=');
            if (!arguments[index].startsWith("-D") || separator < 0) {
                throw new IllegalArgumentException("Expected a setting as -Djenesis.<key>=<value> before --, but got "
                        + arguments[index]);
            }
            requested.put(arguments[index].substring(2, separator), arguments[index].substring(separator + 1));
        }
        String[] selectors = Arrays.copyOfRange(arguments, Math.min(index + 1, arguments.length), arguments.length);
        requested.put("jenesis.make.root", root.toString());
        requested.put("jenesis.make.mode", "compiled");
        requested.putIfAbsent("jenesis.process.mode", "daemon");
        requested.putIfAbsent("jenesis.project.target", root.resolve("target").toString());
        requested.putIfAbsent("jenesis.project.artifacts", root.resolve(".jenesis").resolve("artifacts").toString());
        Map<String, String> keys = Make.keys(requested);
        if (new Environment(keys).flag("project.docker")) {
            throw new IllegalStateException("A dockerized build cannot run in the daemon, because it replaces the"
                    + " running process - unset jenesis.project.docker or run build/jenesis/Make.java");
        }
        Make.Settings settings = Make.settings(root, keys);
        Environment environment = new Environment(settings.keys());
        if (!mainClass.equals(Project.class.getName())) {
            return Project.run(environment, mainClass, root, settings.profiles(), selectors);
        }
        SequencedMap<String, Path> produced = Project.perform(environment, root, settings.profiles(), selectors);
        if (produced == null) {
            return 1;
        }
        if (!arguments[2].isEmpty()) {
            SequencedProperties outputs = new SequencedProperties();
            produced.forEach((identity, output) -> outputs.setProperty(identity,
                    root.relativize(output.toAbsolutePath().normalize()).toString()));
            outputs.store(Path.of(arguments[2]));
        }
        return 0;
    }
}

package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.TestFramework;
import build.jenesis.TestTags;

public record TestNG() implements TestFramework {

    @Override
    public String runnerModule() {
        return "org.testng";
    }

    @Override
    public boolean isMarkedBy(ModuleDescriptor module) {
        return module.name().equals("org.testng");
    }

    @Override
    public Set<String> reflectingModules() {
        return Set.of("org.testng");
    }

    @Override
    public String runnerClass() {
        return "org.testng.TestNG";
    }

    @Override
    public List<String> arguments(Path supplement,
                                  Path output,
                                  SequencedSet<String> classes,
                                  SequencedMap<String, SequencedSet<String>> methods,
                                  boolean parallel,
                                  boolean reporting) {
        List<String> commands = new ArrayList<>(List.of("-verbose", "2", "-d", (reporting
                ? output.resolve(BuildStep.REPORTS + "tests")
                : supplement.resolve("test-output")).toString()));
        if (parallel) {
            commands.add("-parallel");
            commands.add("methods");
        }
        if (!classes.isEmpty()) {
            commands.add("-testclass");
            commands.add(String.join(",", classes));
        }
        if (!methods.isEmpty()) {
            List<String> joined = new ArrayList<>();
            for (Map.Entry<String, SequencedSet<String>> entry : methods.entrySet()) {
                for (String method : entry.getValue()) {
                    joined.add(entry.getKey() + "." + method);
                }
            }
            commands.add("-methods");
            commands.add(String.join(",", joined));
        }
        return commands;
    }

    @Override
    public List<String> tags(List<String> arguments, TestTags requested, List<TestTags> ran) {
        List<String> selection = new ArrayList<>(arguments);
        SequencedSet<String> groups = new TreeSet<>();
        Set<String> excluded = null;
        boolean everything = false;
        for (String alternative : requested.alternatives()) {
            SequencedSet<String> tagged = new TreeSet<>(), untagged = new TreeSet<>();
            for (String literal : TestTags.literals(alternative)) {
                if (literal.startsWith("-")) {
                    untagged.add(literal.substring(1));
                } else {
                    tagged.add(literal);
                }
            }
            if (tagged.size() > 1 || excluded != null && !excluded.equals(untagged)) {
                throw new IllegalArgumentException("TestNG runs the tests of any of several groups and leaves the same"
                        + " groups out of all of them, so it cannot run " + requested + " - write each alternative as at most"
                        + " one group, with the same groups preceded by - in every alternative");
            }
            excluded = untagged;
            groups.addAll(tagged);
            everything |= tagged.isEmpty();
        }
        if (!everything && !groups.isEmpty()) {
            selection.add("-groups");
            selection.add(String.join(",", groups));
        }
        SequencedSet<String> excludedGroups = new TreeSet<>(excluded == null ? Set.of() : excluded);
        if (ran.stream().allMatch(earlier -> !earlier.all() && earlier.alternatives().stream()
                .allMatch(alternative -> !alternative.contains("+") && !alternative.startsWith("-")))) {
            ran.forEach(earlier -> excludedGroups.addAll(earlier.alternatives()));
        }
        if (!excludedGroups.isEmpty()) {
            selection.add("-excludegroups");
            selection.add(String.join(",", excludedGroups));
        }
        return selection;
    }
}

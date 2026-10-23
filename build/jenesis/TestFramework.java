package build.jenesis;

import module java.base;

public interface TestFramework extends Serializable {

    String runnerModule();

    String runnerClass();

    boolean isMarkedBy(ModuleDescriptor module);

    Set<String> reflectingModules();

    default SequencedMap<String, String> missingCoordinates(List<ModuleDescriptor> modules) {
        return Collections.emptyNavigableMap();
    }

    default boolean holdsTests(ClassModel type) {
        return true;
    }

    default Map<String, String> systemProperties() {
        return Map.of();
    }

    default TestFramework runningOn(List<Path> jars) throws IOException {
        return this;
    }

    List<String> arguments(Path supplement,
                           Path output,
                           SequencedSet<String> classes,
                           SequencedMap<String, SequencedSet<String>> methods,
                           boolean parallel,
                           boolean reporting);

    default List<String> tags(List<String> arguments, TestTags requested, List<TestTags> ran) {
        if (!requested.all()) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " cannot select tests by tag, so it cannot run "
                    + requested);
        }
        return arguments;
    }

    default List<String> engines(List<String> arguments, String engines) {
        if (engines != null) {
            throw new IllegalArgumentException(getClass().getSimpleName() + " runs on no JUnit Platform engine, so"
                    + " the engines key of test.properties cannot select " + engines
                    + " - leave it out for these tests");
        }
        return arguments;
    }
}

package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.PathPlacement;
import build.jenesis.TestFramework;
import build.jenesis.step.Dependencies;

public final class TestFrameworks {

    private TestFrameworks() {
    }

    public static TestFramework named(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "junit-platform" -> new JUnitPlatform();
            case "junit4" -> new JUnit4();
            case "testng" -> new TestNG();
            default -> throw new IllegalArgumentException("Unknown test framework: " + name
                    + " (expected junit-platform, junit4, or testng)");
        };
    }

    public static Optional<TestFramework> detect(List<ModuleDescriptor> modules) {
        return detect(List.of(), modules);
    }

    public static Optional<TestFramework> detect(List<TestFramework> frameworks, List<ModuleDescriptor> modules) {
        return Stream.concat(frameworks.stream(), Stream.<TestFramework>of(new JUnitPlatform(), new JUnit4(), new TestNG()))
                .filter(framework -> modules.stream().anyMatch(framework::isMarkedBy))
                .findFirst();
    }

    public static Optional<TestFramework> detect(Iterable<Path> folders) throws IOException {
        return detect(List.of(), modules(folders));
    }

    public static Optional<TestFramework> detect(List<TestFramework> frameworks, Iterable<Path> folders) throws IOException {
        return detect(frameworks, modules(folders));
    }

    public static List<ModuleDescriptor> modules(Iterable<Path> folders) throws IOException {
        List<ModuleDescriptor> modules = new ArrayList<>();
        for (Path file : jars(folders)) {
            ModuleDescriptor module = PathPlacement.moduleDescriptor(file);
            if (module != null) {
                modules.add(module);
            }
        }
        modules.sort(Comparator.comparing(ModuleDescriptor::name));
        return modules;
    }

    public static List<Path> jars(Iterable<Path> folders) throws IOException {
        List<Path> jars = new ArrayList<>();
        for (Path folder : folders) {
            Path artifacts = folder.resolve(BuildStep.ARTIFACTS);
            if (Files.exists(artifacts)) {
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(artifacts)) {
                    for (Path file : stream) {
                        if (Files.isRegularFile(file)) {
                            jars.add(file);
                        }
                    }
                }
            }
            jars.addAll(Dependencies.all(folder));
        }
        return jars;
    }
}

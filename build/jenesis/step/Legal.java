package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Environment;

public class Legal implements BuildStep {

    public static final String LEGAL = "legal/";
    private static final SequencedSet<String> NOTICES = new LinkedHashSet<>(List.of(
            "META-INF/NOTICE", "META-INF/LICENSE", "META-INF/license/", "META-INF/licenses/", "LICENSE", "about.html"));

    private final String group;
    private final SequencedSet<String> notices;

    public Legal() {
        this("main", NOTICES);
    }

    public static Legal ofEnvironment(Environment environment) {
        List<String> notices = environment.entries("legal.notices");
        return new Legal("main", notices == null ? NOTICES : new LinkedHashSet<>(notices));
    }

    private Legal(String group, SequencedSet<String> notices) {
        this.group = group;
        this.notices = notices;
    }

    public Legal group(String group) {
        return new Legal(group, notices);
    }

    public Legal notices(SequencedSet<String> notices) {
        return new Legal(group, notices);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Path legal = context.next().resolve(LEGAL);
        SequencedMap<Path, Path> jars = new LinkedHashMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path artifacts = argument.folder().resolve(BuildStep.ARTIFACTS);
            if (Files.isDirectory(artifacts)) {
                try (Stream<Path> files = Files.list(artifacts)) {
                    files.filter(jar -> jar.getFileName().toString().endsWith(".jar")).sorted().forEach(jar -> jars.put(jar, legal));
                }
            }
            for (Path jar : Dependencies.select(argument.folder(), group, "runtime")) {
                String name = jar.getFileName().toString();
                jars.putIfAbsent(jar, legal.resolve(name.endsWith(".jar") ? name.substring(0, name.length() - 4) : name));
            }
        }
        for (Map.Entry<Path, Path> jar : jars.entrySet()) {
            try (JarFile file = new JarFile(jar.getKey().toFile())) {
                for (JarEntry entry : (Iterable<JarEntry>) file.stream()::iterator) {
                    String name = entry.getName(), lower = name.toLowerCase(Locale.ROOT);
                    String relative = null;
                    for (String candidate : notices) {
                        String expected = candidate.toLowerCase(Locale.ROOT);
                        if (expected.endsWith("/") ? lower.startsWith(expected) : lower.equals(expected)
                                || lower.startsWith(expected + ".") && lower.indexOf('/', expected.length()) == -1) {
                            relative = expected.endsWith("/")
                                    ? name.substring(expected.length())
                                    : name.substring(name.lastIndexOf('/') + 1);
                            break;
                        }
                    }
                    if (entry.isDirectory() || relative == null || relative.isEmpty()) {
                        continue;
                    }
                    Path target = BuildStep.resolveContained(jar.getValue(), relative);
                    Files.createDirectories(target.getParent());
                    if (!Files.exists(target)) {
                        try (InputStream in = file.getInputStream(entry)) {
                            Files.copy(in, target);
                        }
                    }
                }
            }
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

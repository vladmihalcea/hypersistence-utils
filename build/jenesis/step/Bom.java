package build.jenesis.step;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.HashDigestFunction;
import build.jenesis.SequencedProperties;

public class Bom implements BuildStep {

    public static final String BOM = "bom";

    private final HashDigestFunction hashFunction;

    public Bom(HashDigestFunction hashFunction) {
        this.hashFunction = hashFunction;
    }

    @Override
    public boolean shouldRun(SequencedMap<String, BuildStepArgument> arguments) {
        return arguments.values().stream().anyMatch(argument -> argument.hasChanged(
                Path.of(DEPENDENCIES),
                Path.of(MODULE),
                Path.of(Dependencies.RESOLVED)));
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        String module = null;
        SequencedMap<String, String> entries = new TreeMap<>();
        for (BuildStepArgument argument : arguments.values()) {
            if (argument.removed()) {
                continue;
            }
            Path folder = argument.folder();
            Path moduleFile = folder.resolve(MODULE);
            if (module == null && Files.isRegularFile(moduleFile)) {
                String value = SequencedProperties.ofFiles(moduleFile).getProperty("module");
                if (value != null && !value.isEmpty()) {
                    module = value;
                }
            }
            Path dependenciesFile = folder.resolve(DEPENDENCIES);
            if (!Files.isRegularFile(dependenciesFile)) {
                continue;
            }
            SequencedProperties dependencies = SequencedProperties.ofFiles(dependenciesFile);
            for (String key : dependencies.stringPropertyNames()) {
                int first = key.indexOf('/');
                int second = first < 1 ? -1 : key.indexOf('/', first + 1);
                if (second < 0 || !key.substring(0, first).equals("main")) {
                    continue;
                }
                String rest = key.substring(second + 1);
                int firstSlash = rest.indexOf('/');
                int lastSlash = rest.lastIndexOf('/');
                if (lastSlash <= 0 || lastSlash == firstSlash) {
                    continue;
                }
                String coordinate = rest.substring(0, lastSlash);
                String version = rest.substring(lastSlash + 1);
                String raw = dependencies.getProperty(key);
                int space = raw.indexOf(' ');
                String checksum = space < 0 ? null : raw.substring(space + 1).trim();
                if (checksum == null || checksum.isEmpty()) {
                    Path jar = folder.resolve(space < 0 ? raw : raw.substring(0, space)).normalize();
                    checksum = Files.isRegularFile(jar) ? hashFunction.encodedHash(jar) : null;
                }
                String value = checksum == null || checksum.isEmpty() ? version : version + " " + checksum;
                String entry;
                if (coordinate.startsWith("module/")) {
                    String name = coordinate.substring("module/".length());
                    int dash = name.indexOf('-');
                    if (dash >= 0) {
                        value = ":" + name.substring(dash + 1) + ":" + value;
                        name = name.substring(0, dash);
                    }
                    entry = name;
                } else if (coordinate.startsWith("maven/")) {
                    String maven = coordinate.substring("maven/".length());
                    entry = maven.indexOf('/') > 0 && maven.indexOf('/') == maven.lastIndexOf('/')
                            ? maven
                            : coordinate;
                } else {
                    entry = coordinate;
                }
                entries.putIfAbsent(entry, value);
            }
        }
        if (module != null && !entries.isEmpty()) {
            SequencedProperties bom = new SequencedProperties();
            entries.forEach(bom::setProperty);
            bom.store(Files.createDirectories(context.next().resolve(BOM))
                    .resolve("pin-" + module + ".properties"));
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

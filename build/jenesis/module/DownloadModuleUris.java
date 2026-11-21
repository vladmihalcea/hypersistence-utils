package build.jenesis.module;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.BuildStepArgument;
import build.jenesis.BuildStepContext;
import build.jenesis.BuildStepResult;
import build.jenesis.Repository;

public class DownloadModuleUris implements BuildStep {

    public static final String URIS = "uris.properties";
    public static final URI DEFAULT = URI.create("https://raw.githubusercontent.com/" +
            "sormuras/modules/refs/heads/main/com.github.sormuras.modules/" +
            "com/github/sormuras/modules/modules.properties");

    private final String prefix;
    private final Supplier<List<URI>> locations;

    public DownloadModuleUris(String prefix) {
        this(prefix, (Supplier<List<URI>> & Serializable) () -> List.of(DEFAULT));
    }

    private DownloadModuleUris(String prefix, Supplier<List<URI>> locations) {
        this.prefix = prefix;
        this.locations = locations;
    }

    public <S extends Supplier<List<URI>> & Serializable> DownloadModuleUris locations(S locations) {
        return new DownloadModuleUris(prefix, locations);
    }

    @Override
    public CompletionStage<BuildStepResult> apply(Executor executor,
                                                  BuildStepContext context,
                                                  SequencedMap<String, BuildStepArgument> arguments)
            throws IOException {
        Path target = context.next().resolve(URIS);
        Path temporary = Files.createTempFile(context.next(), "uris", ".tmp");
        try {
            try (BufferedWriter writer = Files.newBufferedWriter(temporary)) {
                for (URI location : locations.get()) {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                            Repository.open(location, null),
                            StandardCharsets.UTF_8))) {
                        Iterator<String> it = reader.lines().iterator();
                        while (it.hasNext()) {
                            writer.write((prefix == null ? "" : (prefix + "/")) + it.next());
                            writer.newLine();
                        }
                    }
                }
            }
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException _) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Throwable t) {
            Files.deleteIfExists(temporary);
            throw t;
        }
        return CompletableFuture.completedStage(new BuildStepResult(true));
    }
}

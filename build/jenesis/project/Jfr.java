package build.jenesis.project;

import module java.base;
import build.jenesis.BuildStep;
import build.jenesis.ObservabilityEngine;

public record Jfr(SequencedMap<String, String> options) implements ObservabilityEngine {

    public Jfr {
        options.forEach((key, value) -> {
            if (key.equals("filename")) {
                throw new IllegalArgumentException("The build sets the filename of the recording to the test step's"
                        + " reports/jfr/ - leave filename out of the options");
            } else if (key.isBlank() || value.isBlank() || (key + value).contains(",") || key.contains("=")) {
                throw new IllegalArgumentException("An option of the recording is a key and a value of JFR, such as"
                        + " settings=profile, that holds no comma, and the key no equals sign: '" + key + "=" + value
                        + "'");
            }
        });
        options = Collections.unmodifiableSequencedMap(new LinkedHashMap<>(options));
    }

    public Jfr() {
        this(Collections.emptyNavigableMap());
    }

    @Override
    public String name() {
        return "jfr";
    }

    @Override
    public SequencedMap<String, String> coordinates() {
        return Collections.emptyNavigableMap();
    }

    @Override
    public List<String> commands(SequencedMap<String, Path> resolved, Path output) {
        Path folder = output.resolve(BuildStep.REPORTS + "jfr");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        SequencedMap<String, String> recording = new LinkedHashMap<>();
        recording.put("filename", folder.resolve("tests.jfr").toAbsolutePath().toString());
        recording.put("dumponexit", "true");
        recording.putAll(options);
        return List.of("-XX:StartFlightRecording=" + recording.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining(",")));
    }
}

package build.jenesis.project;

import module java.base;
import build.jenesis.ObservabilityEngine;

public record NativeImageAgent() implements ObservabilityEngine {

    public static final String NATIVE_IMAGE = "native-image";

    @Override
    public String name() {
        return NATIVE_IMAGE;
    }

    @Override
    public SequencedMap<String, String> coordinates() {
        return Collections.emptySortedMap();
    }

    @Override
    public List<String> commands(SequencedMap<String, Path> resolved, Path output) {
        return List.of("-agentlib:native-image-agent=config-output-dir="
                + output.resolve(NATIVE_IMAGE).toAbsolutePath());
    }
}

package build.jenesis;

import module java.base;

public interface ObservabilityEngine extends Serializable {

    String name();

    SequencedMap<String, String> coordinates();

    List<String> commands(SequencedMap<String, Path> resolved, Path output);
}

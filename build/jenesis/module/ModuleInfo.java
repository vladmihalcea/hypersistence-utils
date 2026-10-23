package build.jenesis.module;

import module java.base;

public record ModuleInfo(String coordinate,
                         String release,
                         String name,
                         String description,
                         String testOf,
                         boolean abstractTest,
                         String main,
                         SequencedSet<String> requires,
                         SequencedSet<String> runtimeRequires,
                         SequencedMap<String, String> plugins,
                         SequencedMap<String, String> layerApis,
                         SequencedMap<String, SequencedSet<String>> layers,
                         SequencedMap<String, String> attachments,
                         SequencedSet<String> natives,
                         SequencedMap<String, String> aliases,
                         SequencedMap<String, SequencedSet<String>> excludes,
                         SequencedMap<String, SequencedSet<String>> overrides,
                         SequencedMap<String, String> versions,
                         SequencedMap<String, SequencedMap<String, String>> variants,
                         SequencedMap<String, String> boms,
                         SequencedMap<String, String> signatures,
                         SequencedMap<String, SequencedMap<String, String>> bomVariants) {

    public ModuleInfo(String coordinate, SequencedSet<String> requires, SequencedSet<String> runtimeRequires) {
        this(coordinate,
                null,
                null,
                null,
                null,
                false,
                null,
                requires,
                runtimeRequires,
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableSet(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap(),
                Collections.emptyNavigableMap());
    }
}

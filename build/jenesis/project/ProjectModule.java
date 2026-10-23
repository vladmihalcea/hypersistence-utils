package build.jenesis.project;

import module java.base;

public interface ProjectModule {

    String name();

    SequencedSet<String> dependencies();

    SequencedSet<String> sources();

    SequencedSet<String> resources();

    SequencedSet<String> manifests();

    SequencedSet<String> coordinates();

    SequencedSet<String> artifacts();

    SequencedSet<String> spdx();
}

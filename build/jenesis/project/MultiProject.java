package build.jenesis.project;

import module java.base;

@FunctionalInterface
public interface MultiProject {

    AssemblyDescriptor module(String name,
                              SequencedMap<String, SequencedSet<String>> dependencies,
                              SequencedMap<String, Path> arguments) throws IOException;
}

package build.jenesis;

import module java.base;

public record BuildStepArgument(Path folder, Map<Path, Checksum> files) {

    private static final Set<Path> WILDCARDS = Set.of(Path.of("."), Path.of(""));

    public boolean removed() {
        return folder == null;
    }

    public String checksum(Path file) {
        Checksum checksum = files.get(file);
        return checksum == null ? null : checksum.encoded();
    }

    public boolean hasChanged() {
        return files.values().stream().anyMatch(checksum -> checksum.status() != ChecksumStatus.RETAINED);
    }

    public boolean hasChanged(Path... prefixes) {
        return hasChanged(Arrays.asList(prefixes));
    }

    public boolean hasChanged(Collection<Path> prefixes) {
        return files.entrySet().stream()
                .filter(entry -> prefixes.stream().anyMatch(prefix ->
                        WILDCARDS.contains(prefix) || entry.getKey().startsWith(prefix)))
                .anyMatch(entry -> entry.getValue().status() != ChecksumStatus.RETAINED);
    }
}

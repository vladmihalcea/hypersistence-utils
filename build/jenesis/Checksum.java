package build.jenesis;

import module java.base;

public final class Checksum {

    private final ChecksumStatus status;
    private final Supplier<String> value;

    private Checksum(ChecksumStatus status, Supplier<String> value) {
        if (status == ChecksumStatus.REMOVED && value != null) {
            throw new IllegalArgumentException("A removed entry cannot carry a checksum");
        }
        this.status = status;
        this.value = value;
    }

    public static Checksum of(ChecksumStatus status) {
        return new Checksum(status, null);
    }

    public ChecksumStatus status() {
        return status;
    }

    public String encoded() {
        if (value == null) {
            throw new UnsupportedOperationException("No checksum was recorded for this " + status + " entry");
        }
        return value.get();
    }

    public static Map<Path, Checksum> diff(Map<Path, byte[]> expected, Map<Path, byte[]> actual, HashDigestFunction hash) {
        Map<Path, Checksum> diff = new LinkedHashMap<>();
        Map<Path, byte[]> removed = new LinkedHashMap<>(expected);
        for (Map.Entry<Path, byte[]> entry : actual.entrySet()) {
            byte[] other = removed.remove(entry.getKey());
            ChecksumStatus status;
            if (other == null) {
                status = ChecksumStatus.ADDED;
            } else if (Arrays.equals(other, entry.getValue())) {
                status = ChecksumStatus.RETAINED;
            } else {
                status = ChecksumStatus.ALTERED;
            }
            byte[] bytes = entry.getValue();
            diff.put(entry.getKey(), new Checksum(status, () -> hash.encoded(bytes)));
        }
        for (Path path : removed.keySet()) {
            diff.put(path, of(ChecksumStatus.REMOVED));
        }
        return diff;
    }

    public static Map<Path, Checksum> removed(Collection<Path> expected) {
        Map<Path, Checksum> removed = new LinkedHashMap<>();
        expected.forEach(path -> removed.put(path, of(ChecksumStatus.REMOVED)));
        return removed;
    }

    public static Map<Path, Checksum> added(Map<Path, byte[]> actual, HashDigestFunction hash) {
        Map<Path, Checksum> added = new LinkedHashMap<>();
        actual.forEach((path, bytes) -> added.put(path, new Checksum(ChecksumStatus.ADDED, () -> hash.encoded(bytes))));
        return added;
    }
}

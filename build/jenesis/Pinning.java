package build.jenesis;

import module java.base;

public enum Pinning {

    STRICT,

    VERSIONS,

    IGNORE;

    private static final ConcurrentMap<Integer, Permits> PERMITS = new ConcurrentHashMap<>();

    public static Permits permits() {
        return permits(Environment.NONE);
    }

    public static Permits permits(Environment environment) {
        int concurrency = environment.number("pin.concurrency",
                                             Runtime.getRuntime().availableProcessors());
        if (concurrency < 0) {
            throw new IllegalArgumentException("Pin concurrency must not be negative: " + concurrency);
        }
        return concurrency == 0 ? null : PERMITS.computeIfAbsent(concurrency, Permits::of);
    }

    public static Pinning ofEnvironment(Environment environment) {
        String property = environment.getProperty("dependency.pin");
        if (property == null) {
            return null;
        }
        try {
            return Pinning.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.dependency.pin '" + property
                    + "', expected one of: strict, versions, ignore");
        }
    }
}

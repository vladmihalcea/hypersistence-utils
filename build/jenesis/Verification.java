package build.jenesis;

import module java.base;

public enum Verification {

    NONE, DECLARED, STRICT;

    public static Verification ofEnvironment(Environment environment) {
        String property = environment.getProperty("dependency.signature", "none");
        try {
            return Verification.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.dependency.signature '" + property
                    + "', expected one of: none, declared, strict");
        }
    }
}

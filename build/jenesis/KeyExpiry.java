package build.jenesis;

import module java.base;

public enum KeyExpiry {

    IGNORED, SIGNING, CURRENT;

    public static KeyExpiry ofEnvironment(Environment environment) {
        String property = environment.getProperty("openpgp.expiry", "signing");
        try {
            return KeyExpiry.valueOf(property.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException _) {
            throw new IllegalArgumentException("Unknown jenesis.openpgp.expiry '" + property
                    + "', expected one of: ignored, signing, current");
        }
    }
}

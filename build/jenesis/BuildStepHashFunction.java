package build.jenesis;

import module java.base;

@FunctionalInterface
public interface BuildStepHashFunction {

    byte[] hash(BuildStep step) throws IOException;

    static BuildStepHashFunction ofSerializationDigest(String algorithm) {
        return step -> {
            try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
                try (ObjectOutputStream out = new ObjectOutputStream(bytes) {
                    {
                        enableReplaceObject(true);
                    }

                    @Override
                    protected Object replaceObject(Object value) {
                        return value instanceof Path path
                                ? path.toString().replace('\\', '/')
                                : value;
                    }
                }) {
                    out.writeObject(step);
                }
                try {
                    return MessageDigest.getInstance(algorithm).digest(bytes.toByteArray());
                } catch (NoSuchAlgorithmException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
    }
}

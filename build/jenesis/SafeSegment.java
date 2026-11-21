package build.jenesis;

import module java.base;

public final class SafeSegment implements BiConsumer<String, String> {

    @Override
    public void accept(String role, String value) {
        if (value.isEmpty()) {
            throw new IllegalArgumentException("Blank " + role + " is not a valid coordinate");
        }
        for (String segment : value.split("/", -1)) {
            if (segment.equals("..")) {
                throw new IllegalArgumentException(
                        "Illegal " + role + " '" + value + "': path traversal is not permitted");
            }
        }
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            boolean permitted = character >= 'a' && character <= 'z'
                    || character >= 'A' && character <= 'Z'
                    || character >= '0' && character <= '9'
                    || character == '.'
                    || character == '-'
                    || character == '_'
                    || character == '+';
            if (!permitted) {
                throw new IllegalArgumentException(
                        "Illegal " + role + " '" + value + "': character '" + character + "' is not permitted");
            }
        }
        for (String segment : value.split("\\.", -1)) {
            if (segment.isEmpty() || segment.equals("..")) {
                throw new IllegalArgumentException(
                        "Illegal " + role + " '" + value + "': path traversal is not permitted");
            }
        }
    }
}

package build.jenesis;

import module java.base;

public record Environment(Map<String, String> keys, Consumer<String> out, Consumer<String> err) {

    public static final Environment NONE = new Environment(Map.of());

    public Environment {
        keys = Map.copyOf(keys);
    }

    public Environment(Map<String, String> keys) {
        this(keys, System.out::println, System.err::println);
    }

    public Environment(Map<String, String> keys, PrintWriter out, PrintWriter err) {
        this(keys, out::println, err::println);
    }

    public Environment keys(Map<String, String> keys) {
        return new Environment(keys, out, err);
    }

    public Environment out(Consumer<String> out) {
        return new Environment(keys, out, err);
    }

    public Environment err(Consumer<String> err) {
        return new Environment(keys, out, err);
    }

    public String getProperty(String key) {
        return keys.get(key);
    }

    public String getProperty(String key, String defaultValue) {
        String value = keys.get(key);
        return value == null ? defaultValue : value;
    }

    public String value(String key) {
        return trimmed(keys.get(key));
    }

    public String value(String key, String defaultValue) {
        String value = trimmed(keys.get(key));
        return value == null ? defaultValue : value;
    }

    public boolean flag(String key) {
        return flag(key, false);
    }

    public boolean flag(String key, boolean defaultValue) {
        Boolean value = flagOrNull(key);
        return value == null ? defaultValue : value;
    }

    public Boolean flagOrNull(String key) {
        return Make.parsed("jenesis." + key, keys.get(key));
    }

    public int number(String key, int defaultValue) {
        Integer value = numberOrNull(key);
        return value == null ? defaultValue : value;
    }

    public long number(String key, long defaultValue) {
        Long value = longOrNull(key);
        return value == null ? defaultValue : value;
    }

    public Integer numberOrNull(String key) {
        String value = value(key);
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Malformed value for jenesis." + key + ": '" + value
                    + "' (expected a whole number)");
        }
    }

    public Long longOrNull(String key) {
        String value = value(key);
        if (value == null) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("Malformed value for jenesis." + key + ": '" + value
                    + "' (expected a whole number)");
        }
    }

    public List<String> entries(String key) {
        String value = value(key);
        if (value == null) {
            return null;
        }
        List<String> entries = new ArrayList<>();
        for (String entry : value.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) {
                entries.add(trimmed);
            }
        }
        return entries;
    }

    public List<String> words(String key) {
        String value = value(key);
        return value == null ? List.of() : List.of(value.split("\\s+"));
    }

    public String variable(String origin, String value) {
        if (!value.startsWith("@") || value.startsWith("@@")) {
            return value.startsWith("@") ? value.substring(1) : value;
        }
        int slash = value.indexOf('/');
        String name = value.substring(1, slash == -1 ? value.length() : slash);
        String resolved = value("variable." + name, slash == -1 ? null : value.substring(slash + 1));
        if (resolved == null) {
            throw new IllegalArgumentException(origin + " to " + value + ", but jenesis.variable." + name
                    + " is not set - set it, or give a default as @" + name + "/<default>");
        }
        return resolved;
    }

    private static String trimmed(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

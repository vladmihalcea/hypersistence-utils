package build.jenesis;

import module java.base;

public record Platform(SequencedSet<String> tokens) implements Serializable {

    public Platform(SequencedSet<String> tokens) {
        SequencedSet<String> normalized = new TreeSet<>();
        for (String token : tokens) {
            String value = token.trim().toLowerCase(Locale.ROOT);
            if (!value.isEmpty()) {
                normalized.add(value);
            }
        }
        this.tokens = Collections.unmodifiableSequencedSet(normalized);
    }

    public Platform() {
        this(new TreeSet<>(List.of(os(System.getProperty("os.name", "")), arch(System.getProperty("os.arch", "")))));
    }

    public static Platform ofEnvironment(Environment environment) {
        SequencedSet<String> tokens = new TreeSet<>(new Platform().tokens());
        for (String key : new TreeSet<>(environment.keys().keySet())) {
            if (!key.startsWith("platform.")) {
                continue;
            }
            String token = key.substring("platform.".length());
            if (environment.flag(key)) {
                tokens.add(token.toLowerCase(Locale.ROOT));
            } else {
                tokens.remove(token.toLowerCase(Locale.ROOT));
            }
        }
        return new Platform(tokens);
    }

    private static String os(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("windows")) {
            return "windows";
        }
        if (normalized.startsWith("mac") || normalized.startsWith("darwin")) {
            return "macos";
        }
        if (normalized.startsWith("linux")) {
            return "linux";
        }
        return normalized.replace(' ', '-');
    }

    private static String arch(String name) {
        String normalized = name.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "amd64", "x86-64", "x64" -> "x86_64";
            case "arm64" -> "aarch64";
            default -> normalized;
        };
    }

    public static Platform of(String value) {
        return new Platform(new TreeSet<>(List.of(value.split(",", -1))));
    }

    public String canonical() {
        return String.join(",", tokens);
    }

    public boolean matches(Platform guard) {
        return tokens.containsAll(guard.tokens());
    }

    public String select(String key, String fallback, SequencedMap<String, String> guarded) {
        int specificity = 0;
        for (Map.Entry<String, String> entry : guarded.entrySet()) {
            Platform guard = of(entry.getKey());
            if (matches(guard) && guard.tokens().size() > specificity) {
                specificity = guard.tokens().size();
            }
        }
        if (specificity == 0) {
            return fallback;
        }
        String selected = null, winner = null;
        for (Map.Entry<String, String> entry : guarded.entrySet()) {
            Platform guard = of(entry.getKey());
            if (!matches(guard) || guard.tokens().size() != specificity) {
                continue;
            }
            if (selected == null) {
                selected = entry.getValue();
                winner = entry.getKey();
            } else if (!entry.getValue().equals(selected)) {
                throw new IllegalStateException("Ambiguous platform guards for " + key
                        + ": [" + winner + "] and [" + entry.getKey() + "] both match " + tokens);
            }
        }
        return selected;
    }
}

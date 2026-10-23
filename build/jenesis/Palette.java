package build.jenesis;

import module java.base;

public enum Palette {

    ANSI, NONE;

    public static Palette ofEnvironment(Environment environment) {
        String colors = environment.value("palette.colors", "ansi");
        return switch (colors) {
            case "ansi" -> ANSI;
            case "none" -> NONE;
            default -> throw new IllegalArgumentException(
                    "Unknown jenesis.palette.colors '" + colors + "', expected 'ansi' or 'none'");
        };
    }

    public String reset() {
        return escape("0");
    }

    public String status() {
        return escape("32");
    }

    public String failure() {
        return escape("31");
    }

    public String skipped() {
        return escape("34");
    }

    public String info() {
        return escape("33");
    }

    public String warning() {
        return escape("33");
    }

    public String heading() {
        return escape("33");
    }

    public String detail() {
        return escape("36");
    }

    public String output() {
        return escape("38;5;244");
    }

    public String error() {
        return escape("38;5;131");
    }

    public String color(int code) {
        return escape("38;5;" + code);
    }

    public String bold(int code) {
        return escape("1;38;5;" + code);
    }

    private String escape(String code) {
        return this == ANSI ? "\033[" + code + "m" : "";
    }
}

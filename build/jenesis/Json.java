package build.jenesis;

import module java.base;

public final class Json {

    private static final int MAX_DEPTH = 1000;

    private final String text;
    private int pos;

    private Json(String text) {
        this.text = text;
    }

    public static Object parse(String text) {
        Json json = new Json(text);
        json.skip();
        Object value = json.value(0);
        json.skip();
        return value;
    }

    public static String escaped(String text) {
        StringBuilder builder = new StringBuilder(text.length());
        for (int index = 0; index < text.length(); index++) {
            char c = text.charAt(index);
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.toString();
    }

    private Object value(int depth) {
        if (depth > MAX_DEPTH) {
            throw new IllegalStateException("JSON nesting exceeds " + MAX_DEPTH + " levels");
        }
        skip();
        return switch (text.charAt(pos)) {
            case '{' -> object(depth);
            case '[' -> array(depth);
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> number();
        };
    }

    private Map<String, Object> object(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++;
        skip();
        if (text.charAt(pos) == '}') {
            pos++;
            return map;
        }
        while (true) {
            skip();
            String key = string();
            skip();
            pos++;
            map.put(key, value(depth + 1));
            skip();
            if (text.charAt(pos++) == '}') {
                return map;
            }
        }
    }

    private List<Object> array(int depth) {
        List<Object> list = new ArrayList<>();
        pos++;
        skip();
        if (text.charAt(pos) == ']') {
            pos++;
            return list;
        }
        while (true) {
            list.add(value(depth + 1));
            skip();
            if (text.charAt(pos++) == ']') {
                return list;
            }
        }
    }

    private String string() {
        StringBuilder builder = new StringBuilder();
        pos++;
        while (true) {
            char c = text.charAt(pos++);
            if (c == '"') {
                return builder.toString();
            }
            if (c != '\\') {
                builder.append(c);
                continue;
            }
            char escaped = text.charAt(pos++);
            switch (escaped) {
                case 'n' -> builder.append('\n');
                case 't' -> builder.append('\t');
                case 'r' -> builder.append('\r');
                case 'b' -> builder.append('\b');
                case 'f' -> builder.append('\f');
                case 'u' -> {
                    builder.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                }
                default -> builder.append(escaped);
            }
        }
    }

    private Object number() {
        int start = pos;
        while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
            pos++;
        }
        return Double.parseDouble(text.substring(start, pos));
    }

    private Object literal(String token, Object value) {
        pos += token.length();
        return value;
    }

    private void skip() {
        while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
            pos++;
        }
    }
}

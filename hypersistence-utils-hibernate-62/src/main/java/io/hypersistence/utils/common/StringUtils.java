package io.hypersistence.utils.common;

import java.util.Locale;

/**
 * <code>StringUtils</code> - String utilities holder.
 *
 * @author Vlad Mihalcea
 * @since 2.5.1
 */
public class StringUtils {

    public static final String LINE_SEPARATOR = System.getProperty("line.separator");

    private StringUtils() {
        throw new UnsupportedOperationException("StringUtils is not instantiable!");
    }

    /**
     * Join the provided {@code elements} separated by the {@code delimiter}.
     *
     * @param delimiter delimiter
     * @param elements elements to join
     * @return the {link @String} result obtained from joining all elements
     */
    public static String join(CharSequence delimiter, CharSequence... elements) {
        StringBuilder builder = new StringBuilder();

        boolean first = true;

        for (CharSequence element : elements) {
            if(first) {
                first = false;
            } else {
                builder.append(delimiter);
            }
            builder.append(element);
        }

        return builder.toString();
    }

    /**
     * Check if the String value is null, empty or contains only whitespace characters.
     * @param value String value
     * @return if the string is blank
     */
    public static boolean isBlank(String value) {
        return value == null || value.isEmpty() || value.trim().isEmpty();
    }

    /**
     * Transform string to lowercase.
     *
     * @param value String value
     * @return String value in lowercase
     */
    public static String toLowercase(String value) {
        if(isBlank(value)) {
            return value;
        }
        return value.toLowerCase(Locale.ROOT);
    }
}

package com.vladmihalcea.hibernate.type.util;

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
                builder.append(LINE_SEPARATOR);
            }
            builder.append(element);
        }

        return builder.toString();
    }

}

package io.hypersistence.utils.hibernate.util;

/**
 * Emulates a Java 8 Objects class.
 *
 * @author Vlad Mihalcea
 */
public class Objects {
    public static void requireNonNull(Object object) {
        if(object == null) {
            throw new NullPointerException();
        }
    }

    public static boolean equals(Object first, Object second) {
        if(first == second) {
            return true;
        }
        return first != null && first.equals(second);
    }

    public static int hashCode(Object o) {
        return o != null ? o.hashCode() : 0;
    }
}

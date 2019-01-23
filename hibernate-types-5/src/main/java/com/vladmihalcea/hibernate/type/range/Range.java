package com.vladmihalcea.hibernate.type.range;

import com.vladmihalcea.hibernate.type.util.Objects;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Arrays;

/**
 * Represents the range/interval with two bounds. Abstraction follows the semantics of the mathematical interval. The
 * range can be unbounded or open from the left or/and unbounded from the right. The range supports half-open or closed
 * bounds on both sides.
 *
 * <p>
 * The class has some very simple methods for usability. For example {@link Range#contains(Comparable)} method can tell user whether
 * this range contains argument or not. The {@link Range#contains(Range)} helps to find out whether this range fully
 * enclosing argument or not.
 * <p>
 * For more details about how to use it,
 * check out <a href="https://vladmihalcea.com/map-postgresql-range-column-type-jpa-hibernate/">this article</a>
 * on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Edgar Asatryan
 * @author Vlad Mihalcea
 */
public final class Range<T extends Comparable> implements Serializable {

    private static final int LOWER_INCLUSIVE = 1 << 1;
    private static final int LOWER_EXCLUSIVE = 1 << 2;
    private static final int UPPER_INCLUSIVE = 1 << 3;
    private static final int UPPER_EXCLUSIVE = 1 << 4;
    private static final int LOWER_INFINITE = (1 << 5) | LOWER_EXCLUSIVE;
    private static final int UPPER_INFINITE = (1 << 6) | UPPER_EXCLUSIVE;

    private final T lower;
    private final T upper;
    private final int mask;
    private final Class<T> clazz;

    private Range(T lower, T upper, int mask, Class<T> clazz) {
        this.lower = lower;
        this.upper = upper;
        this.mask = mask;
        this.clazz = clazz;

        if (isBounded() && lower.compareTo(upper) > 0) {
            throw new IllegalArgumentException("The lower bound is greater then upper!");
        }
    }

    /**
     * Creates the closed range with provided bounds.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     [a, b] = {x | a <= x <= b}
     * }</pre>.
     *
     * @param lower The lower bound, never null.
     * @param upper The upper bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The closed range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> closed(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<T>(lower, upper, LOWER_INCLUSIVE | UPPER_INCLUSIVE, (Class<T>) lower.getClass());
    }

    /**
     * Creates the open range with provided bounds.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     (a, b) = {x | a < x < b}
     * }</pre>
     *
     * @param lower The lower bound, never null.
     * @param upper The upper bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> open(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<T>(lower, upper, LOWER_EXCLUSIVE | UPPER_EXCLUSIVE, (Class<T>) lower.getClass());
    }

    /**
     * Creates the left-open, right-closed range with provided bounds.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     (a, b] = {x | a < x <= b}
     * }</pre>
     *
     * @param lower The lower bound, never null.
     * @param upper The upper bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> openClosed(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<T>(lower, upper, LOWER_EXCLUSIVE | UPPER_INCLUSIVE, (Class<T>) lower.getClass());
    }

    /**
     * Creates the left-closed, right-open range with provided bounds.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     [a, b) = {x | a <= x < b}
     * }</pre>
     *
     * @param lower The lower bound, never null.
     * @param upper The upper bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> closedOpen(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<T>(lower, upper, LOWER_INCLUSIVE | UPPER_EXCLUSIVE, (Class<T>) lower.getClass());
    }

    /**
     * Creates the left-bounded, left-open and right-unbounded range with provided lower bound.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     (a, +∞) = {x | x > a}
     * }</pre>
     *
     * @param lower The lower bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> openInfinite(T lower) {
        Objects.requireNonNull(lower);
        return new Range<T>(lower, null, LOWER_EXCLUSIVE | UPPER_INFINITE, (Class<T>) lower.getClass());
    }

    /**
     * Creates the left-bounded, left-closed and right-unbounded range with provided lower bound.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     [a, +∞) = {x | x >= a}
     * }</pre>
     *
     * @param lower The lower bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> closedInfinite(T lower) {
        Objects.requireNonNull(lower);
        return new Range(lower, null, LOWER_INCLUSIVE | UPPER_INFINITE, lower.getClass());
    }

    /**
     * Creates the left-unbounded, right-bounded and right-open range with provided upper bound.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     (-∞, b) = {x | x < b}
     * }</pre>
     *
     * @param upper The upper bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> infiniteOpen(T upper) {
        Objects.requireNonNull(upper);
        return new Range<T>(null, upper, UPPER_EXCLUSIVE | LOWER_INFINITE, (Class<T>) upper.getClass());
    }

    /**
     * Creates the left-unbounded, right-bounded and right-closed range with provided upper bound.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     (-∞, b] = {x | x =< b}
     * }</pre>
     *
     * @param upper The upper bound, never null.
     * @param <T>   The type of bounds.
     *
     * @return The range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> infiniteClosed(T upper) {
        Objects.requireNonNull(upper);
        return new Range<T>(null, upper, UPPER_INCLUSIVE | LOWER_INFINITE, (Class<T>) upper.getClass());
    }

    /**
     * Creates the unbounded at both ends range with provided upper bound.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     (-∞, +∞) = ℝ
     * }</pre>
     *
     * @param cls The range class, never null.
     * @param <T> The type of bounds.
     *
     * @return The infinite range.
     */
    @SuppressWarnings("unchecked")
    public static <T extends Comparable<?>> Range<T> infinite(Class<T> cls) {
        return new Range<T>(null, null, LOWER_INFINITE | UPPER_INFINITE, cls);
    }

    @SuppressWarnings("unchecked")
    public static <T extends Comparable> Range<T> ofString(String str, Function<String, T> converter, Class<T> cls) {
        int mask = str.charAt(0) == '[' ? LOWER_INCLUSIVE : LOWER_EXCLUSIVE;
        mask |= str.charAt(str.length() - 1) == ']' ? UPPER_INCLUSIVE : UPPER_EXCLUSIVE;

        int delim = str.indexOf(',');

        if (delim == -1) {
            throw new IllegalArgumentException("Cannot find comma character");
        }

        String lowerStr = str.substring(1, delim);
        String upperStr = str.substring(delim + 1, str.length() - 1);

        if (lowerStr.length() == 0) {
            mask |= LOWER_INFINITE;
        }

        if (upperStr.length() == 0) {
            mask |= UPPER_INFINITE;
        }

        T lower = null;
        T upper = null;

        if ((mask & LOWER_INFINITE) != LOWER_INFINITE) {
            lower = converter.apply(lowerStr);
        }

        if ((mask & UPPER_INFINITE) != UPPER_INFINITE) {
            upper = converter.apply(upperStr);
        }

        return new Range<T>(lower, upper, mask, cls);
    }

    /**
     * Creates the {@code BigDecimal} range from provided string:
     * <pre>{@code
     *     Range<BigDecimal> closed = Range.bigDecimalRange("[0.1,1.1]");
     *     Range<BigDecimal> halfOpen = Range.bigDecimalRange("(0.1,1.1]");
     *     Range<BigDecimal> open = Range.bigDecimalRange("(0.1,1.1)");
     *     Range<BigDecimal> leftUnbounded = Range.bigDecimalRange("(,1.1)");
     * }</pre>
     *
     * @param range The range string, for example {@literal "[5.5,7.8]"}.
     *
     * @return The range of {@code BigDecimal}s.
     *
     * @throws NumberFormatException when one of the bounds are invalid.
     */
    public static Range<BigDecimal> bigDecimalRange(String range) {
        return ofString(range, new Function<String, BigDecimal>() {
            @Override
            public BigDecimal apply(String s) {
                return new BigDecimal(s);
            }
        }, BigDecimal.class);
    }

    /**
     * Creates the {@code Integer} range from provided string:
     * <pre>{@code
     *     Range<Integer> closed = Range.integerRange("[1,5]");
     *     Range<Integer> halfOpen = Range.integerRange("(-1,1]");
     *     Range<Integer> open = Range.integerRange("(1,2)");
     *     Range<Integer> leftUnbounded = Range.integerRange("(,10)");
     *     Range<Integer> unbounded = Range.integerRange("(,)");
     * }</pre>
     *
     * @param range The range string, for example {@literal "[5,7]"}.
     *
     * @return The range of {@code Integer}s.
     *
     * @throws NumberFormatException when one of the bounds are invalid.
     */
    public static Range<Integer> integerRange(String range) {
        return ofString(range, new Function<String, Integer>() {
            @Override
            public Integer apply(String s) {
                return Integer.parseInt(s);
            }
        }, Integer.class);
    }

    /**
     * Creates the {@code Long} range from provided string:
     * <pre>{@code
     *     Range<Long> closed = Range.longRange("[1,5]");
     *     Range<Long> halfOpen = Range.longRange("(-1,1]");
     *     Range<Long> open = Range.longRange("(1,2)");
     *     Range<Long> leftUnbounded = Range.longRange("(,10)");
     *     Range<Long> unbounded = Range.longRange("(,)");
     * }</pre>
     *
     * @param range The range string, for example {@literal "[5,7]"}.
     *
     * @return The range of {@code Long}s.
     *
     * @throws NumberFormatException when one of the bounds are invalid.
     */
    public static Range<Long> longRange(String range) {
        return ofString(range, new Function<String, Long>() {
            @Override
            public Long apply(String s) {
                return Long.parseLong(s);
            }
        }, Long.class);
    }

    private static Function<String, String> unquote() {
        return new Function<String, String>() {
            @Override
            public String apply(String s) {
                if (s.charAt(0) == '\"' && s.charAt(s.length() - 1) == '\"') {
                    return s.substring(1, s.length() - 1);
                }

                return s;
            }
        };
    }

    private boolean isBounded() {
        return !hasMask(LOWER_INFINITE) && !hasMask(UPPER_INFINITE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Range)) return false;
        Range<?> range = (Range<?>) o;
        return mask == range.mask &&
                Objects.equals(lower, range.lower) &&
                Objects.equals(upper, range.upper) &&
                Objects.equals(clazz, range.clazz);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(new Object[]{lower, upper, mask, clazz});
    }

    @Override
    public String toString() {
        return "Range{" + "lower=" + lower +
                ", upper=" + upper +
                ", mask=" + mask +
                ", clazz=" + clazz +
                '}';
    }

    private boolean hasMask(int lowerInclusive) {
        return (mask & lowerInclusive) == lowerInclusive;
    }

    public boolean hasLowerBound() {
        return !hasMask(LOWER_INFINITE);
    }

    public boolean hasUpperBound() {
        return !hasMask(UPPER_INFINITE);
    }

    /**
     * Returns the lower bound of this range. If {@code null} is returned then this range is left-unbounded.
     *
     * @return The lower bound.
     */
    public T lower() {
        return lower;
    }

    /**
     * Returns the upper bound of this range. If {@code null} is returned then this range is right-unbounded.
     *
     * @return The upper bound.
     */
    public T upper() {
        return upper;
    }

    /**
     * Determines whether this range contains this point or not.
     * <p>
     * For example:
     * <pre>{@code
     *     assertTrue(integerRange("[1,2]").contains(1))
     *     assertTrue(integerRange("[1,2]").contains(2))
     *     assertTrue(integerRange("[-1,1]").contains(0))
     *     assertTrue(infinity(Integer.class).contains(Integer.MAX_VALUE))
     *     assertTrue(infinity(Integer.class).contains(Integer.MIN_VALUE))
     *
     *     assertFalse(integerRange("(1,2]").contains(1))
     *     assertFalse(integerRange("(1,2]").contains(3))
     *     assertFalse(integerRange("[-1,1]").contains(0))
     * }</pre>
     *
     * @param point The point to check.
     *
     * @return Whether {@code point} in this range or not.
     */
    @SuppressWarnings("unchecked")
    public boolean contains(T point) {
        boolean l = hasLowerBound();
        boolean u = hasUpperBound();

        if (l && u) {
            boolean inLower = hasMask(LOWER_INCLUSIVE) ? lower.compareTo(point) <= 0 : lower.compareTo(point) < 0;
            boolean inUpper = hasMask(UPPER_INCLUSIVE) ? upper.compareTo(point) >= 0 : upper.compareTo(point) > 0;

            return inLower && inUpper;
        } else if (l) {
            return hasMask(LOWER_INCLUSIVE) ? lower.compareTo(point) <= 0 : lower.compareTo(point) < 0;
        } else if (u) {
            return hasMask(UPPER_INCLUSIVE) ? upper.compareTo(point) >= 0 : upper.compareTo(point) > 0;
        }

        // INFINITY
        return true;
    }

    /**
     * Determines whether this range contains this point or not.
     * <p>
     * For example:
     * <pre>{@code
     *     assertTrue(integerRange("[-2,2]").contains(integerRange("[-1,1]")))
     *     assertTrue(integerRange("(,)").contains(integerRange("(,)"))
     *
     *     assertFalse(integerRange("[-2,2)").contains(integerRange("[-1,2]")))
     *     assertFalse(integerRange("(-2,2]").contains(1))
     * }</pre>
     *
     * @param range The range to check.
     *
     * @return Whether {@code range} in this range or not.
     */
    public boolean contains(Range<T> range) {
        return (!range.hasLowerBound() || contains(range.lower)) && (!range.hasUpperBound() || contains(range.upper));
    }

    public String asString() {
        StringBuilder sb = new StringBuilder();

        sb.append(hasMask(LOWER_INCLUSIVE) ? '[' : '(')
                .append(hasLowerBound() ? boundToString().apply(lower) : "")
                .append(",")
                .append(hasUpperBound() ? boundToString().apply(upper) : "")
                .append(hasMask(UPPER_INCLUSIVE) ? ']' : ')');

        return sb.toString();
    }

    private Function<T, String> boundToString() {
        return new Function<T, String>() {
            @Override
            public String apply(T t) {
                return t.toString();
            }
        };
    }

    Class<T> getClazz() {
        return clazz;
    }

    public interface Function<T, R> {
        
        R apply(T t);
    }
}

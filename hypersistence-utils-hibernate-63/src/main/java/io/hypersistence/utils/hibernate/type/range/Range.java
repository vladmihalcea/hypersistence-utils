package io.hypersistence.utils.hibernate.type.range;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Objects;
import java.util.function.Function;

/**
 * Represents the range/interval with two bounds. Abstraction follows the semantics of the mathematical interval. The
 * range can be unbounded, empty or open from the left or/and unbounded from the right. The range supports half-open or closed
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
public final class Range<T extends Comparable<? super T>> implements Serializable {

    public static final int LOWER_INCLUSIVE = 1 << 1;
    public static final int LOWER_EXCLUSIVE = 1 << 2;
    public static final int UPPER_INCLUSIVE = 1 << 3;
    public static final int UPPER_EXCLUSIVE = 1 << 4;
    public static final int LOWER_INFINITE = (1 << 5) | LOWER_EXCLUSIVE;
    public static final int UPPER_INFINITE = (1 << 6) | UPPER_EXCLUSIVE;

    public static final String EMPTY = "empty";

    public static final String INFINITY = "infinity";

    private static final DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendPattern(".")
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, false)
        .optionalEnd()
        .toFormatter();

    private static final DateTimeFormatter ZONE_DATE_TIME = new DateTimeFormatterBuilder()
        .appendPattern("yyyy-MM-dd HH:mm:ss")
        .optionalStart()
        .appendPattern(".")
        .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, false)
        .optionalEnd()
        .appendOffset("+HH:mm", "Z")
        .toFormatter();

    private final T lower;
    private final T upper;
    private final int mask;
    private final Class<T> clazz;

    private Range(T lower, T upper, int mask, Class<T> clazz) {
        this.lower = lower;
        this.upper = upper;
        this.mask = mask;
        this.clazz = clazz;

        if (isBounded() && lower != null && upper != null && lower.compareTo(upper) > 0) {
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
    public static <T extends Comparable<? super T>> Range<T> closed(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<>(lower, upper, LOWER_INCLUSIVE | UPPER_INCLUSIVE, (Class<T>) lower.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> open(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<>(lower, upper, LOWER_EXCLUSIVE | UPPER_EXCLUSIVE, (Class<T>) lower.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> openClosed(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<>(lower, upper, LOWER_EXCLUSIVE | UPPER_INCLUSIVE, (Class<T>) lower.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> closedOpen(T lower, T upper) {
        Objects.requireNonNull(lower);
        Objects.requireNonNull(upper);
        return new Range<>(lower, upper, LOWER_INCLUSIVE | UPPER_EXCLUSIVE, (Class<T>) lower.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> openInfinite(T lower) {
        Objects.requireNonNull(lower);
        return new Range<>(lower, null, LOWER_EXCLUSIVE | UPPER_INFINITE, (Class<T>) lower.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> closedInfinite(T lower) {
        Objects.requireNonNull(lower);
        return new Range<>(lower, null, LOWER_INCLUSIVE | UPPER_INFINITE, (Class<T>) lower.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> infiniteOpen(T upper) {
        Objects.requireNonNull(upper);
        return new Range<>(null, upper, UPPER_EXCLUSIVE | LOWER_INFINITE, (Class<T>) upper.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> infiniteClosed(T upper) {
        Objects.requireNonNull(upper);
        return new Range<>(null, upper, UPPER_INCLUSIVE | LOWER_INFINITE, (Class<T>) upper.getClass());
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
    public static <T extends Comparable<? super T>> Range<T> infinite(Class<T> cls) {
        return new Range<>(null, null, LOWER_INFINITE | UPPER_INFINITE, cls);
    }

    /**
     * Creates the empty range. In other words the range that contains no points.
     * <p>
     * The mathematical equivalent will be:
     * <pre>{@code
     *     (a, a) = ∅
     * }</pre>
     *
     * @param cls The range class, never null.
     * @param <R> The type of bounds.
     *
     * @return The empty range.
     */
    public static <R extends Comparable<? super R>> Range<R> emptyRange(Class<R> cls) {
        return new Range<>(
            null,
            null,
            LOWER_EXCLUSIVE | UPPER_EXCLUSIVE,
            cls
        );
    }

    public static <T extends Comparable<? super T>> Range<T> ofString(String str, Function<String, T> converter, Class<T> clazz) {
        if(str.equals(EMPTY)) {
            return emptyRange(clazz);
        }

        int mask = str.charAt(0) == '[' ? LOWER_INCLUSIVE : LOWER_EXCLUSIVE;
        mask |= str.charAt(str.length() - 1) == ']' ? UPPER_INCLUSIVE : UPPER_EXCLUSIVE;

        int delim = str.indexOf(',');

        if (delim == -1) {
            throw new IllegalArgumentException("Cannot find comma character");
        }

        String lowerStr = str.substring(1, delim);
        String upperStr = str.substring(delim + 1, str.length() - 1);

        if (lowerStr.length() == 0 || lowerStr.endsWith(INFINITY)) {
            mask |= LOWER_INFINITE;
        }

        if (upperStr.length() == 0 || upperStr.endsWith(INFINITY)) {
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

        return new Range<>(lower, upper, mask, clazz);
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
        return ofString(range, BigDecimal::new, BigDecimal.class);
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
        return ofString(range, Integer::parseInt, Integer.class);
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
        return ofString(range, Long::parseLong, Long.class);
    }

    /**
     * Creates the {@code LocalDateTime} range from provided string:
     * <pre>{@code
     *     Range<LocalDateTime> closed = Range.localDateTimeRange("[2014-04-28 16:00:49,2015-04-28 16:00:49]");
     *     Range<LocalDateTime> quoted = Range.localDateTimeRange("[\"2014-04-28 16:00:49\",\"2015-04-28 16:00:49\"]");
     *     Range<LocalDateTime> iso = Range.localDateTimeRange("[\"2014-04-28T16:00:49.2358\",\"2015-04-28T16:00:49\"]");
     * }</pre>
     * <p>
     * The valid formats for bounds are:
     * <ul>
     * <li>yyyy-MM-dd HH:mm:ss[.SSSSSS]</li>
     * <li>yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]</li>
     * </ul>
     *
     * @param range The range string, for example {@literal "[2014-04-28 16:00:49,2015-04-28 16:00:49]"}.
     *
     * @return The range of {@code LocalDateTime}s.
     *
     * @throws DateTimeParseException when one of the bounds are invalid.
     */
    public static Range<LocalDateTime> localDateTimeRange(String range) {
        return ofString(range, parseLocalDateTime().compose(unquote()), LocalDateTime.class);
    }

    /**
     * Creates the {@code LocalDate} range from provided string:
     * <pre>{@code
     *     Range<LocalDate> closed = Range.localDateRange("[2014-04-28,2015-04-289]");
     *     Range<LocalDate> quoted = Range.localDateRange("[\"2014-04-28\",\"2015-04-28\"]");
     *     Range<LocalDate> iso = Range.localDateRange("[\"2014-04-28\",\"2015-04-28\"]");
     * }</pre>
     * <p>
     * The valid formats for bounds are:
     * <ul>
     * <li>yyyy-MM-dd</li>
     * <li>yyyy-MM-dd</li>
     * </ul>
     *
     * @param range The range string, for example {@literal "[2014-04-28,2015-04-28]"}.
     *
     * @return The range of {@code LocalDate}s.
     *
     * @throws DateTimeParseException when one of the bounds are invalid.
     */
    public static Range<LocalDate> localDateRange(String range) {
        Function<String, LocalDate> parseLocalDate = LocalDate::parse;
        return ofString(range, parseLocalDate.compose(unquote()), LocalDate.class);
    }

    /**
     * Creates the {@code ZonedDateTime} range from provided string:
     * <pre>{@code
     *     Range<ZonedDateTime> closed = Range.zonedDateTimeRange("[2007-12-03T10:15:30+01:00\",\"2008-12-03T10:15:30+01:00]");
     *     Range<ZonedDateTime> quoted = Range.zonedDateTimeRange("[\"2007-12-03T10:15:30+01:00\",\"2008-12-03T10:15:30+01:00\"]");
     *     Range<ZonedDateTime> iso = Range.zonedDateTimeRange("[2011-12-03T10:15:30+01:00[Europe/Paris], 2012-12-03T10:15:30+01:00[Europe/Paris]]");
     * }</pre>
     * <p>
     * The valid formats for bounds are:
     * <ul>
     * <li>yyyy-MM-dd HH:mm:ss[.SSSSSS]X</li>
     * <li>yyyy-MM-dd'T'HH:mm:ss[.SSSSSS]X</li>
     * </ul>
     *
     * @param rangeStr The range string, for example {@literal "[2011-12-03T10:15:30+01:00,2012-12-03T10:15:30+01:00]"}.
     *
     * @return The range of {@code ZonedDateTime}s.
     *
     * @throws DateTimeParseException   when one of the bounds are invalid.
     * @throws IllegalArgumentException when bounds time zones are different.
     */
    public static Range<ZonedDateTime> zonedDateTimeRange(String rangeStr) {
        Range<ZonedDateTime> range = ofString(rangeStr, parseZonedDateTime().compose(unquote()), ZonedDateTime.class);
        if (range.hasLowerBound() && range.hasUpperBound() && !EMPTY.equals(rangeStr)) {
            ZoneId lowerZone = range.lower().getZone();
            ZoneId upperZone = range.upper().getZone();
            if (!lowerZone.equals(upperZone)) {
                Duration lowerDst = ZoneId.systemDefault().getRules().getDaylightSavings(range.lower().toInstant());
                Duration upperDst = ZoneId.systemDefault().getRules().getDaylightSavings(range.upper().toInstant());
                long dstSeconds = upperDst.minus(lowerDst).getSeconds();
                if(dstSeconds < 0 ) {
                    dstSeconds *= -1;
                }
                long zoneDriftSeconds = ((ZoneOffset) lowerZone).getTotalSeconds() - ((ZoneOffset) upperZone).getTotalSeconds();
                if (zoneDriftSeconds < 0) {
                    zoneDriftSeconds *= -1;
                }

                if (dstSeconds != zoneDriftSeconds) {
                    throw new IllegalArgumentException("The upper and lower bounds must be in same time zone!");
                }
            }
        }
        return range;
    }

    private static Function<String, LocalDateTime> parseLocalDateTime() {
        return s -> {
            try {
                return LocalDateTime.parse(s, LOCAL_DATE_TIME);
            } catch (DateTimeParseException e) {
                return LocalDateTime.parse(s);
            }
        };
    }

    private static Function<String, ZonedDateTime> parseZonedDateTime() {
        return s -> {
            try {
                return ZonedDateTime.parse(s, ZONE_DATE_TIME);
            } catch (DateTimeParseException e) {
                return ZonedDateTime.parse(s);
            }
        };
    }

    private static Function<String, String> unquote() {
        return s -> {
            if (s.charAt(0) == '\"' && s.charAt(s.length() - 1) == '\"') {
                return s.substring(1, s.length() - 1);
            }

            return s;
        };
    }

    public boolean isBounded() {
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
        return Objects.hash(lower, upper, mask, clazz);
    }

    @Override
    public String toString() {
        return "Range{" + "lower=" + lower +
                ", upper=" + upper +
                ", mask=" + mask +
                ", clazz=" + clazz +
                '}';
    }

    public boolean hasMask(int flag) {
        return (mask & flag) == flag;
    }
    
    public boolean isLowerBoundClosed() {
    	return hasLowerBound() && hasMask(LOWER_INCLUSIVE);
    }
    
    public boolean isUpperBoundClosed() {
    	return hasUpperBound() && hasMask(UPPER_INCLUSIVE);
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
    public boolean contains(T point) {
        if (isEmpty()) {
            return false;
        }

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
     * Determines whether this range contains this range or not.
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
        return !isEmpty() && (!range.hasLowerBound() || contains(range.lower)) && (!range.hasUpperBound() || contains(range.upper));
    }

    /**
     * Determines whether this range is empty or not.
     * <p>
     * For example:
     * <pre>{@code
     *     assertFalse(integerRange("empty").contains(1))
     * }</pre>
     *
     * @return Whether {@code range} in this range or not.
     */
    public boolean isEmpty() {
        return isBoundedOpen() && hasEqualBounds();
    }

    public boolean hasEqualBounds() {
        return lower == null && upper == null
            || lower != null && upper != null && lower.compareTo(upper) == 0;
    }

    public boolean isBoundedOpen() {
        return isBounded() && !isLowerBoundClosed() && !isUpperBoundClosed();
    }

    public String asString() {
        if (lower == null && upper == null && isBoundedOpen()) {
          return EMPTY;
        }

        StringBuilder sb = new StringBuilder();

        sb.append(hasMask(LOWER_INCLUSIVE) ? '[' : '(')
                .append(hasLowerBound() ? boundToString().apply(lower) : "")
                .append(",")
                .append(hasUpperBound() ? boundToString().apply(upper) : "")
                .append(hasMask(UPPER_INCLUSIVE) ? ']' : ')');

        return sb.toString();
    }

    private Function<T, String> boundToString() {
        return t -> {
            if (clazz.equals(ZonedDateTime.class)) {
                return ZONE_DATE_TIME.format((ZonedDateTime) t);
            }

            return t.toString();
        };
    }

    Class<T> getClazz() {
        return clazz;
    }
}

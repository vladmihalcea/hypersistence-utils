package io.hypersistence.utils.hibernate.type.range.spring;

import io.hypersistence.utils.common.ReflectionUtils;
import io.hypersistence.utils.hibernate.type.ImmutableType;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.usertype.DynamicParameterizedType;
import org.springframework.data.domain.Range;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.util.Properties;
import java.util.function.Function;

public class PostgreSQLSpringRangeType extends ImmutableType<Range> implements DynamicParameterizedType {

    private static final Range<Integer> EMPTY_INT_RANGE = Range.rightOpen(Integer.MIN_VALUE, Integer.MIN_VALUE);
    
    private static final Range<Long> EMPTY_LONG_RANGE = Range.rightOpen(Long.MIN_VALUE, Long.MIN_VALUE);
    
    private static final Range<BigDecimal> EMPTY_BIGDECIMAL_RANGE = Range.rightOpen(BigDecimal.ZERO, BigDecimal.ZERO);
    
    private static final Range<LocalDateTime> EMPTY_LOCALDATETIME_RANGE = Range.rightOpen(LocalDateTime.MIN, LocalDateTime.MIN);
    
    private static final Range<OffsetDateTime> EMPTY_OFFSETDATETIME_RANGE = Range.rightOpen(OffsetDateTime.MIN, OffsetDateTime.MIN);
    
    private static final Range<ZonedDateTime> EMPTY_ZONEDDATETIME_RANGE = Range.rightOpen(OffsetDateTime.MIN.toZonedDateTime(), OffsetDateTime.MIN.toZonedDateTime());
    
    private static final Range<LocalDate> EMPTY_DATE_RANGE = Range.rightOpen(LocalDate.MIN, LocalDate.MIN);

    private static final DateTimeFormatter LOCAL_DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendPattern(".")
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, false)
            .optionalEnd()
            .toFormatter();

    private static final DateTimeFormatter OFFSET_DATE_TIME = new DateTimeFormatterBuilder()
            .appendPattern("yyyy-MM-dd HH:mm:ss")
            .optionalStart()
            .appendPattern(".")
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 6, false)
            .optionalEnd()
            .appendPattern("X")
            .toFormatter();

    public static final PostgreSQLSpringRangeType INSTANCE = new PostgreSQLSpringRangeType();

    private Type type;

    private Class<?> elementType;

    public PostgreSQLSpringRangeType() {
        super(Range.class);
    }

    public PostgreSQLSpringRangeType(Class<?> elementType) {
        super(Range.class);
        this.elementType = elementType;
    }
    
    @Override
    protected Range get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        Object pgObject = rs.getObject(position);

        if (pgObject == null) {
            return null;
        }

        String type = ReflectionUtils.invokeGetter(pgObject, "type");
        String value = ReflectionUtils.invokeGetter(pgObject, "value");

        switch (type) {
            case "int4range":
                return integerRange(value);
            case "int8range":
                return longRange(value);
            case "numrange":
                return bigDecimalRange(value);
            case "tsrange":
                return localDateTimeRange(value);
            case "tstzrange":
                return ZonedDateTime.class.equals(elementType) ? zonedDateTimeRange(value) : offsetDateTimeRange(value);
            case "daterange":
                return localDateRange(value);
            default:
                throw new HibernateException(
                        new IllegalStateException("The range type [" + type + "] is not supported!")
                );
        }
    }

    @Override
    protected void set(PreparedStatement st, Range range, int index, SharedSessionContractImplementor session) throws SQLException {
        if (range == null) {
            st.setNull(index, Types.OTHER);
        } else {
            Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
            ReflectionUtils.invokeSetter(holder, "type", determineRangeType(range));
            ReflectionUtils.invokeSetter(holder, "value", asString(range));
            st.setObject(index, holder);
        }
    }

    @Override
    public Range fromStringValue(CharSequence sequence) throws HibernateException {
        if (sequence != null) {
            String stringValue = (String) sequence;
            Class clazz = rangeClass();
            if (clazz != null) {
                if (Integer.class.isAssignableFrom(clazz)) {
                    return integerRange(stringValue);
                }
                if (Long.class.isAssignableFrom(clazz)) {
                    return longRange(stringValue);
                }
                if (BigDecimal.class.isAssignableFrom(clazz)) {
                    return bigDecimalRange(stringValue);
                }
                if (LocalDateTime.class.isAssignableFrom(clazz)) {
                    return localDateTimeRange(stringValue);
                }
                if (ZonedDateTime.class.isAssignableFrom(clazz)) {
                    return zonedDateTimeRange(stringValue);
                }
                if (LocalDate.class.isAssignableFrom(clazz)) {
                    return localDateRange(stringValue);
                }
                throw new HibernateException(
                        new IllegalStateException("The range type [" + type + "] is not supported!")
                );
            }
        }
        return null;
    }

    @Override
    public void setParameterValues(Properties properties) {
        final ParameterType parameterType = (ParameterType) properties.get(PARAMETER_TYPE);

        type = parameterType.getReturnedClass();

        final Type returnedJavaType = parameterType.getReturnedJavaType();
        if (returnedJavaType instanceof ParameterizedType) {
            elementType = (Class<?>) ((ParameterizedType) returnedJavaType).getActualTypeArguments()[0];
        }
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    private String determineRangeType(Range<?> range) {
        Type clazz = this.elementType;

        if (clazz.equals(Integer.class)) {
            return "int4range";
        } else if (clazz.equals(Long.class)) {
            return "int8range";
        } else if (clazz.equals(BigDecimal.class)) {
            return "numrange";
        } else if (clazz.equals(LocalDateTime.class)) {
            return "tsrange";
        } else if (clazz.equals(ZonedDateTime.class) || clazz.equals(OffsetDateTime.class)) {
            return "tstzrange";
        } else if (clazz.equals(LocalDate.class)) {
            return "daterange";
        }

        throw new HibernateException(
                new IllegalStateException("The class [" + clazz + "] is not supported!")
        );
    }

    public static <T extends Comparable<?>> Range<T> ofString(String str, Function<String, T> converter, Class<T> clazz) {
        if ("empty".equals(str)) {
            if (clazz.equals(Integer.class)) {
                return (Range<T>) EMPTY_INT_RANGE;
            } else if (clazz.equals(Long.class)) {
                return (Range<T>) EMPTY_LONG_RANGE;
            } else if (clazz.equals(BigDecimal.class)) {
                return (Range<T>) EMPTY_BIGDECIMAL_RANGE;
            } else if (clazz.equals(LocalDateTime.class)) {
                return (Range<T>) EMPTY_LOCALDATETIME_RANGE;
            } else if (clazz.equals(ZonedDateTime.class)) {
                return (Range<T>) EMPTY_ZONEDDATETIME_RANGE;
            } else if (clazz.equals(OffsetDateTime.class)) {
                return (Range<T>) EMPTY_OFFSETDATETIME_RANGE;
            } else if (clazz.equals(LocalDate.class)) {
                return (Range<T>) EMPTY_DATE_RANGE;
            }
        }

        int delim = str.indexOf(',');

        if (delim == -1) {
            throw new HibernateException(
                    new IllegalArgumentException("Cannot find comma character")
            );
        }

        String lowerStr = str.substring(1, delim);
        String upperStr = str.substring(delim + 1, str.length() - 1);

        Range.Bound<T> lowerBound =  Range.Bound.unbounded();
        Range.Bound<T> upperBound = Range.Bound.unbounded();

        if (!lowerStr.isEmpty()) {
            T lower = converter.apply(lowerStr);
            lowerBound = str.charAt(0) == '[' ? Range.Bound.inclusive(lower) : Range.Bound.exclusive(lower);
        }

        if (!upperStr.isEmpty()) {
            T upper = converter.apply(upperStr);
            upperBound = str.charAt(str.length() - 1) == ']' ? Range.Bound.inclusive(upper) : Range.Bound.exclusive(upper);
        }

        return Range.of(lowerBound, upperBound);

    }

    public static Range<BigDecimal> bigDecimalRange(String range) {
        return ofString(range, BigDecimal::new, BigDecimal.class);
    }

    public static Range<Integer> integerRange(String range) {
        return ofString(range, Integer::parseInt, Integer.class);
    }

    public static Range<Long> longRange(String range) {
        return ofString(range, Long::parseLong, Long.class);
    }

    public static Range<LocalDateTime> localDateTimeRange(String range) {
        return ofString(range, parseLocalDateTime().compose(unquote()), LocalDateTime.class);
    }

    public static Range<LocalDate> localDateRange(String range) {
        Function<String, LocalDate> parseLocalDate = LocalDate::parse;
        return ofString(range, parseLocalDate.compose(unquote()), LocalDate.class);
    }

    public static Range<ZonedDateTime> zonedDateTimeRange(String rangeStr) {
        Range<ZonedDateTime> range = ofString(rangeStr, parseZonedDateTime().compose(unquote()), ZonedDateTime.class);
        if (range.getLowerBound().isBounded() && range.getUpperBound().isBounded()) {
            ZoneId lowerZone = range.getLowerBound().getValue().get().getZone();
            ZoneId upperZone = range.getUpperBound().getValue().get().getZone();
            if (!lowerZone.equals(upperZone)) {
                Duration lowerDst = ZoneId.systemDefault().getRules().getDaylightSavings(range.getLowerBound().getValue().get().toInstant());
                Duration upperDst = ZoneId.systemDefault().getRules().getDaylightSavings(range.getUpperBound().getValue().get().toInstant());
                long dstSeconds = upperDst.minus(lowerDst).getSeconds();
                if (dstSeconds < 0) {
                    dstSeconds *= -1;
                }
                long zoneDriftSeconds = ((ZoneOffset) lowerZone).getTotalSeconds() - ((ZoneOffset) upperZone).getTotalSeconds();
                if (zoneDriftSeconds < 0) {
                    zoneDriftSeconds *= -1;
                }

                if (dstSeconds != zoneDriftSeconds) {
                    throw new HibernateException(
                            new IllegalArgumentException("The upper and lower bounds must be in same time zone!")
                    );
                }
            }
        }
        return range;
    }

    public static Range<OffsetDateTime> offsetDateTimeRange(String rangeStr) {
        return ofString(rangeStr, parseOffsetDateTime().compose(unquote()), OffsetDateTime.class);
    }

    private static Function<String, LocalDateTime> parseLocalDateTime() {
        return str -> {
            try {
                return LocalDateTime.parse(str, LOCAL_DATE_TIME);
            } catch (DateTimeParseException e) {
                return LocalDateTime.parse(str);
            }
        };
    }

    private static Function<String, ZonedDateTime> parseZonedDateTime() {
        return s -> {
            try {
                return ZonedDateTime.parse(s, OFFSET_DATE_TIME);
            } catch (DateTimeParseException e) {
                return ZonedDateTime.parse(s);
            }
        };
    }

    private static Function<String, OffsetDateTime> parseOffsetDateTime() {
        return s -> {
            try {
                return OffsetDateTime.parse(s, OFFSET_DATE_TIME);
            } catch (DateTimeParseException e) {
                return OffsetDateTime.parse(s);
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

    String asString(Range<?> range) {

        if (!range.getLowerBound().isBounded() && !range.getUpperBound().isBounded()) {
            return "(,)";
        }
        if (range.getLowerBound().getValue().equals(range.getUpperBound().getValue())) {
            return "empty";
        }

        Range.Bound<?> lower = range.getLowerBound();
        Range.Bound<?> upper = range.getUpperBound();

        StringBuilder sb = new StringBuilder();

        sb.append(lower.isBounded() ? lower.isInclusive() ? "[" : "(" : "(");
        lower.getValue().ifPresent(sb::append);
        sb.append(",");
        upper.getValue().ifPresent(sb::append);
        sb.append(upper.isBounded() ? upper.isInclusive() ? "]": ")": ")");

        return sb.toString();
    }

    private Class rangeClass() {
            if (type instanceof ParameterizedType) {
                Type[] types = ((ParameterizedType) type).getActualTypeArguments();
                return (Class) types[0];
            }
            return null;
        }
}

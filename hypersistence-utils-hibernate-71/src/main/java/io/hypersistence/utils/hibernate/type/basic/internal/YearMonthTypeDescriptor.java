package io.hypersistence.utils.hibernate.type.basic.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
public class YearMonthTypeDescriptor
        extends AbstractClassJavaType<YearMonth> {

    public static final YearMonthTypeDescriptor INSTANCE = new YearMonthTypeDescriptor();

    public YearMonthTypeDescriptor() {
        super(YearMonth.class);
    }

    @Override
    public boolean areEqual(YearMonth one, YearMonth another) {
        return Objects.equals(one, another);
    }

    @Override
    public String toString(YearMonth value) {
        return value.toString();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(YearMonth value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (X) toString(value);
        }
        if (Number.class.isAssignableFrom(type)) {
            Integer numericValue = (value.getYear() * 100) + value.getMonth().getValue();
            return (X) (numericValue);
        }
        if (Timestamp.class.isAssignableFrom(type)) {
            return (X) java.sql.Timestamp.valueOf(value.atDay(1).atStartOfDay());
        }
        if (Date.class.isAssignableFrom(type)) {
            return (X) java.sql.Date.valueOf(value.atDay(1));
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> YearMonth wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return fromString((String) value);
        }
        if (value instanceof Number) {
            int numericValue = ((Number) (value)).intValue();
            if(numericValue > 0) {
                int year = numericValue / 100;
                int month = numericValue % 100;
                return YearMonth.of(year, month);
            } else {
                return null;
            }
        }
        if (value instanceof Date) {
            Date date = (Date) value;
            return YearMonth.from(Instant.ofEpochMilli(date.getTime())
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate());
        }
        throw unknownWrap(value.getClass());
    }
}

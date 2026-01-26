package io.hypersistence.utils.hibernate.type.basic.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;

import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.ZoneId;
import java.util.Date;
import java.util.Objects;

/**
 * @author Mladen Savic (mladensavic94@gmail.com)
 */

public class MonthDayTypeDescriptor extends AbstractClassJavaType<MonthDay> {

    public static final MonthDayTypeDescriptor INSTANCE = new MonthDayTypeDescriptor();

    protected MonthDayTypeDescriptor() {
        super(MonthDay.class);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(MonthDay monthDay, Class<X> type, WrapperOptions wrapperOptions) {
        if (monthDay == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (X) toString(monthDay);
        }
        if (Number.class.isAssignableFrom(type)) {
            Integer numericValue = (monthDay.getMonthValue() * 100) + monthDay.getDayOfMonth();
            return (X) (numericValue);
        }
        if (Date.class.isAssignableFrom(type)) {
            int currentYear = LocalDate.now().getYear();
            return (X) java.sql.Date.valueOf(monthDay.atYear(currentYear));
        }

        throw unknownUnwrap(type);
    }

    @Override
    public <X> MonthDay wrap(X value, WrapperOptions wrapperOptions) {
        if (value == null) {
            return null;
        }
        if (value instanceof String) {
            return fromString((String) value);
        }
        if (value instanceof Number) {
            int numericValue = ((Number) (value)).intValue();
            int month = numericValue / 100;
            int dayOfMonth = numericValue % 100;
            return MonthDay.of(month, dayOfMonth);
        }
        if (value instanceof Date) {
            Date date = (Date) value;
            return MonthDay.from(Instant.ofEpochMilli(date.getTime())
                .atZone(ZoneId.systemDefault())
                .toLocalDate());
        }
        throw unknownWrap(value.getClass());
    }

    @Override
    public boolean areEqual(MonthDay one, MonthDay another) {
        return Objects.equals(one, another);
    }

    @Override
    public String toString(MonthDay value) {
        return value.toString();
    }
}

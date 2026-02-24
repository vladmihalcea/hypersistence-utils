package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.MutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.MonthDayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.DateJdbcType;

import java.time.Month;
import java.time.MonthDay;

/**
 * Maps a Java {@link java.time.MonthDay} object to a {@code DATE} column type.
 *
 * @author Mladen Savic (mladensavic94@gmail.com)
 */

public class MonthDayDateType extends DescriptorImmutableType<MonthDay, DateJdbcType, MonthDayTypeDescriptor> {

    public static final MonthDayDateType INSTANCE = new MonthDayDateType();

    public MonthDayDateType() {
        super(MonthDay.class, DateJdbcType.INSTANCE, MonthDayTypeDescriptor.INSTANCE);
    }

    public MonthDayDateType(Configuration configuration) {
        super(MonthDay.class, DateJdbcType.INSTANCE, MonthDayTypeDescriptor.INSTANCE, configuration);
    }

    public MonthDayDateType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "monthday-date";
    }

    @Override
    public MonthDay fromStringValue(CharSequence sequence) throws HibernateException {
        return sequence != null ? MonthDay.parse(sequence) : null;
    }
}

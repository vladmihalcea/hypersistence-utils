package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.MonthDayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import java.time.MonthDay;

/**
 * Maps a Java {@link java.time.MonthDay} object to a {@code INT} column type.
 *
 * @author Mladen Savic (mladensavic94@gmail.com)
 */
public class MonthDayIntegerType extends DescriptorImmutableType<MonthDay, IntegerJdbcType, MonthDayTypeDescriptor> {

    public static final MonthDayIntegerType INSTANCE = new MonthDayIntegerType();

    public MonthDayIntegerType() {
        super(
            MonthDay.class,
            IntegerJdbcType.INSTANCE,
            MonthDayTypeDescriptor.INSTANCE
        );
    }

    public MonthDayIntegerType(Configuration configuration) {
        super(
            MonthDay.class,
            IntegerJdbcType.INSTANCE,
            MonthDayTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public MonthDayIntegerType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "monthday-int";
    }

    @Override
    public MonthDay fromStringValue(CharSequence sequence) throws HibernateException {
        return sequence != null ? MonthDay.parse(sequence) : null;
    }
}

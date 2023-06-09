package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.Iso8601MonthMonthTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import java.time.Month;

/**
 * Maps a {@link Month} object type to a {@code INT}  column type
 * which is saved as value from 1 (January) to 12 (December),
 * according to the ISO 8601 standard.
 *
 * @author Martin Panzer
 */
public class Iso8601MonthType extends DescriptorImmutableType<Month, IntegerJdbcType, Iso8601MonthMonthTypeDescriptor> {

    public static final Iso8601MonthType INSTANCE = new Iso8601MonthType();

    public Iso8601MonthType() {
        super(
            Month.class,
            IntegerJdbcType.INSTANCE,
            Iso8601MonthMonthTypeDescriptor.INSTANCE
        );
    }

    public Iso8601MonthType(Configuration configuration) {
        super(
            Month.class,
            IntegerJdbcType.INSTANCE,
            Iso8601MonthMonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public Iso8601MonthType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "month";
    }

    @Override
    public Month fromStringValue(CharSequence sequence) throws HibernateException {
        return sequence != null ? Month.valueOf((String) sequence) : null;
    }
}
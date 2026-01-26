package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.YearMonthTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType;

import java.time.YearMonth;

/**
 * Maps a Java {@link YearMonth} object to a {@code TIMESTAMP} column type.
 * <p>
 *
 * @author Vlad Mihalcea
 */
public class YearMonthTimestampType extends DescriptorImmutableType<YearMonth, TimestampJdbcType, YearMonthTypeDescriptor> {

    public static final YearMonthTimestampType INSTANCE = new YearMonthTimestampType();

    public YearMonthTimestampType() {
        super(
            YearMonth.class,
            TimestampJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE
        );
    }

    public YearMonthTimestampType(Configuration configuration) {
        super(
            YearMonth.class,
            TimestampJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public YearMonthTimestampType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "yearmonth-timestamp";
    }

    @Override
    public YearMonth fromStringValue(CharSequence charSequence) throws HibernateException {
        return charSequence != null ? YearMonth.parse(charSequence) : null;
    }
}
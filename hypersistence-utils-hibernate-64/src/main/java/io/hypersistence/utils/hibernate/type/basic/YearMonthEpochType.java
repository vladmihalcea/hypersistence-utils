package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.YearMonthEpochTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.SmallIntJdbcType;

import java.time.YearMonth;

/**
 * Maps a Java {@link YearMonth} object to an small and continuous {@code INT} column type
 * which defines the months that passed since the Unix epoch.
 *
 * @author Vlad Mihalcea
 */
public class YearMonthEpochType extends DescriptorImmutableType<YearMonth, SmallIntJdbcType, YearMonthEpochTypeDescriptor> {

    public static final YearMonthEpochType INSTANCE = new YearMonthEpochType();

    public YearMonthEpochType() {
        super(
            YearMonth.class,
            SmallIntJdbcType.INSTANCE,
            YearMonthEpochTypeDescriptor.INSTANCE
        );
    }

    public YearMonthEpochType(Configuration configuration) {
        super(
            YearMonth.class,
            SmallIntJdbcType.INSTANCE,
            YearMonthEpochTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public YearMonthEpochType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "yearmonth-epoch";
    }

    @Override
    public YearMonth fromStringValue(CharSequence charSequence) throws HibernateException {
        return charSequence != null ? YearMonth.parse(charSequence) : null;
    }
}
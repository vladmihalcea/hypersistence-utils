package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.YearMonthTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.DateJdbcType;

import java.time.YearMonth;

/**
 * Maps a Java {@link java.time.YearMonth} object to a {@code DATE} column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/java-yearmonth-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearMonthDateType extends DescriptorImmutableType<YearMonth, DateJdbcType, YearMonthTypeDescriptor> {

    public static final YearMonthDateType INSTANCE = new YearMonthDateType();

    public YearMonthDateType() {
        super(
            YearMonth.class,
            DateJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE
        );
    }

    public YearMonthDateType(Configuration configuration) {
        super(
            YearMonth.class,
            DateJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public YearMonthDateType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "yearmonth-date";
    }

    @Override
    public YearMonth fromStringValue(CharSequence charSequence) throws HibernateException {
        return charSequence != null ? YearMonth.parse(charSequence) : null;
    }
}
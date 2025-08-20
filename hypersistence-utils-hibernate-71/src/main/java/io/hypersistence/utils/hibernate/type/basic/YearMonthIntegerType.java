package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.YearMonthTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.IntegerJdbcType;

import java.time.YearMonth;

/**
 * Maps a Java {@link YearMonth} object to an {@code INT} column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/java-yearmonth-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearMonthIntegerType extends DescriptorImmutableType<YearMonth, IntegerJdbcType, YearMonthTypeDescriptor> {

    public static final YearMonthIntegerType INSTANCE = new YearMonthIntegerType();

    public YearMonthIntegerType() {
        super(
            YearMonth.class,
            IntegerJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE
        );
    }

    public YearMonthIntegerType(Configuration configuration) {
        super(
            YearMonth.class,
            IntegerJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public YearMonthIntegerType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "yearmonth-int";
    }

    @Override
    public YearMonth fromStringValue(CharSequence charSequence) throws HibernateException {
        return charSequence != null ? YearMonth.parse(charSequence) : null;
    }
}
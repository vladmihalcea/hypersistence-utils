package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.YearTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.SmallIntJdbcType;

import java.time.Year;
import java.time.YearMonth;

/**
 * Maps a Java {@link Year} object to an {@code INT} column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/java-time-year-month-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearType extends DescriptorImmutableType<Year, SmallIntJdbcType, YearTypeDescriptor> {

    public static final YearType INSTANCE = new YearType();

    public YearType() {
        super(
            Year.class,
            SmallIntJdbcType.INSTANCE,
            YearTypeDescriptor.INSTANCE
        );
    }

    public YearType(Configuration configuration) {
        super(
            Year.class,
            SmallIntJdbcType.INSTANCE,
            YearTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public YearType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "year";
    }

    @Override
    public Year fromStringValue(CharSequence charSequence) throws HibernateException {
        return charSequence != null ? Year.parse(charSequence) : null;
    }
}
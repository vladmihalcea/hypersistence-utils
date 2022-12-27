package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.basic.internal.YearMonthTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.IntegerTypeDescriptor;

import java.time.YearMonth;

/**
 * Maps a Java {@link YearMonth} object to an {@code INT} column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/java-yearmonth-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class YearMonthIntegerType
        extends AbstractHibernateType<YearMonth> {

    public static final YearMonthIntegerType INSTANCE = new YearMonthIntegerType();

    public YearMonthIntegerType() {
        super(
            IntegerTypeDescriptor.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE
        );
    }

    public YearMonthIntegerType(Configuration configuration) {
        super(
            IntegerTypeDescriptor.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public String getName() {
        return "yearmonth-int";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}
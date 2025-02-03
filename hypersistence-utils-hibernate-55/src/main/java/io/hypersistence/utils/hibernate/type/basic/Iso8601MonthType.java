package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.basic.internal.Iso8601MonthTypeDescriptor;
import io.hypersistence.utils.hibernate.type.basic.internal.NumberSqlTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.usertype.DynamicParameterizedType;

import java.time.Month;
import java.util.Properties;

/**
 * Maps a {@link Month} object type to a {@code INT}  column type
 * which is saved as value from 1 (January) to 12 (December),
 * according to the ISO 8601 standard.
 *
 * @author Martin Panzer
 */
public class Iso8601MonthType extends AbstractHibernateType<Month> implements DynamicParameterizedType {

    public static final Iso8601MonthType INSTANCE = new Iso8601MonthType();

    public Iso8601MonthType() {
        super(
            NumberSqlTypeDescriptor.INSTANCE,
            Iso8601MonthTypeDescriptor.INSTANCE
        );
    }

    public Iso8601MonthType(Configuration configuration) {
        super(
            NumberSqlTypeDescriptor.INSTANCE,
            Iso8601MonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public Iso8601MonthType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public String getName() {
        return "month";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((NumberSqlTypeDescriptor) getSqlTypeDescriptor()).setParameterValues(parameters);
    }
}
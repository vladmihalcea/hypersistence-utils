package com.vladmihalcea.hibernate.type.array.internal;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Base class for all ARRAY types.
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractArrayType<T>
        extends AbstractHibernateType<T>
        implements DynamicParameterizedType {

    public static final String SQL_ARRAY_TYPE = "sql_array_type";

    public AbstractArrayType(AbstractArrayTypeDescriptor<T> arrayTypeDescriptor) {
        super(
            ArraySqlTypeDescriptor.INSTANCE,
            arrayTypeDescriptor
        );
    }

    public AbstractArrayType(AbstractArrayTypeDescriptor<T> arrayTypeDescriptor, Configuration configuration) {
        super(
            ArraySqlTypeDescriptor.INSTANCE,
            arrayTypeDescriptor,
            configuration
        );
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((AbstractArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}
package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.EnhancedUserType;

/**
 * @author Vlad Mihalcea
 */
public abstract class ImmutableDynamicParameterizedType<T> extends ImmutableType<T> implements DynamicParameterizedType, EnhancedUserType<T> {

    /**
     * {@inheritDoc}
     */
    public ImmutableDynamicParameterizedType(Class<T> clazz) {
        super(clazz);
    }

    /**
     * {@inheritDoc}
     */
    public ImmutableDynamicParameterizedType(Class<T> clazz, Configuration configuration) {
        super(clazz, configuration);
    }

    @Override
    public String toSqlLiteral(T o) {
        return toString(o);
    }

    @Override
    public String toString(T o) throws HibernateException {
        return o != null ? o.toString() : null;
    }
}
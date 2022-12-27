package com.vladmihalcea.hibernate.type;

import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.usertype.DynamicParameterizedType;

/**
 * @author Vlad Mihalcea
 */
public abstract class ImmutableDynamicParameterizedType<T> extends ImmutableType<T> implements DynamicParameterizedType {

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
}
package com.vladmihalcea.hibernate.type.list.internal;

import java.sql.Array;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Properties;
import org.hibernate.internal.SessionImpl;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Daniel Hoffmann
 */
public class ListTypeDescriptor<T> extends AbstractTypeDescriptor<Object> implements DynamicParameterizedType {

    private static final Logger LOGGER = LoggerFactory.getLogger(ListTypeDescriptor.class);

    private final String typeName;
    private final ListWrapper<T> listWrapper;

    public ListTypeDescriptor(String typeName, ListWrapper<T> listWrapper) {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            @SuppressWarnings({"unchecked"})
            protected Object deepCopyNotNull(Object value) {
                return new ArrayList<T>((Collection<T>) value);
            }
        });
        this.typeName = typeName;
        this.listWrapper = listWrapper;
    }

    @Override
    public boolean areEqual(Object one, Object another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return Objects.equals(one, another);
    }

    @Override
    public Object fromString(String string) {
        throw new UnsupportedOperationException("Mapping from string is not supported");
    }

    @Override
    public String toString(Object value) {
        throw new UnsupportedOperationException("Mapping to string is not supported");
    }

    @Override
    public <X> Object wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }

        if (Array.class.isAssignableFrom(value.getClass())) {
            try {
                return listWrapper.wrap((Array) value);
            } catch (SQLException e) {
                LOGGER.error("Failed to wrap array", e);
            }
        }
        throw unknownWrap(value.getClass());
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <X> X unwrap(Object value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (Array.class.isAssignableFrom(type)) {
            try {
                return (X) ((SessionImpl) options).connection()
                    .createArrayOf(typeName, ((Collection<T>) value).toArray());
            } catch (SQLException e) {
                LOGGER.error("Failed to unwrap array", e);
            }
        }

        throw unknownUnwrap(type);
    }

    @Override
    public void setParameterValues(Properties properties) {

    }

    public interface ListWrapper<T> {
        List<T> wrap(Array array) throws SQLException;
    }
}

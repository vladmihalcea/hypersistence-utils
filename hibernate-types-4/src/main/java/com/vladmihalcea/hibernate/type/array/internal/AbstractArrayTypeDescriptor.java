package com.vladmihalcea.hibernate.type.array.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutabilityPlan;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public abstract class AbstractArrayTypeDescriptor<T>
        extends AbstractTypeDescriptor<T> implements DynamicParameterizedType {

    private Class<T> arrayObjectClass;

    public AbstractArrayTypeDescriptor(Class<T> arrayObjectClass) {
        this(arrayObjectClass, (MutabilityPlan<T>) new MutableMutabilityPlan<Object>() {
            @Override
            protected T deepCopyNotNull(Object value) {
                return ArrayUtil.deepCopy(value);
            }
        });
    }

    protected AbstractArrayTypeDescriptor(Class<T> arrayObjectClass, MutabilityPlan<T> mutableMutabilityPlan) {
        super(arrayObjectClass, mutableMutabilityPlan);
        this.arrayObjectClass = arrayObjectClass;
    }

    protected Class<T> getArrayObjectClass() {
        return arrayObjectClass;
    }

    public void setArrayObjectClass(Class<T> arrayObjectClass) {
        this.arrayObjectClass = arrayObjectClass;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        if (parameters.containsKey(PARAMETER_TYPE)) {
            arrayObjectClass = ((ParameterType) parameters.get(PARAMETER_TYPE)).getReturnedClass();
        }
    }

    @Override
    public boolean areEqual(Object one, Object another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return ArrayUtil.isEquals(one, another);
    }

    @Override
    public String toString(Object value) {
        return Arrays.deepToString((Object[]) value);
    }

    @Override
    public T fromString(String string) {
        return ArrayUtil.fromString(string, arrayObjectClass);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(T value, Class<X> type, WrapperOptions options) {
        return (X) ArrayUtil.wrapArray(value);
    }

    @Override
    public <X> T wrap(X value, WrapperOptions options) {
        if (value instanceof Array) {
            Array array = (Array) value;
            try {
                return ArrayUtil.unwrapArray((Object[]) array.getArray(), arrayObjectClass);
            } catch (SQLException e) {
                throw new IllegalArgumentException(e);
            }
        }
        return (T) value;
    }

    protected abstract String getSqlArrayType();

}

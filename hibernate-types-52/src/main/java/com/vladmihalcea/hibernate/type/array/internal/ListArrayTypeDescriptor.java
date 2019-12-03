package com.vladmihalcea.hibernate.type.array.internal;

import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.*;

/**
 * @author Vlad Mihalcea
 */
public class ListArrayTypeDescriptor extends AbstractArrayTypeDescriptor<Object> {

    private String sqlArrayType;

    public ListArrayTypeDescriptor() {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                if (value instanceof List) {
                    Object[] array = ((List) value).toArray();
                    return ArrayUtil.deepCopy(array);
                } else {
                    throw new UnsupportedOperationException("The provided " + value + " is not a List!");
                }
            }
        });
    }

    @Override
    protected String getSqlArrayType() {
        return sqlArrayType;
    }

    @Override
    public Object unwrap(Object value, Class type, WrapperOptions options) {
        if (value instanceof Object[]) {
            return value;
        } else if (value instanceof List) {
            return super.unwrap(((List) value).toArray(), type, options);
        } else {
            throw new UnsupportedOperationException("The provided " + value + " is not a Object[] or List!");
        }
    }

    @Override
    public Object wrap(Object value, WrapperOptions options) {
        Object wrappedObject = super.wrap(value, options);
        List list = new ArrayList<>();
        if (wrappedObject instanceof Object[]) {
            Object[] wrappedArray = (Object[]) wrappedObject;
            Collections.addAll(list, wrappedArray);
        } else {
            throw new UnsupportedOperationException("The wrapped object " + value + " is not an Object[]!");
        }
        return list;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        Class entityClass = ReflectionUtils.getClass(parameters.getProperty(DynamicParameterizedType.ENTITY));
        String property = parameters.getProperty(DynamicParameterizedType.PROPERTY);
        Type memberGenericType = ReflectionUtils.getMemberGenericTypeOrNull(entityClass, property);
        if (memberGenericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) memberGenericType;
            Class arrayElementClass = ReflectionUtils.getClass(parameterizedType.getActualTypeArguments()[0].getTypeName());
            setArrayObjectClass(
                arrayElementClass.isArray() ?
                    arrayElementClass :
                    ArrayUtil.toArrayClass(arrayElementClass)
            );
            sqlArrayType = parameters.getProperty(AbstractArrayType.SQL_ARRAY_TYPE);
            if (sqlArrayType == null) {
                if (Integer.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "integer";
                } else if (Long.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "bigint";
                } else if (String.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "text";
                } else if (UUID.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "uuid";
                } else if (Date.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "timestamp";
                } else {
                    throw new UnsupportedOperationException("The " + arrayElementClass + " is not supported yet!");
                }
            }

        } else {
            throw new UnsupportedOperationException("The property " + property + " in the " + entityClass + " entity is not parameterized!");
        }
    }
}

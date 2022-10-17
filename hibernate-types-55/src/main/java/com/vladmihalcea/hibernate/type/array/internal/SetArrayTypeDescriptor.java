package com.vladmihalcea.hibernate.type.array.internal;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.ArrayUtil;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * @author Aleksey2093
 */
public class SetArrayTypeDescriptor extends AbstractArrayTypeDescriptor<Object> {

    private String sqlArrayType;

    public SetArrayTypeDescriptor() {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                if (value instanceof Set) {
                    Object[] array = ((Set) value).toArray();
                    Set set = new LinkedHashSet();
                    Collections.addAll(set, ArrayUtil.deepCopy(array));
                    return set;
                } else if (value.getClass().isArray()) {
                    Object[] array = (Object[]) value;
                    return ArrayUtil.deepCopy(array);
                } else {
                    throw new UnsupportedOperationException("The provided " + value + " is not a Set!");
                }
            }

            @Override
            public Object assemble(Serializable cached) {
                if (cached != null && cached.getClass().isArray()) {
                    Object[] array = (Object[]) cached;
                    Set set = new LinkedHashSet();
                    Collections.addAll(set, array);
                    return set;
                }
                return super.assemble(cached);
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
        } else if (value instanceof Set) {
            return super.unwrap(((Set) value).toArray(), type, options);
        } else {
            throw new UnsupportedOperationException("The provided " + value + " is not a Object[] or Set!");
        }
    }

    @Override
    public Object wrap(Object value, WrapperOptions options) {
        Object wrappedObject = super.wrap(value, options);
        Set set = null;
        if (wrappedObject != null) {
            set = new LinkedHashSet();
            if (wrappedObject instanceof Object[]) {
                Object[] wrappedArray = (Object[]) wrappedObject;
                Collections.addAll(set, wrappedArray);
            } else {
                throw new UnsupportedOperationException("The wrapped object " + value + " is not an Object[]!");
            }
        }
        return set;
    }

    @Override
    public boolean areEqual(Object one, Object another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        if (one instanceof Set && another instanceof Set) {
            return ArrayUtil.isEquals(((Set) one).toArray(), ((Set) another).toArray());
        }
        if (one instanceof Object[] && another instanceof Object[]) {
            return ArrayUtil.isEquals(one, another);
        } else {
            throw new UnsupportedOperationException("The provided " + one + " and " + another + " are not Object[] or Set!");
        }
    }

    @Override
    public void setParameterValues(Properties parameters) {
        Class entityClass = ReflectionUtils.getClass(parameters.getProperty(DynamicParameterizedType.ENTITY));
        String property = parameters.getProperty(DynamicParameterizedType.PROPERTY);
        Type memberGenericType = ReflectionUtils.getMemberGenericTypeOrNull(entityClass, property);
        if (memberGenericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) memberGenericType;
            Type genericType = parameterizedType.getActualTypeArguments()[0];
            if (genericType instanceof WildcardType) {
                genericType = ((WildcardType) genericType).getUpperBounds()[0];
            }
            Class arrayElementClass = ReflectionUtils.getClass(genericType.getTypeName());
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
                } else if (Double.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "float8";
                } else if (String.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "text";
                } else if (UUID.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "uuid";
                } else if (Date.class.isAssignableFrom(arrayElementClass) || LocalDateTime.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "timestamp";
                } else if (Boolean.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "boolean";
                } else if (BigDecimal.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "decimal";
                } else if (LocalDate.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "date";
                } else {
                    throw new UnsupportedOperationException("The " + arrayElementClass + " is not supported yet!");
                }
            }

        } else {
            throw new UnsupportedOperationException("The property " + property + " in the " + entityClass + " entity is not parameterized!");
        }
    }
}

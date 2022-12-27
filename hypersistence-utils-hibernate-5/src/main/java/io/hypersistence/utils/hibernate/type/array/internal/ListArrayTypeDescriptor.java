package io.hypersistence.utils.hibernate.type.array.internal;

import io.hypersistence.utils.hibernate.util.ReflectionUtils;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.io.Serializable;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.util.*;

/**
 * @author Vlad Mihalcea
 */
public class ListArrayTypeDescriptor extends AbstractArrayTypeDescriptor<Object> {

    private String sqlArrayType;

    private Class entityClass;

    private String propertyName;

    private Class propertyClass;

    public ListArrayTypeDescriptor() {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                if (value instanceof Collection) {
                    Object[] array = ((Collection<Object>) value).toArray();
                    return ArrayUtil.asList((Object[]) ArrayUtil.deepCopy(array));
                } else if (value.getClass().isArray()) {
                    Object[] array = (Object[]) value;
                    return ArrayUtil.deepCopy(array);
                } else {
                    throw new UnsupportedOperationException("The provided " + value + " is not a List!");
                }
            }

            @Override
            public Object assemble(Serializable cached) {
                if (cached != null && cached.getClass().isArray()) {
                    Object[] array = (Object[]) cached;
                    return Arrays.asList(array);
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
        } else if (value instanceof Collection) {
            return super.unwrap(((Collection) value).toArray(), type, options);
        } else {
            throw new UnsupportedOperationException("The provided " + value + " is not a Object[] or List!");
        }
    }

    @Override
    public Object wrap(Object value, WrapperOptions options) {
        Object wrappedObject = super.wrap(value, options);
        Collection list = null;
        if (wrappedObject != null) {
            list = newPropertyCollectionInstance();
            if (wrappedObject instanceof Object[]) {
                Object[] wrappedArray = (Object[]) wrappedObject;
                Collections.addAll(list, wrappedArray);
            } else {
                throw new UnsupportedOperationException("The wrapped object " + value + " is not an Object[]!");
            }
        }
        return list;
    }

    @Override
    public boolean areEqual(Object one, Object another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        if (one instanceof Collection && another instanceof Collection) {
            return ArrayUtil.isEquals(((Collection) one).toArray(), ((Collection) another).toArray());
        }
        if (one instanceof Object[] && another instanceof Object[]) {
            return ArrayUtil.isEquals(one, another);
        } else {
            throw new UnsupportedOperationException("The provided " + one + " and " + another + " are not Object[] or List!");
        }
    }

    @Override
    public void setParameterValues(Properties parameters) {
        this.entityClass = ReflectionUtils.getClass(parameters.getProperty(DynamicParameterizedType.ENTITY));
        this.propertyName = parameters.getProperty(DynamicParameterizedType.PROPERTY);
        this.propertyClass = ReflectionUtils.getClass(parameters.getProperty(DynamicParameterizedType.RETURNED_CLASS));
        Type memberGenericType = ReflectionUtils.getMemberGenericTypeOrNull(entityClass, propertyName);
        if (memberGenericType instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) memberGenericType;

            Type arrayElementType = parameterizedType.getActualTypeArguments()[0];
            String arrayElementClassName = ReflectionUtils.getFieldValue(arrayElementType, "name");
            if (arrayElementClassName == null) {
                arrayElementClassName = arrayElementType.toString().replaceAll("class ", "");
            }

            Class arrayElementClass = ReflectionUtils.getClass(arrayElementClassName);
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
                } else if (Date.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "timestamp";
                } else if (Boolean.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "boolean";
                } else if (BigDecimal.class.isAssignableFrom(arrayElementClass)) {
                    sqlArrayType = "decimal";
                } else {
                    throw new UnsupportedOperationException("The " + arrayElementClass + " is not supported yet!");
                }
            }
        } else {
            throw new UnsupportedOperationException("The property " + propertyName + " in the " + entityClass + " entity is not parameterized!");
        }
    }

    private Collection newPropertyCollectionInstance() {
        if(List.class.isAssignableFrom(propertyClass)) {
            return new ArrayList();
        } else if(Set.class.isAssignableFrom(propertyClass)) {
            return new LinkedHashSet();
        }
        throw new UnsupportedOperationException("The property " + propertyName + " in the " + entityClass + " entity is not supported by the ListArrayType!");
    }
}

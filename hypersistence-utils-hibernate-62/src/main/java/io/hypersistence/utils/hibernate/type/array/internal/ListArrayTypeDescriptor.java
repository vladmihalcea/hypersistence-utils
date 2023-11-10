package io.hypersistence.utils.hibernate.type.array.internal;

import io.hypersistence.utils.common.ReflectionUtils;
import org.hibernate.SharedSessionContract;
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
 * @author Vlad Mihalcea
 */
public class ListArrayTypeDescriptor extends AbstractArrayTypeDescriptor<Collection> {

    private String sqlArrayType;

    private Class entityClass;

    private String propertyName;

    private Class propertyClass;

    public ListArrayTypeDescriptor() {
        super(Collection.class, new MutableMutabilityPlan<>() {
            @Override
            protected Collection deepCopyNotNull(Collection value) {
                Object[] array = ((Collection<Object>) value).toArray();
                return ArrayUtil.asList(ArrayUtil.deepCopy(array));
            }

            @Override
            public Collection assemble(Serializable cached, SharedSessionContract session) {
                if (cached != null && cached.getClass().isArray()) {
                    Object[] array = (Object[]) cached;
                    return Arrays.asList(array);
                }
                return super.assemble(cached, session);
            }
        });
    }

    @Override
    protected String getSqlArrayType() {
        return sqlArrayType;
    }

    @Override
    public Object unwrap(Collection value, Class type, WrapperOptions options) {
        return super.unwrap(value, type, options);
    }

    @Override
    public Collection wrap(Object value, WrapperOptions options) {
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
    public boolean areEqual(Collection one, Collection another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return ArrayUtil.isEquals(one.toArray(), another.toArray());
    }

    @Override
    public void setParameterValues(Properties parameters) {
        this.entityClass = ReflectionUtils.getClass(parameters.getProperty(DynamicParameterizedType.ENTITY));
        this.propertyName = parameters.getProperty(DynamicParameterizedType.PROPERTY);
        this.propertyClass = ReflectionUtils.getClass(parameters.getProperty(DynamicParameterizedType.RETURNED_CLASS));
        Type memberGenericType = ReflectionUtils.getMemberGenericTypeOrNull(entityClass, propertyName);
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
            throw new UnsupportedOperationException("The property " + propertyName + " in the " + entityClass + " entity is not parameterized!");
        }
        }

    private Collection newPropertyCollectionInstance() {
        if (propertyClass == null || List.class.isAssignableFrom(propertyClass)) {
            return new ArrayList();
        } else if(Set.class.isAssignableFrom(propertyClass)) {
            return new LinkedHashSet();
        }
        throw new UnsupportedOperationException("The property " + propertyName + " in the " + entityClass + " entity is not supported by the ListArrayType!");
    }
}

package com.vladmihalcea.hibernate.type.json.internal;

import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Objects;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class JsonTypeDescriptor
        extends AbstractTypeDescriptor<Object> implements DynamicParameterizedType {

    private Type type;

    private ObjectMapperWrapper objectMapperWrapper;

    public JsonTypeDescriptor() {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return ObjectMapperWrapper.INSTANCE.clone(value);
            }
        });
    }

    public JsonTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper) {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return objectMapperWrapper.clone(value);
            }
        });
        this.objectMapperWrapper = objectMapperWrapper;
    }

    public JsonTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper, Type type) {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return objectMapperWrapper.clone(value);
            }
        });
        this.objectMapperWrapper = objectMapperWrapper;
        this.type = type;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        final XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
        if (xProperty instanceof JavaXMember) {
            type = ReflectionUtils.invokeGetter(xProperty, "javaType");
        } else {
            type = ((ParameterType) parameters.get(PARAMETER_TYPE)).getReturnedClass();
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
        if (one instanceof String && another instanceof String) {
            return one.equals(another);
        }
        if (one instanceof Collection && another instanceof Collection) {
            return Objects.equals(one, another);
        }
        return objectMapperWrapper.toJsonNode(objectMapperWrapper.toString(one)).equals(
                objectMapperWrapper.toJsonNode(objectMapperWrapper.toString(another)));
    }

    @Override
    public String toString(Object value) {
        return objectMapperWrapper.toString(value);
    }

    @Override
    public Object fromString(String string) {
        if (String.class.isAssignableFrom(typeToClass())) {
            return string;
        }
        return objectMapperWrapper.fromString(string, type);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(Object value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (X) toString(value);
        }
        if (Object.class.isAssignableFrom(type)) {
            String stringValue = (value instanceof String) ? (String) value : toString(value);
            return (X) objectMapperWrapper.toJsonNode(stringValue);
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> Object wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        return fromString(value.toString());
    }

    private Class typeToClass() {
        Type classType = type;
        if(type instanceof ParameterizedType) {
            classType = ((ParameterizedType) type).getRawType();
        }
        return (Class) classType;
    }
}

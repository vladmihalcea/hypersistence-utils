package com.vladmihalcea.hibernate.type.tsvector.dynamicparameters.internal;

import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.usertype.DynamicParameterizedType;
import org.postgresql.util.PGobject;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Properties;

/**
 * @author Lukman Adekunle
 */
public class XVectorTypeDescriptor extends AbstractTypeDescriptor<Object> implements DynamicParameterizedType {

    public static final XVectorTypeDescriptor INSTANCE = new XVectorTypeDescriptor();

    private Type type;

    public XVectorTypeDescriptor() {
        super(Object.class);
    }

    @Override
    public boolean areEqual(Object one, Object another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return one.equals(another);
    }

    @Override
    public String toString(Object value) {
        return value.toString();
    }

    @Override
    public Object fromString(String string) {
        if (String.class.isAssignableFrom(typeToClass())) {
            return string;
        }
        return string;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <X> X unwrap(Object value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (PGobject.class.isAssignableFrom(type) && value instanceof String) {
            Object pGObject = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
            ReflectionUtils.invokeSetter(pGObject, "type", "tsvector");
            ReflectionUtils.invokeSetter(pGObject, "value", value);
            return (X) pGObject;
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

    @Override
    public void setParameterValues(Properties parameters) {
        final XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
        if (xProperty instanceof JavaXMember) {
            type = ReflectionUtils.invokeGetter(xProperty, "javaType");
        } else {
            type = ((ParameterType) parameters.get(PARAMETER_TYPE)).getReturnedClass();
        }
    }

    private Class typeToClass() {
        Type classType = type;
        if (type instanceof ParameterizedType) {
            classType = ((ParameterizedType) type).getRawType();
        }
        return (Class) classType;
    }
}

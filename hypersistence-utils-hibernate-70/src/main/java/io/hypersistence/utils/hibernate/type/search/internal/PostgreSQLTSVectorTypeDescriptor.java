package io.hypersistence.utils.hibernate.type.search.internal;

import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.Properties;

public class PostgreSQLTSVectorTypeDescriptor extends AbstractClassJavaType<Object> implements DynamicParameterizedType {

    public PostgreSQLTSVectorTypeDescriptor() {
        super(Object.class);
    }

    private Type type;

    @Override
    public void setParameterValues(Properties parameters) {
        final XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
        if (xProperty instanceof JavaXMember) {
            type = ((JavaXMember) xProperty).getJavaType();
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
        return one.equals(another);
    }

    @Override
    public String toString(Object value) {
        return value.toString();
    }

    @Override
    public Object fromString(CharSequence string) {
        if (String.class.isAssignableFrom(typeToClass())) {
            return string;
        }
        return string;
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
        if (type instanceof ParameterizedType) {
            classType = ((ParameterizedType) type).getRawType();
        } else if (type instanceof TypeVariable) {
            classType = ((TypeVariable) type).getGenericDeclaration().getClass();
        }
        return (Class) classType;
    }
}

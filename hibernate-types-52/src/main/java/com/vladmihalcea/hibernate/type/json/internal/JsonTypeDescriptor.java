package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.annotations.common.reflection.XProperty;
import org.hibernate.annotations.common.reflection.java.JavaXMember;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class JsonTypeDescriptor
        extends AbstractTypeDescriptor<Object> implements DynamicParameterizedType {

    private static final Method getJavaTypeMethod;
    static {
        try {
            getJavaTypeMethod = JavaXMember.class.getDeclaredMethod("getJavaType");
            getJavaTypeMethod.setAccessible(true);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Type type;

    public JsonTypeDescriptor() {
        super(Object.class, new MutableMutabilityPlan<Object>() {
            @Override
            protected Object deepCopyNotNull(Object value) {
                return JacksonUtil.clone(value);
            }
        });
    }

    @Override
    public void setParameterValues(Properties parameters) {
        	final XProperty xProperty = (XProperty) parameters.get(DynamicParameterizedType.XPROPERTY);
        	if (xProperty instanceof JavaXMember) {
            try {
                type = (Type) getJavaTypeMethod.invoke(xProperty);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
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
        return JacksonUtil.toJsonNode(JacksonUtil.toString(one)).equals(
                JacksonUtil.toJsonNode(JacksonUtil.toString(another)));
    }

    @Override
    public String toString(Object value) {
        return JacksonUtil.toString(value);
    }

    @Override
    public Object fromString(String string) {
        return JacksonUtil.fromString(string, type);
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
            return (X) JacksonUtil.toJsonNode(toString(value));
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

}

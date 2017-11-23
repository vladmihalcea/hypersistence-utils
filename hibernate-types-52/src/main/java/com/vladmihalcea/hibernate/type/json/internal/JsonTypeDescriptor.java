package com.vladmihalcea.hibernate.type.json.internal;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vladmihalcea.hibernate.type.json.TypeReferenceFactory;
import org.hibernate.HibernateException;
import org.hibernate.internal.util.ReflectHelper;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

import static com.vladmihalcea.hibernate.type.json.TypeReferenceFactory.FACTORY_CLASS;

/**
 * @author Vlad Mihalcea
 */
public class JsonTypeDescriptor
        extends AbstractTypeDescriptor<Object> implements DynamicParameterizedType {

    private Class<?> jsonObjectClass;
    private JsonSerializer<?> jsonSerializer;

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
        jsonObjectClass = ((ParameterType) parameters.get(PARAMETER_TYPE)).getReturnedClass();
        final String typeRef = parameters.getProperty(FACTORY_CLASS);
        if (typeRef == null) {
            jsonSerializer = new ClassJsonSerializer<>(jsonObjectClass);
        } else {
            try {
                final TypeReferenceFactory factory = (TypeReferenceFactory) ReflectHelper.classForName(typeRef, getClass()).newInstance();
                jsonSerializer = new TypeReferenceJsonSerializer<>(factory.newTypeReference());
            } catch (InstantiationException | IllegalAccessException | ClassNotFoundException e) {
                throw new HibernateException("Cannot generate TypeReferenceFactory class " + typeRef, e);
            }
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
        return JacksonUtil.toJsonNode(JacksonUtil.toString(one)).equals(
                JacksonUtil.toJsonNode(JacksonUtil.toString(another)));
    }

    @Override
    public String toString(Object value) {
        return JacksonUtil.toString(value);
    }

    @Override
    public Object fromString(String string) {
        return jsonSerializer.fromString(string);
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

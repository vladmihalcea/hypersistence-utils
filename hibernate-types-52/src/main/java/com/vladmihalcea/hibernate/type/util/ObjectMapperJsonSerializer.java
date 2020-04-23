package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.hibernate.internal.util.SerializationHelper;

import java.io.Serializable;
import java.util.Collection;

/**
 * @author Vlad Mihalcea
 */
public class ObjectMapperJsonSerializer implements JsonSerializer {

    private final ObjectMapperWrapper objectMapperWrapper;

    public ObjectMapperJsonSerializer(ObjectMapperWrapper objectMapperWrapper) {
        this.objectMapperWrapper = objectMapperWrapper;
    }

    @Override
    public <T> T clone(T value) {
        if (value instanceof Collection && !((Collection) value).isEmpty()) {
            Object firstElement = ((Collection) value).iterator().next();
            if (!(firstElement instanceof Serializable)) {
                JavaType type = TypeFactory.defaultInstance().constructParametricType(value.getClass(), firstElement.getClass());
                return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(value), type);
            }
        }

        if (value instanceof Map && !((Map) value).isEmpty()) {
            Map.Entry firstElement = (Map.Entry) ((Map) value).entrySet().iterator().next();
            if (!(firstElement.getKey() instanceof Serializable)
                    || !(firstElement.getValue() instanceof Serializable)) {
                return (T) objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(value), (Class<T>) value.getClass());
            }
        }

        return value instanceof Serializable ?
                (T) SerializationHelper.clone((Serializable) value) :
                objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(value), (Class<T>) value.getClass());
    }
}

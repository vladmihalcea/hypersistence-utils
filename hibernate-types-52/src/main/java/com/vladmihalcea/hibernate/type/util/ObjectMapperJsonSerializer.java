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
                return objectMapperWrapper.fromString(objectMapperWrapper.toString(value), type);
            }
        }

        return value instanceof Serializable ?
                (T) SerializationHelper.clone((Serializable) value) :
                objectMapperWrapper.fromString(objectMapperWrapper.toString(value), (Class<T>) value.getClass());
    }
}

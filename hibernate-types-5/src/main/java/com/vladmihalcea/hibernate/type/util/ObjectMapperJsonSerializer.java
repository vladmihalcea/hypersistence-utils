package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.hibernate.internal.util.SerializationHelper;

import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

/**
 * @author Vlad Mihalcea
 */
public class ObjectMapperJsonSerializer implements JsonSerializer {

    private final ObjectMapperWrapper objectMapperWrapper;

    public ObjectMapperJsonSerializer(ObjectMapperWrapper objectMapperWrapper) {
        this.objectMapperWrapper = objectMapperWrapper;
    }

    @Override
    public <T> T clone(T object) {
        if (object instanceof Collection) {
            Collection collection = (Collection) object;
            if (!collection.isEmpty()) {
                Object firstElement = collection.iterator().next();
                if (!(firstElement instanceof Serializable)) {
                    JavaType type = TypeFactory.defaultInstance().constructParametricType(collection.getClass(), firstElement.getClass());
                    return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(collection), type);
                }
            }
        }

        if (object instanceof Map) {
            Map map = (Map) object;
            if (!map.isEmpty()) {
                Map.Entry firstElement = (Map.Entry) map.entrySet().iterator().next();
                Object key = firstElement.getKey();
                Object value = firstElement.getValue();
                if (!(key instanceof Serializable) || !(value instanceof Serializable)) {
                    JavaType type = TypeFactory.defaultInstance().constructParametricType(map.getClass(), key.getClass(), value.getClass());
                    return (T) objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(map), type);
                }
            }
        }

        return object instanceof Serializable ?
            (T) SerializationHelper.clone((Serializable) object) :
            objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), (Class<T>) object.getClass());
    }
}

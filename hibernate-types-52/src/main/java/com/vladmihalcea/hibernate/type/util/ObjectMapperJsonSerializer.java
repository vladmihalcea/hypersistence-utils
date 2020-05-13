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
            Object firstElement = findFirstNonNullElement((Collection) object);
            if (firstElement != null && !(firstElement instanceof Serializable)) {
                JavaType type = TypeFactory.defaultInstance().constructParametricType(object.getClass(), firstElement.getClass());
                return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), type);
            }
        }

        if (object instanceof Map) {
            Map.Entry firstEntry = this.findFirstNonNullEntry((Map) object);
            if (firstEntry != null) {
                Object key = firstEntry.getKey();
                Object value = firstEntry.getValue();
                if (!(key instanceof Serializable) || !(value instanceof Serializable)) {
                    JavaType type = TypeFactory.defaultInstance().constructParametricType(object.getClass(), key.getClass(), value.getClass());
                    return (T) objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), type);
                }
            }
        }

        return object instanceof Serializable ?
            (T) SerializationHelper.clone((Serializable) object) :
            objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), (Class<T>) object.getClass());
    }

    private Object findFirstNonNullElement(Collection collection) {
        for (java.lang.Object element : collection) {
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private Map.Entry findFirstNonNullEntry(Map<?, ?> map) {
        for (Map.Entry entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                return entry;
            }
        }
        return null;
    }
}
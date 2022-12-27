package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.type.SerializationException;

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
        if (object instanceof String) {
            return object;
        } else if (object instanceof Collection) {
            Object firstElement = findFirstNonNullElement((Collection) object);
            if (firstElement != null && !(firstElement instanceof Serializable)) {
                JavaType type = TypeFactory.defaultInstance().constructParametricType(object.getClass(), firstElement.getClass());
                return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), type);
            }
        } else if (object instanceof Map) {
            Map.Entry firstEntry = this.findFirstNonNullEntry((Map) object);
            if (firstEntry != null) {
                Object key = firstEntry.getKey();
                Object value = firstEntry.getValue();
                if (!(key instanceof Serializable) || !(value instanceof Serializable)) {
                    JavaType type = TypeFactory.defaultInstance().constructParametricType(object.getClass(), key.getClass(), value.getClass());
                    return (T) objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), type);
                }
            }
        } else if (object instanceof JsonNode) {
            return (T) ((JsonNode) object).deepCopy();
        }

        if (object instanceof Serializable) {
            try {
                return (T) SerializationHelper.clone((Serializable) object);
            } catch (SerializationException e) {
                //it is possible that object itself implements java.io.Serializable, but underlying structure does not
                //in this case we switch to the other JSON marshaling strategy which doesn't use the Java serialization
            }
        }

        return jsonClone(object);
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

    private <T> T jsonClone(T object) {
        return objectMapperWrapper.fromBytes(objectMapperWrapper.toBytes(object), (Class<T>) object.getClass());
    }
}
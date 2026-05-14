package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.internal.util.SerializationHelper;

import java.io.Serializable;
import java.util.Optional;

/**
 * @author Vlad Mihalcea
 */
public class ObjectMapperJsonSerializer implements JsonSerializer {

    @Override
    public <T> T clone(T object) {
        if (object instanceof String) {
            return object;
        }
        if (object instanceof JsonNode) {
            return (T) ((JsonNode) object).deepCopy();
        }
        if (object instanceof Optional) {
            Optional<?> optional = (Optional<?>) object;
            return (T) optional.map(this::clone);
        }
        try {
            if (object instanceof Serializable) {
                return (T) SerializationHelper.clone((Serializable) object);
            } else {
                throw new NonSerializableObjectException(object);
            }
        } catch (Exception e) {
            throw new NonSerializableObjectException(object);
        }
    }
}
package io.hypersistence.utils.hibernate.type.util;

import org.hibernate.internal.util.SerializationHelper;
import tools.jackson.databind.JsonNode;

import java.io.Serializable;

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
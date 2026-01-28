package io.hypersistence.utils.hibernate.type.util;

import org.hibernate.internal.util.SerializationHelper;
import org.hibernate.type.SerializationException;
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
            return (T) SerializationHelper.clone((Serializable) object);
        } catch (SerializationException e) {
            String message = """
                The JPA specification requires that the entity attributes are Serializable.\
                The default JsonSerializer does not support JSON object cloning (other than the JsonNode attribute type) because this is a very inefficient operation.\
                If you want to use JSON object cloning, then you can provide your own custom JsonSerializer.\
                Offending type : %s""".formatted(object.getClass().getName());
            throw new IllegalArgumentException(message, e);
        }
    }
}
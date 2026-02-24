package io.hypersistence.utils.hibernate.type.util;

import org.checkerframework.checker.nullness.qual.Nullable;
import org.hibernate.HibernateException;

/**
 * The {@link NonSerializableObjectException} is thrown when the framework wants to serialize a given Object, but the Object itself or its inner children are not Serializable.
 *
 * @author Vlad Mihalcea
 * @since 3.15.2
 */
public class NonSerializableObjectException extends HibernateException {

    private static final String SERIALIZATION_FAILURE = "The JPA specification requires that the entity attributes are Serializable and the [%s] Object (or its inner child Objects) is not Serializable. The default JsonSerializer does not support JSON object cloning (other than the JsonNode attribute type) because this is a very inefficient operation. If you want to use JSON object cloning, then you can provide your own custom JsonSerializer.";

    private Object object;

    public NonSerializableObjectException(Object object) {
        super(String.format(SERIALIZATION_FAILURE, object));
    }

    public NonSerializableObjectException(Object object, @Nullable Throwable cause) {
        super(String.format(SERIALIZATION_FAILURE, object), cause);
    }

    public Object getObject() {
        return object;
    }
}

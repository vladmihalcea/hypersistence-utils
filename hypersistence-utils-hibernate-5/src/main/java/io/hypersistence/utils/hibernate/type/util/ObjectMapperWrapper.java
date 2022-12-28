package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.HibernateException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;

/**
 * Wraps a Jackson {@link ObjectMapper} so that you can supply your own {@link ObjectMapper} reference.
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public class ObjectMapperWrapper implements Serializable {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper()
        .findAndRegisterModules();

    public static final ObjectMapperWrapper INSTANCE = new ObjectMapperWrapper();

    private ObjectMapper objectMapper;

    private ObjectMapperSupplier objectMapperSupplier;

    private JsonSerializer jsonSerializer = new ObjectMapperJsonSerializer(this);

    public ObjectMapperWrapper() {
        this(OBJECT_MAPPER);
    }

    public ObjectMapperWrapper(ObjectMapperSupplier objectMapperSupplier) {
        this.objectMapperSupplier = objectMapperSupplier;
    }

    public ObjectMapperWrapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setJsonSerializer(JsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    public ObjectMapper getObjectMapper() {
        if(objectMapper == null && objectMapperSupplier != null) {
            objectMapper = objectMapperSupplier.get();
        }
        if(objectMapper == null) {
            throw new HibernateException("The provided ObjectMapper is null!");
        }
        return objectMapper;
    }

    public <T> T fromString(String string, Class<T> clazz) {
        try {
            return getObjectMapper().readValue(string, clazz);
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromString(String string, Type type) {
        try {
            return getObjectMapper().readValue(string, getObjectMapper().getTypeFactory().constructType(type));
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromBytes(byte[] value, Class<T> clazz) {
        try {
            return getObjectMapper().readValue(value, clazz);
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given byte array cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromBytes(byte[] value, Type type) {
        try {
            return getObjectMapper().readValue(value, getObjectMapper().getTypeFactory().constructType(type));
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given byte array cannot be transformed to Json object", e)
            );
        }
    }

    public String toString(Object value) {
        try {
            return getObjectMapper().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e)
            );
        }
    }

    public byte[] toBytes(Object value) {
        try {
            return getObjectMapper().writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a byte array", e)
            );
        }
    }

    public JsonNode toJsonNode(String value) {
        try {
            return getObjectMapper().readTree(value);
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException(e)
            );
        }
    }

    public <T> T clone(T value) {
        return jsonSerializer.clone(value);
    }
}

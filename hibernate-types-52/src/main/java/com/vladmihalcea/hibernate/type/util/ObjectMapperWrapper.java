package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.hibernate.HibernateException;

import java.io.IOException;
import java.lang.reflect.Type;

/**
 * Wraps a Jackson {@link ObjectMapper} so that you can supply your own {@link ObjectMapper} reference.
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public class ObjectMapperWrapper {

    public static final ObjectMapperWrapper INSTANCE = new ObjectMapperWrapper();

    private final ObjectMapper objectMapper;

    private JsonSerializer jsonSerializer;

    public ObjectMapperWrapper() {
        this(new ObjectMapper().findAndRegisterModules());
    }

    public ObjectMapperWrapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.jsonSerializer = new ObjectMapperJsonSerializer(this);
    }

    public void setJsonSerializer(JsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public <T> T fromString(String string, Type type) {
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(type);
            return objectMapper.readValue(string, javaType);
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromBytes(byte[] value, Type type) {
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(type);
            return objectMapper.readValue(value, javaType);
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given byte array cannot be transformed to Json object", e)
            );
        }
    }

    public String toString(Object value, Type type) {
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(type);
            return objectMapper.writerFor(javaType).writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e)
            );
        }
    }

    public String toString(JsonNode value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e)
            );
        }
    }

    public byte[] toBytes(Object value, Type type) {
        try {
            JavaType javaType = objectMapper.getTypeFactory().constructType(type);
            return objectMapper.writerFor(javaType).writeValueAsBytes(value);
        } catch (JsonProcessingException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a byte array", e)
            );
        }
    }

    public JsonNode toJsonNode(String value) {
        try {
            return objectMapper.readTree(value);
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

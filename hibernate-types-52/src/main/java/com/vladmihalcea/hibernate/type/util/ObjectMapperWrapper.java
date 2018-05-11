package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

    private JsonSerializer jsonSerializer = new ObjectMapperJsonSerializer(this);

    public ObjectMapperWrapper() {
        this.objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    public ObjectMapperWrapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setJsonSerializer(JsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    public ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public <T> T fromString(String string, Class<T> clazz) {
        try {
            return objectMapper.readValue(string, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e);
        }
    }

    public <T> T fromString(String string, Type type) {
        try {
            return objectMapper.readValue(string, objectMapper.getTypeFactory().constructType(type));
        } catch (IOException e) {
            throw new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e);
        }
    }

    public String toString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e);
        }
    }

    public JsonNode toJsonNode(String value) {
        try {
            return objectMapper.readTree(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public <T> T clone(T value) {
        return jsonSerializer.clone(value);
    }
}

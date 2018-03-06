package com.vladmihalcea.hibernate.type.json.internal;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;

import org.hibernate.internal.util.SerializationHelper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Vlad Mihalcea
 */
public class JacksonUtil {

    public static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();

    public static <T> T fromString(String string, Class<T> clazz) {
        return fromString(OBJECT_MAPPER, string, clazz);
    }

    public static <T> T fromString(ObjectMapper mapper, String string, Class<T> clazz) {
        try {
            return mapper.readValue(string, clazz);
        } catch (IOException e) {
            throw new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e);
        }
    }

    public static <T> T fromString(String string, Type type) {
        return fromString(OBJECT_MAPPER, string, type);
    }

    public static <T> T fromString(ObjectMapper mapper, String string, Type type) {
        try {
            return mapper.readValue(string, OBJECT_MAPPER.getTypeFactory().constructType(type));
        } catch (IOException e) {
            throw new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e);
        }
    }

    public static String toString(Object value) {
        return toString(OBJECT_MAPPER, value);
    }

    public static String toString(ObjectMapper mapper, Object value) {
        try {
            return mapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e);
        }
    }

    public static JsonNode toJsonNode(String value) {
        return toJsonNode(OBJECT_MAPPER, value);
    }

    public static JsonNode toJsonNode(ObjectMapper mapper, String value) {
        try {
            return mapper.readTree(value);
        } catch (IOException e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static <T> T clone(T value) {
        return clone(OBJECT_MAPPER, value);
    }

    @SuppressWarnings("unchecked")
    public static <T> T clone(ObjectMapper mapper, T value) {
        return (value instanceof Serializable)
             ? (T) SerializationHelper.clone((Serializable) value)
             : fromString(mapper, toString(mapper, value), (Class<T>) value.getClass());
    }
}

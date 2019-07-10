package com.vladmihalcea.hibernate.type.util;

import javax.json.Json;
import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbException;
import java.io.StringReader;
import java.lang.reflect.Type;

/**
 * Wraps a Jackson {@link Jsonb} so that you can supply your own {@link Jsonb} reference.
 *
 * @author Vlad Mihalcea
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonbWrapper {

    public static final JsonbWrapper INSTANCE = new JsonbWrapper();

    private final Jsonb objectMapper;

    private JsonSerializer jsonSerializer = new JsonbJsonSerializer(this);

    public JsonbWrapper() {
        this.objectMapper = JsonbBuilder.create();
    }

    public JsonbWrapper(Jsonb objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setJsonSerializer(JsonSerializer jsonSerializer) {
        this.jsonSerializer = jsonSerializer;
    }

    public Jsonb getObjectMapper() {
        return objectMapper;
    }

    public <T> T fromString(String string, Class<T> clazz) {
        try {
            return objectMapper.fromJson(string, clazz);
        } catch (JsonbException e) {
            throw new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e);
        }
    }

    public <T> T fromString(String string, Type type) {
        try {
            return objectMapper.fromJson(string, type);
        } catch (JsonbException e) {
            throw new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e);
        }
    }

    public String toString(Object value) {
        try {
            return objectMapper.toJson(value);
        } catch (JsonbException e) {
            throw new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e);
        }
    }

    public JsonValue toJsonNode(String value) {
        try {
            return Json.createReader(new StringReader(value)).readValue();
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public <T> T clone(T value) {
        return jsonSerializer.clone(value);
    }
}

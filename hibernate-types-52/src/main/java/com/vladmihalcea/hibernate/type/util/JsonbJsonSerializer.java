package com.vladmihalcea.hibernate.type.util;

import org.hibernate.internal.util.SerializationHelper;

import java.io.Serializable;

/**
 * @author Vlad Mihalcea
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonbJsonSerializer implements JsonSerializer {

    private final JsonbWrapper jsonbWrapper;

    public JsonbJsonSerializer(JsonbWrapper jsonbWrapper) {
        this.jsonbWrapper = jsonbWrapper;
    }

    @Override
    public <T> T clone(T value) {
        return (value instanceof Serializable) ?
                (T) SerializationHelper.clone((Serializable) value) :
                jsonbWrapper.fromString(
                    jsonbWrapper.toString(value), (Class<T>) value.getClass()
                );
    }
}

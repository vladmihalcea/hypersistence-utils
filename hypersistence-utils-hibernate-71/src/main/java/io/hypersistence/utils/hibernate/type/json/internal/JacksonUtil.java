package io.hypersistence.utils.hibernate.type.json.internal;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;

import java.lang.reflect.Type;

/**
 * @author Vlad Mihalcea
 */
public class JacksonUtil {

    public static <T> T fromString(String string, Class<T> clazz) {
        return ObjectMapperWrapper.INSTANCE.fromString(string, clazz);
    }

    public static <T> T fromString(String string, Type type) {
        return ObjectMapperWrapper.INSTANCE.fromString(string, type);
    }

    public static String toString(Object value) {
        return ObjectMapperWrapper.INSTANCE.toString(value);
    }

    public static JsonNode toJsonNode(String value) {
        return ObjectMapperWrapper.INSTANCE.toJsonNode(value);
    }

    public static <T> T clone(T value) {
        return ObjectMapperWrapper.INSTANCE.clone(value);
    }
}

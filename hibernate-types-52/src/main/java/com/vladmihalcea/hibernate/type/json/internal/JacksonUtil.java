package com.vladmihalcea.hibernate.type.json.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.JsonSerializer;
import com.vladmihalcea.hibernate.type.util.JsonSerializerSupplier;
import com.vladmihalcea.hibernate.type.util.ObjectMapperSupplier;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;

import java.lang.reflect.Type;
import java.util.function.Supplier;

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

    /**
     * Get {@link ObjectMapperWrapper} reference
     *
     * @return {@link ObjectMapperWrapper} reference
     */
    public static ObjectMapperWrapper getObjectMapperWrapper(Configuration configuration) {
        Object objectMapperPropertyInstance = configuration.instantiateClass(Configuration.PropertyKey.JACKSON_OBJECT_MAPPER);

        ObjectMapperWrapper objectMapperWrapper = new ObjectMapperWrapper();

        if (objectMapperPropertyInstance != null) {
            if(objectMapperPropertyInstance instanceof ObjectMapperSupplier) {
                ObjectMapper objectMapper = ((ObjectMapperSupplier) objectMapperPropertyInstance).get();
                if(objectMapper != null) {
                    objectMapperWrapper = new ObjectMapperWrapper(objectMapper);
                }
            }
            else if (objectMapperPropertyInstance instanceof Supplier) {
                Supplier<ObjectMapper> objectMapperSupplier = (Supplier<ObjectMapper>) objectMapperPropertyInstance;
                objectMapperWrapper = new ObjectMapperWrapper(objectMapperSupplier.get());
            }
            else if (objectMapperPropertyInstance instanceof ObjectMapper) {
                ObjectMapper objectMapper = (ObjectMapper) objectMapperPropertyInstance;
                objectMapperWrapper = new ObjectMapperWrapper(objectMapper);
            }
        }

        Object jsonSerializerPropertyInstance = configuration.instantiateClass(Configuration.PropertyKey.JSON_SERIALIZER);

        if (jsonSerializerPropertyInstance != null) {
            JsonSerializer jsonSerializer = null;

            if(jsonSerializerPropertyInstance instanceof JsonSerializerSupplier) {
                jsonSerializer = ((JsonSerializerSupplier) jsonSerializerPropertyInstance).get();
            }
            else if (jsonSerializerPropertyInstance instanceof Supplier) {
                Supplier<JsonSerializer> jsonSerializerSupplier = (Supplier<JsonSerializer>) jsonSerializerPropertyInstance;
                jsonSerializer = jsonSerializerSupplier.get();
            }
            else if (jsonSerializerPropertyInstance instanceof JsonSerializer) {
                jsonSerializer = (JsonSerializer) jsonSerializerPropertyInstance;
            }

            if (jsonSerializer != null) {
                objectMapperWrapper.setJsonSerializer(jsonSerializer);
            }
        }

        return objectMapperWrapper;
    }
}

package com.vladmihalcea.hibernate.type.jsonp.internal;

import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.JsonSerializer;
import com.vladmihalcea.hibernate.type.util.JsonSerializerSupplier;
import com.vladmihalcea.hibernate.type.util.JsonbSupplier;
import com.vladmihalcea.hibernate.type.util.JsonbWrapper;

import javax.json.JsonValue;
import javax.json.bind.Jsonb;
import java.lang.reflect.Type;
import java.util.function.Supplier;

/**
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonbUtil {

    public static <T> T fromString(String string, Class<T> clazz) {
        return JsonbWrapper.INSTANCE.fromString(string, clazz);
    }

    public static <T> T fromString(String string, Type type) {
        return JsonbWrapper.INSTANCE.fromString(string, type);
    }

    public static String toString(Object value) {
        return JsonbWrapper.INSTANCE.toString(value);
    }

    public static JsonValue toJsonNode(String value) {
        return JsonbWrapper.INSTANCE.toJsonNode(value);
    }

    public static <T> T clone(T value) {
        return JsonbWrapper.INSTANCE.clone(value);
    }

    public static JsonbWrapper getObjectMapperWrapper(Configuration configuration) {
        Object objectMapperPropertyInstance = configuration.instantiateClass(Configuration.PropertyKey.JSONB);

        JsonbWrapper jsonbWrapper = new JsonbWrapper();

        if (objectMapperPropertyInstance != null) {
            if(objectMapperPropertyInstance instanceof JsonbSupplier) {
                Jsonb objectMapper = ((JsonbSupplier) objectMapperPropertyInstance).get();
                if(objectMapper != null) {
                    jsonbWrapper = new JsonbWrapper(objectMapper);
                }
            }
            else if (objectMapperPropertyInstance instanceof Supplier) {
                Supplier<Jsonb> objectMapperSupplier = (Supplier<Jsonb>) objectMapperPropertyInstance;
                jsonbWrapper = new JsonbWrapper(objectMapperSupplier.get());
            }
            else if (objectMapperPropertyInstance instanceof Jsonb) {
                Jsonb objectMapper = (Jsonb) objectMapperPropertyInstance;
                jsonbWrapper = new JsonbWrapper(objectMapper);
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
                jsonbWrapper.setJsonSerializer(jsonSerializer);
            }
        }

        return jsonbWrapper;
    }

}

package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.function.Supplier;

/**
 * <code>JsonConfiguration</code> - It allows you to configure various JSON Hibernate Types..
 *
 * @author Vlad Mihalcea
 * @since 3.3.0
 */
public class JsonConfiguration extends Configuration {

    public static final JsonConfiguration INSTANCE = new JsonConfiguration();

    private final ObjectMapperWrapper objectMapperWrapper;

    private JsonConfiguration() {
        this(null);
    }

    public JsonConfiguration(Map<String, Object> settings) {
        super(settings);

        Object objectMapperPropertyInstance = instantiateClass(PropertyKey.JACKSON_OBJECT_MAPPER);

        ObjectMapperWrapper objectMapperWrapper = null;

        if (objectMapperPropertyInstance != null) {
            if (objectMapperPropertyInstance instanceof ObjectMapperSupplier) {
                ObjectMapperSupplier objectMapperSupplier = (ObjectMapperSupplier) objectMapperPropertyInstance;
                objectMapperWrapper = new ObjectMapperWrapper(objectMapperSupplier);
            } else if (objectMapperPropertyInstance instanceof Supplier) {
                Supplier<ObjectMapper> objectMapperSupplier = (Supplier<ObjectMapper>) objectMapperPropertyInstance;
                objectMapperWrapper = new ObjectMapperWrapper((ObjectMapperSupplier) () -> objectMapperSupplier.get());
            } else if (objectMapperPropertyInstance instanceof ObjectMapper) {
                ObjectMapper objectMapper = (ObjectMapper) objectMapperPropertyInstance;
                objectMapperWrapper = new ObjectMapperWrapper(objectMapper);
            }
        }

        if(objectMapperWrapper == null) {
            objectMapperWrapper = new ObjectMapperWrapper();
        }

        Object jsonSerializerPropertyInstance = instantiateClass(PropertyKey.JSON_SERIALIZER);

        if (jsonSerializerPropertyInstance != null) {
            JsonSerializer jsonSerializer = null;

            if (jsonSerializerPropertyInstance instanceof JsonSerializerSupplier) {
                jsonSerializer = ((JsonSerializerSupplier) jsonSerializerPropertyInstance).get();
            } else if (jsonSerializerPropertyInstance instanceof Supplier) {
                Supplier<JsonSerializer> jsonSerializerSupplier = (Supplier<JsonSerializer>) jsonSerializerPropertyInstance;
                jsonSerializer = jsonSerializerSupplier.get();
            } else if (jsonSerializerPropertyInstance instanceof JsonSerializer) {
                jsonSerializer = (JsonSerializer) jsonSerializerPropertyInstance;
            }

            if (jsonSerializer != null) {
                objectMapperWrapper.setJsonSerializer(jsonSerializer);
            }
        }

        this.objectMapperWrapper = objectMapperWrapper;
    }

    /**
     * Get {@link ObjectMapperWrapper} reference
     *
     * @return {@link ObjectMapperWrapper} reference
     */
    public ObjectMapperWrapper getObjectMapperWrapper() {
        return objectMapperWrapper;
    }
}

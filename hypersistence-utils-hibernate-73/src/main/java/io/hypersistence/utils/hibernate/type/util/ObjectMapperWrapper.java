package io.hypersistence.utils.hibernate.type.util;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.core.JsonParser;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.databind.type.TypeFactory;
import io.hypersistence.utils.common.ReflectionUtils;
import org.hibernate.HibernateException;

import java.io.IOException;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Date;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

/**
 * Wraps a Jackson {@link ObjectMapper} so that you can supply your own {@link ObjectMapper} reference.
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public class ObjectMapperWrapper implements Serializable {

    private static final ObjectMapper OBJECT_MAPPER = newObjectMapper();

    private static ObjectMapper newObjectMapper() {
        JsonMapper.Builder objectMapper = (ReflectionUtils.getClassOrNull("com.fasterxml.jackson.module.kotlin.KotlinModule") != null) ?
            ReflectionUtils.invokeStaticMethod(
                ReflectionUtils.getMethod(
                    ReflectionUtils.getClass("io.hypersistence.utils.hibernate.type.util.KotlinObjectMapperBuilder"),
                    "build"
                )
            ) :
            JsonMapper.builder();
        return objectMapper
                .findAndAddModules()
                .addModule(
                    new SimpleModule()
                        .addSerializer(OffsetDateTime.class, OffsetDateTimeSerializer.INSTANCE)
                        .addDeserializer(OffsetDateTime.class, OffsetDateTimeDeserializer.INSTANCE)
                )
                .build();
    }

    public static final ObjectMapperWrapper INSTANCE = new ObjectMapperWrapper();

    private ObjectMapper objectMapper;

    private ObjectMapperSupplier objectMapperSupplier;

    private JsonSerializer jsonSerializer = new ObjectMapperJsonSerializer();

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

    public void setObjectMapper(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T fromString(String string, Class<T> clazz) {
        try {
            return getObjectMapper().readValue(string, clazz);
        } catch (JacksonException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromString(String string, Type type) {
        try {
            return getObjectMapper().readValue(string, getObjectMapper().getTypeFactory().constructType(type));
        } catch (JacksonException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromBytes(byte[] value, Class<T> clazz) {
        try {
            return getObjectMapper().readValue(value, clazz);
        } catch (JacksonException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given byte array cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromBytes(byte[] value, Type type) {
        try {
            return getObjectMapper().readValue(value, getObjectMapper().getTypeFactory().constructType(type));
        } catch (JacksonException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given byte array cannot be transformed to Json object", e)
            );
        }
    }

    public String toString(Object value) {
        try {
            return getObjectMapper().writeValueAsString(value);
        } catch (JacksonException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e)
            );
        }
    }

    public byte[] toBytes(Object value) {
        try {
            return getObjectMapper().writeValueAsBytes(value);
        } catch (JacksonException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a byte array", e)
            );
        }
    }

    public JsonNode toJsonNode(String value) {
        try {
            return getObjectMapper().readTree(value);
        } catch (JacksonException e) {
            throw new HibernateException(
                new IllegalArgumentException(e)
            );
        }
    }

    public <T> T clone(T value) {
        return jsonSerializer.clone(value);
    }

  public TypeFactory getTypeFactory() {
    return OBJECT_MAPPER.getTypeFactory();
  }

  public static class OffsetDateTimeSerializer extends ValueSerializer<OffsetDateTime> {

        public static final OffsetDateTimeSerializer INSTANCE = new OffsetDateTimeSerializer();

        @Override
        public void serialize(OffsetDateTime offsetDateTime, JsonGenerator jsonGenerator, SerializationContext context) throws JacksonException {
            if (offsetDateTime == null) {
                jsonGenerator.writeNull();
            } else {
                jsonGenerator.writeString(offsetDateTime.format(ISO_OFFSET_DATE_TIME));
            }
        }

        @Override
        public Class<OffsetDateTime> handledType() {
            return OffsetDateTime.class;
        }
    }

    public static class OffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

        public static final OffsetDateTimeDeserializer INSTANCE = new OffsetDateTimeDeserializer();

        @Override
        public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws JacksonException {
            if (jsonParser.getText() != null) {
                try {
                    return OffsetDateTime.parse(jsonParser.getText(), ISO_OFFSET_DATE_TIME);
                } catch (DateTimeParseException e) {
                    Date date = new Date((long) jsonParser.getDoubleValue() * 1000);
                    return date.toInstant().atOffset(ZoneOffset.UTC);
                }
            }
            return null;
        }

        @Override
        public Class<OffsetDateTime> handledType() {
            return OffsetDateTime.class;
        }
    }
}

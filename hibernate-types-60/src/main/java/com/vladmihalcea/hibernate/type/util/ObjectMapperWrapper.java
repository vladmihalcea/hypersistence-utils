package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.hibernate.HibernateException;

import java.io.IOException;
import java.lang.reflect.Type;
import java.time.OffsetDateTime;

import static java.time.format.DateTimeFormatter.ISO_OFFSET_DATE_TIME;

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
        this(new ObjectMapper()
            .findAndRegisterModules()
            .registerModule(
                new SimpleModule()
                    .addSerializer(OffsetDateTime.class, OffsetDateTimeSerializer.INSTANCE)
                    .addDeserializer(OffsetDateTime.class, OffsetDateTimeDeserializer.INSTANCE)
            )
        );
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

    public <T> T fromString(String string, Class<T> clazz) {
        try {
            return objectMapper.readValue(string, clazz);
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromString(String string, Type type) {
        try {
            return objectMapper.readValue(string, objectMapper.getTypeFactory().constructType(type));
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given string value: " + string + " cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromBytes(byte[] value, Class<T> clazz) {
        try {
            return objectMapper.readValue(value, clazz);
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given byte array cannot be transformed to Json object", e)
            );
        }
    }

    public <T> T fromBytes(byte[] value, Type type) {
        try {
            return objectMapper.readValue(value, objectMapper.getTypeFactory().constructType(type));
        } catch (IOException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given byte array cannot be transformed to Json object", e)
            );
        }
    }

    public String toString(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new HibernateException(
                new IllegalArgumentException("The given Json object value: " + value + " cannot be transformed to a String", e)
            );
        }
    }

    public byte[] toBytes(Object value) {
        try {
            return objectMapper.writeValueAsBytes(value);
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

    public static class OffsetDateTimeSerializer extends com.fasterxml.jackson.databind.JsonSerializer<OffsetDateTime> {

        public static final OffsetDateTimeSerializer INSTANCE = new OffsetDateTimeSerializer();

        @Override
        public void serialize(OffsetDateTime offsetDateTime, JsonGenerator jsonGenerator, SerializerProvider serializerProvider) throws IOException {
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

    public static class OffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

        public static final OffsetDateTimeDeserializer INSTANCE = new OffsetDateTimeDeserializer();

        @Override
        public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
            if (jsonParser.getText() != null) {
                return OffsetDateTime.parse(jsonParser.getText(), ISO_OFFSET_DATE_TIME);
            }
            return null;
        }

        @Override
        public Class<OffsetDateTime> handledType() {
            return OffsetDateTime.class;
        }
    }
}

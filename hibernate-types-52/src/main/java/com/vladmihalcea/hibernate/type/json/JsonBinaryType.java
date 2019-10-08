package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.json.internal.JsonBinarySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.Type;
import java.util.Properties;

/**
 * Maps any given Java object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setObject(int, Object)} at JDBC Driver level.
 * <p>
 * If you are using <strong>PostgreSQL</strong>, you should use this {@link JsonBinaryType} to map both <strong>{@code jsonb}</strong> and <strong>{@code json}</strong> column types.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class JsonBinaryType
        extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    public static final JsonBinaryType INSTANCE = new JsonBinaryType();

    public JsonBinaryType() {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonBinaryType(Type javaType) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonBinaryType(Configuration configuration) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonBinaryType(ObjectMapper objectMapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonBinaryType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonBinaryType(ObjectMapper objectMapper, Type javaType) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonBinaryType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(objectMapperWrapper, javaType)
        );
    }

    public String getName() {
        return "jsonb";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((JsonTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }

}
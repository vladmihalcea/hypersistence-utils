package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.Type;
import java.sql.Blob;
import java.util.Properties;

/**
 * Maps any given Java object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setBlob(int, Blob)} at JDBC Driver level.
 * <p>
 * If you are using Oracle, you should use this {@link JsonBlobType} to map a {@code BLOB} column type storing JSON.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/oracle-json-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class JsonBlobType extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    public static final JsonBlobType INSTANCE = new JsonBlobType();

    public JsonBlobType() {
        super(
            org.hibernate.type.descriptor.sql.BlobTypeDescriptor.DEFAULT,
            new JsonTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonBlobType(Type javaType) {
        super(
            org.hibernate.type.descriptor.sql.BlobTypeDescriptor.DEFAULT,
            new JsonTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonBlobType(Configuration configuration) {
        super(
            org.hibernate.type.descriptor.sql.BlobTypeDescriptor.DEFAULT,
            new JsonTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonBlobType(ObjectMapper objectMapper) {
        super(
            org.hibernate.type.descriptor.sql.BlobTypeDescriptor.DEFAULT,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonBlobType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            org.hibernate.type.descriptor.sql.BlobTypeDescriptor.DEFAULT,
            new JsonTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonBlobType(ObjectMapper objectMapper, Type javaType) {
        super(
            org.hibernate.type.descriptor.sql.BlobTypeDescriptor.DEFAULT,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonBlobType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            org.hibernate.type.descriptor.sql.BlobTypeDescriptor.DEFAULT,
            new JsonTypeDescriptor(objectMapperWrapper, javaType)
        );
    }

    public String getName() {
        return "jsonb-lob";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((JsonTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }

}
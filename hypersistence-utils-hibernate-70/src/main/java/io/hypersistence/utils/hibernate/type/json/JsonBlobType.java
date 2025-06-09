package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.MutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonBlobJdbcTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonJavaTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;

import java.lang.reflect.Type;
import java.sql.Blob;

/**
 * <p>
 * Maps any given Java object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setBlob(int, Blob)} at JDBC Driver level.
 * </p>
 * <p>
 * If you are using <strong>Oracle</strong>, you can use this {@link JsonBlobType} to map a {@code BLOB} column type storing JSON.
 * </p>
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/oracle-json-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 * <p>
 * If you want to use a more portable Hibernate <code>Type</code> that can work on <strong>Oracle</strong>, <strong>SQL Server</strong>, <strong>PostgreSQL</strong>, <strong>MySQL</strong>, or <strong>H2</strong> without any configuration changes, then you should use the {@link JsonType} instead.
 * </p>
 *
 * @author Vlad Mihalcea
 */
public class JsonBlobType extends MutableDynamicParameterizedType<Object, JsonBlobJdbcTypeDescriptor, JsonJavaTypeDescriptor> {

    public static final JsonBlobType INSTANCE = new JsonBlobType();

    public JsonBlobType() {
        super(
            Object.class,
            JsonBlobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonBlobType(Type javaType) {
        super(
            Object.class,
            JsonBlobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonBlobType(JsonConfiguration configuration) {
        super(
            Object.class,
            JsonBlobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(configuration.getObjectMapperWrapper())
        );
    }

    public JsonBlobType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new JsonConfiguration(typeBootstrapContext.getConfigurationSettings()));
    }

    public JsonBlobType(ObjectMapper objectMapper) {
        super(
            Object.class,
            JsonBlobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonBlobType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            Object.class,
            JsonBlobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonBlobType(ObjectMapper objectMapper, Type javaType) {
        super(
            Object.class,
            JsonBlobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonBlobType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            Object.class,
            JsonBlobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(objectMapperWrapper, javaType)
        );
    }

    public String getName() {
        return "jsonb-lob";
    }
}
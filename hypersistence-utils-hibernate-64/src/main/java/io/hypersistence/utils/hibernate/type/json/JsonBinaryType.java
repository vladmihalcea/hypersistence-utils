package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.MutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonBinaryJdbcTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonJavaTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;

import java.lang.reflect.Type;

/**
 * <p>
 * Maps any given Java object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setObject(int, Object)} at JDBC Driver level.
 * </p>
 * <p>
 * If you are using <strong>PostgreSQL</strong>, you can use this {@link JsonBinaryType} to map both <code>jsonb</code> and <code>json</code> column types.
 * </p>
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 * <p>
 * If you want to use a more portable Hibernate <code>Type</code> that can work on <strong>Oracle</strong>, <strong>SQL Server</strong>, <strong>PostgreSQL</strong>, <strong>MySQL</strong>, or <strong>H2</strong> without any configuration changes, then you should use the {@link JsonType} instead.
 * </p>
 *
 * @author Vlad Mihalcea
 */
public class JsonBinaryType extends MutableDynamicParameterizedType<Object, JsonBinaryJdbcTypeDescriptor, JsonJavaTypeDescriptor> {

    public static final JsonBinaryType INSTANCE = new JsonBinaryType();

    public JsonBinaryType() {
        super(
            Object.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonBinaryType(Type javaType) {
        super(
            Object.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonBinaryType(JsonConfiguration configuration) {
        super(
            Object.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(configuration.getObjectMapperWrapper())
        );
    }

    public JsonBinaryType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new JsonConfiguration(typeBootstrapContext.getConfigurationSettings()));
    }

    public JsonBinaryType(ObjectMapper objectMapper) {
        super(
            Object.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonBinaryType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            Object.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonBinaryType(ObjectMapper objectMapper, Type javaType) {
        super(
            Object.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonBinaryType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            Object.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(objectMapperWrapper, javaType)
        );
    }

    public String getName() {
        return "jsonb";
    }
}
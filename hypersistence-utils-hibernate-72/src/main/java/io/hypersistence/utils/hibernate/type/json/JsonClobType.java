package io.hypersistence.utils.hibernate.type.json;

import tools.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.MutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.type.json.internal.AbstractJsonJdbcTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonClobJdbcTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonJavaTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;

import java.lang.reflect.Type;
import java.sql.Clob;

/**
 * <p>
 * Maps any given Java object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setClob(int, Clob)} at JDBC Driver level.
 * </p>
 * <p>
 * If you are using <strong>Oracle</strong>, you can use this {@link JsonClobType} to map a {@code CLOB} column type storing JSON.
 * </p>
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/oracle-json-jpa-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 * <p>
 * If you want to use a more portable Hibernate <code>Type</code> that can work on <strong>Oracle</strong>, <strong>SQL Server</strong>, <strong>PostgreSQL</strong>, <strong>MySQL</strong>, or <strong>H2</strong> without any configuration changes, then you should use the {@link JsonType} instead.
 * </p>
 *
 * @author Vlad Mihalcea
 * @author Andreas Gebhardt
 */
public class JsonClobType extends MutableDynamicParameterizedType<Object, AbstractJsonJdbcTypeDescriptor, AbstractClassJavaType<Object>> {

    public static final JsonClobType INSTANCE = new JsonClobType();

    public JsonClobType() {
        super(
            Object.class,
            JsonClobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonClobType(Type javaType) {
        super(
            Object.class,
            JsonClobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonClobType(JsonConfiguration configuration) {
        super(
            Object.class,
            JsonClobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(configuration.getObjectMapperWrapper())
        );
    }

    public JsonClobType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new JsonConfiguration(typeBootstrapContext.getConfigurationSettings()));
    }

    public JsonClobType(ObjectMapper objectMapper) {
        super(
            Object.class,
            JsonClobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonClobType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            Object.class,
            JsonClobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonClobType(ObjectMapper objectMapper, Type javaType) {
        super(
            Object.class,
            JsonClobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonClobType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            Object.class,
            JsonClobJdbcTypeDescriptor.INSTANCE,
            new JsonJavaTypeDescriptor(objectMapperWrapper, javaType)
        );
    }

    public String getName() {
        return "jsonb-clob";
    }
}
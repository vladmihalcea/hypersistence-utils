package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonClobSqlTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.Type;
import java.sql.Clob;
import java.util.Properties;

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
 */
public class JsonClobType extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    public static final JsonClobType INSTANCE = new JsonClobType();

    public JsonClobType() {
        super(
            JsonClobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonClobType(Type javaType) {
        super(
            JsonClobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonClobType(JsonConfiguration configuration) {
        super(
            JsonClobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonClobType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new JsonConfiguration(typeBootstrapContext.getConfigurationSettings()));
    }

    public JsonClobType(ObjectMapper objectMapper) {
        super(
            JsonClobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonClobType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            JsonClobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonClobType(ObjectMapper objectMapper, Type javaType) {
        super(
            JsonClobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonClobType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            JsonClobSqlTypeDescriptor.INSTANCE,
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
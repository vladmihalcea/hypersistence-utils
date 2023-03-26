package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonBlobSqlTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.Type;
import java.sql.Blob;
import java.util.Properties;

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
public class JsonBlobType extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    public static final JsonBlobType INSTANCE = new JsonBlobType();

    public JsonBlobType() {
        super(
            JsonBlobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonBlobType(Type javaType) {
        super(
            JsonBlobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonBlobType(JsonConfiguration configuration) {
        super(
            JsonBlobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonBlobType(ObjectMapper objectMapper) {
        super(
            JsonBlobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonBlobType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            JsonBlobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonBlobType(ObjectMapper objectMapper, Type javaType) {
        super(
            JsonBlobSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonBlobType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            JsonBlobSqlTypeDescriptor.INSTANCE,
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
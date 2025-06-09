package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.MutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonBinaryJdbcTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonNodeJavaTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.JsonConfiguration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;

/**
 * <p>
 * Maps a Jackson {@link JsonNode} object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setObject(int, Object)} at JDBC Driver level.
 * </p>
 * <p>
 * For instance, if you are using <strong>PostgreSQL</strong>, you can use the {@link JsonNodeBinaryType} to map both {@code jsonb} and {@code json} column types to a Jackson {@link JsonNode} object.
 * </p>
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 * </p>
 * <p>
 * If you want to use a more portable Hibernate <code>Type</code> that can work on <strong>Oracle</strong>, <strong>SQL Server</strong>, <strong>PostgreSQL</strong>, <strong>MySQL</strong>, or <strong>H2</strong> without any configuration changes, then you should use the {@link JsonType} instead.
 * </p>
 *
 * @author Vlad Mihalcea
 */
public class JsonNodeBinaryType extends MutableDynamicParameterizedType<JsonNode, JsonBinaryJdbcTypeDescriptor, JsonNodeJavaTypeDescriptor> {

    public static final JsonNodeBinaryType INSTANCE = new JsonNodeBinaryType();

    public JsonNodeBinaryType() {
        super(
            JsonNode.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonNodeJavaTypeDescriptor(JsonConfiguration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonNodeBinaryType(JsonConfiguration configuration) {
        super(
            JsonNode.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonNodeJavaTypeDescriptor(configuration.getObjectMapperWrapper())
        );
    }

    public JsonNodeBinaryType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new JsonConfiguration(typeBootstrapContext.getConfigurationSettings()));
    }

    public JsonNodeBinaryType(ObjectMapper objectMapper) {
        super(
            JsonNode.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonNodeJavaTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonNodeBinaryType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            JsonNode.class,
            JsonBinaryJdbcTypeDescriptor.INSTANCE,
            new JsonNodeJavaTypeDescriptor(objectMapperWrapper)
        );
    }

    public String getName() {
        return "jsonb-node";
    }
}
package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonBinarySqlTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonNodeTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
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
public class JsonNodeBinaryType extends AbstractHibernateType<JsonNode> {

    public static final JsonNodeBinaryType INSTANCE = new JsonNodeBinaryType();

    public JsonNodeBinaryType() {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonNodeBinaryType(Configuration configuration) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonNodeBinaryType(ObjectMapper objectMapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonNodeBinaryType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(objectMapperWrapper)
        );
    }

    public String getName() {
        return "jsonb-node";
    }
}
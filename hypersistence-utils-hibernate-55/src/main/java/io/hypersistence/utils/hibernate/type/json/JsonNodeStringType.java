package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.json.internal.JsonNodeTypeDescriptor;
import io.hypersistence.utils.hibernate.type.json.internal.JsonStringSqlTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;

/**
 * <p>
 * Maps a Jackson {@link JsonNode} object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setString(int, String)} at JDBC Driver level.
 * </p>
 * <p>
 * For instance, if you are using <strong>MySQL</strong>, you can use the {@link JsonNodeStringType} to map the {@code json} column type to a Jackson {@link JsonNode} object.
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
public class JsonNodeStringType extends AbstractHibernateType<JsonNode> {

    public static final JsonNodeStringType INSTANCE = new JsonNodeStringType();

    public JsonNodeStringType() {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonNodeStringType(Configuration configuration) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonNodeStringType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public JsonNodeStringType(ObjectMapper objectMapper) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonNodeStringType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(objectMapperWrapper)
        );
    }

    @Override
    public String getName() {
        return "jsonb-node";
    }
}
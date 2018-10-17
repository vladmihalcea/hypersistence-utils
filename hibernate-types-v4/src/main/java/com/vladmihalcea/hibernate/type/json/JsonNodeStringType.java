package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.json.internal.JsonNodeTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonStringSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;

/**
 * Maps a Jackson {@link JsonNode} on a String JSON column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class JsonNodeStringType
        extends AbstractSingleColumnStandardBasicType<JsonNode> {

    public static final JsonNodeStringType INSTANCE = new JsonNodeStringType();

    public JsonNodeStringType() {
        super(
                JsonStringSqlTypeDescriptor.INSTANCE,
                new JsonNodeTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
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
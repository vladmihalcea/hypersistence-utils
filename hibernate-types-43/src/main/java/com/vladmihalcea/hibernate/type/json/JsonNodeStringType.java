package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.internal.JsonStringSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonNodeTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;

/**
 * Maps a Jackson {@link JsonNode} on a String JSON column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/2017/08/08/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class JsonNodeStringType
        extends AbstractSingleColumnStandardBasicType<JsonNode> {

    public static final JsonNodeStringType INSTANCE = new JsonNodeStringType();

    public JsonNodeStringType() {
        super(JsonStringSqlTypeDescriptor.INSTANCE, JsonNodeTypeDescriptor.INSTANCE);
    }

    @Override
    public String getName() {
        return "jsonb-node";
    }
}
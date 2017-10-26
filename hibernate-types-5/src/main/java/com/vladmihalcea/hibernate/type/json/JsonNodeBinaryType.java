package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.json.internal.JsonBinarySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonNodeTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;

/**
 * Maps a Jackson {@link JsonNode} on a binary JSON column type.
 *
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/2017/08/08/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/">this article</a> for more info.
 *
 * @author Vlad Mihalcea
 */
public class JsonNodeBinaryType
        extends AbstractSingleColumnStandardBasicType<JsonNode> {

    public JsonNodeBinaryType() {
        super(JsonBinarySqlTypeDescriptor.INSTANCE, JsonNodeTypeDescriptor.INSTANCE);
    }

    public String getName() {
        return "jsonb-node";
    }
}
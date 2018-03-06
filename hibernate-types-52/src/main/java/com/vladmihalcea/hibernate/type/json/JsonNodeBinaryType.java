package com.vladmihalcea.hibernate.type.json;

import org.hibernate.type.AbstractSingleColumnStandardBasicType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.json.internal.JsonBinarySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonNodeTypeDescriptor;

/**
 * Maps a Jackson {@link JsonNode} on a binary JSON column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-store-schema-less-eav-entity-attribute-value-data-using-json-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class JsonNodeBinaryType
        extends AbstractSingleColumnStandardBasicType<JsonNode> {

    public static final JsonNodeBinaryType INSTANCE = new JsonNodeBinaryType();

    public JsonNodeBinaryType() {
        super(JsonBinarySqlTypeDescriptor.INSTANCE, JsonNodeTypeDescriptor.INSTANCE);
    }

    public JsonNodeBinaryType(ObjectMapper mapper) {
    	super(JsonBinarySqlTypeDescriptor.INSTANCE, new JsonNodeTypeDescriptor(mapper));
    }

    public String getName() {
        return "jsonb-node";
    }
}
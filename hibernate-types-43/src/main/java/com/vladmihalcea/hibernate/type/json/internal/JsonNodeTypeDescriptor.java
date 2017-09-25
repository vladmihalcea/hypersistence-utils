package com.vladmihalcea.hibernate.type.json.internal;

import com.fasterxml.jackson.databind.JsonNode;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;

/**
 * @author Vlad Mihalcea
 */
public class JsonNodeTypeDescriptor
        extends AbstractTypeDescriptor<JsonNode> {

    public static final JsonNodeTypeDescriptor INSTANCE = new JsonNodeTypeDescriptor();

    public JsonNodeTypeDescriptor() {
        super(JsonNode.class, new MutableMutabilityPlan<JsonNode>() {
            @Override
            protected JsonNode deepCopyNotNull(JsonNode value) {
                return JacksonUtil.clone(value);
            }
        });
    }

    @Override
    public boolean areEqual(JsonNode one, JsonNode another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return JacksonUtil.toJsonNode(JacksonUtil.toString(one)).equals(
                JacksonUtil.toJsonNode(JacksonUtil.toString(another)));
    }

    @Override
    public String toString(JsonNode value) {
        return JacksonUtil.toString(value);
    }

    @Override
    public JsonNode fromString(String string) {
        return JacksonUtil.toJsonNode(string);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(JsonNode value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (X) toString(value);
        }
        if (JsonNode.class.isAssignableFrom(type)) {
            return (X) JacksonUtil.toJsonNode(toString(value));
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> JsonNode wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        return fromString(value.toString());
    }

}

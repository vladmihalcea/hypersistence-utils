package com.vladmihalcea.hibernate.type.json.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;

import java.io.Serializable;

/**
 * @author Vlad Mihalcea
 */
public class JsonNodeTypeDescriptor
        extends AbstractTypeDescriptor<JsonNode> {

    public static final JsonNodeTypeDescriptor INSTANCE = new JsonNodeTypeDescriptor();

    private ObjectMapperWrapper objectMapperWrapper;

    public JsonNodeTypeDescriptor() {
        super(JsonNode.class, new MutableMutabilityPlan<JsonNode>() {
            @Override
            public Serializable disassemble(JsonNode value) {
                return JacksonUtil.toString(value);
            }

            @Override
            public JsonNode assemble(Serializable cached) {
                return JacksonUtil.toJsonNode((String) cached);
            }

            @Override
            protected JsonNode deepCopyNotNull(JsonNode value) {
                return ObjectMapperWrapper.INSTANCE.clone(value);
            }
        });
        this.objectMapperWrapper = ObjectMapperWrapper.INSTANCE;
    }

    public JsonNodeTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper) {
        super(JsonNode.class, new MutableMutabilityPlan<JsonNode>() {
            @Override
            public Serializable disassemble(JsonNode value) {
                return JacksonUtil.toString(value);
            }

            @Override
            public JsonNode assemble(Serializable cached) {
                return JacksonUtil.toJsonNode((String) cached);
            }

            @Override
            protected JsonNode deepCopyNotNull(JsonNode value) {
                return objectMapperWrapper.clone(value);
            }
        });
        this.objectMapperWrapper = objectMapperWrapper;
    }

    @Override
    public boolean areEqual(JsonNode one, JsonNode another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return objectMapperWrapper.toJsonNode(objectMapperWrapper.toString(one)).equals(
                objectMapperWrapper.toJsonNode(objectMapperWrapper.toString(another)));
    }

    @Override
    public String toString(JsonNode value) {
        return objectMapperWrapper.toString(value);
    }

    @Override
    public JsonNode fromString(String string) {
        return objectMapperWrapper.toJsonNode(string);
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
            return (X) objectMapperWrapper.toJsonNode(toString(value));
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

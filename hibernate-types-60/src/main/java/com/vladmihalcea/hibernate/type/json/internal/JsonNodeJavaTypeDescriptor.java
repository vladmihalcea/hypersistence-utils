package com.vladmihalcea.hibernate.type.json.internal;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.SharedSessionContract;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;

import java.io.Serializable;

/**
 * @author Vlad Mihalcea
 */
public class JsonNodeJavaTypeDescriptor
        extends AbstractClassJavaType<JsonNode> {

    public static final JsonNodeJavaTypeDescriptor INSTANCE = new JsonNodeJavaTypeDescriptor();

    private ObjectMapperWrapper objectMapperWrapper;

    public JsonNodeJavaTypeDescriptor() {
        this(ObjectMapperWrapper.INSTANCE);
    }

    public JsonNodeJavaTypeDescriptor(final ObjectMapperWrapper objectMapperWrapper) {
        super(JsonNode.class, new MutableMutabilityPlan<JsonNode>() {
            @Override
            public Serializable disassemble(JsonNode value, SharedSessionContract session) {
                return JacksonUtil.toString(value);
            }

            @Override
            public JsonNode assemble(Serializable cached, SharedSessionContract session) {
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
    public JsonNode fromString(CharSequence string) {
        return objectMapperWrapper.toJsonNode((String) string);
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

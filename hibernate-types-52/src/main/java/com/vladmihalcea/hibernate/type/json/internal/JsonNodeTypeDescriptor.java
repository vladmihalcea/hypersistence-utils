package com.vladmihalcea.hibernate.type.json.internal;

import java.io.Serializable;
import java.util.Objects;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * @author Vlad Mihalcea
 */
public class JsonNodeTypeDescriptor
        extends AbstractTypeDescriptor<JsonNode> {

    public static final JsonNodeTypeDescriptor INSTANCE = new JsonNodeTypeDescriptor();

    protected final ObjectMapper mapperInstance;

    public JsonNodeTypeDescriptor() {
        this(JacksonUtil.OBJECT_MAPPER);
    }

    public JsonNodeTypeDescriptor(ObjectMapper mapper) {
        super(JsonNode.class, new MutableMutabilityPlan<JsonNode>() {
            @Override
            public Serializable disassemble(JsonNode value) {
                return JacksonUtil.toString(mapper, value);
            }

            @Override
            public JsonNode assemble(Serializable cached) {
                return JacksonUtil.toJsonNode(mapper, (String) cached);
            }

            @Override
            protected JsonNode deepCopyNotNull(JsonNode value) {
                return JacksonUtil.clone(mapper, value);
            }
        });
        this.mapperInstance = Objects.requireNonNull(mapper, "No object mapper provided");
    }

    @Override
    public boolean areEqual(JsonNode one, JsonNode another) {
        if (one == another) {
            return true;
        }

        if ((one == null) || (another == null)) {
            return false;
        }

        String oneString = JacksonUtil.toString(mapperInstance, one);
        JsonNode oneNode = JacksonUtil.toJsonNode(mapperInstance, oneString);
        String anotherString = JacksonUtil.toString(mapperInstance, another);
        JsonNode anotherNode = JacksonUtil.toJsonNode(mapperInstance, anotherString);
        return Objects.equals(oneNode, anotherNode);
    }

    @Override
    public String toString(JsonNode value) {
        return JacksonUtil.toString(mapperInstance, value);
    }

    @Override
    public JsonNode fromString(String string) {
        return JacksonUtil.toJsonNode(mapperInstance, string);
    }

    @Override
    public <X> X unwrap(JsonNode value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }

        if (String.class.isAssignableFrom(type)) {
            String str = toString(value);
            return type.cast(str);
        }

        if (JsonNode.class.isAssignableFrom(type)) {
            String str = toString(value);
            JsonNode node = JacksonUtil.toJsonNode(mapperInstance, str);
            return type.cast(node);
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

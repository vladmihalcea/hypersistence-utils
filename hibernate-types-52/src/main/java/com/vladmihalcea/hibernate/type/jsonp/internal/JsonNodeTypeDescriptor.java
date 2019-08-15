package com.vladmihalcea.hibernate.type.jsonp.internal;

import com.vladmihalcea.hibernate.type.util.JsonbWrapper;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.type.descriptor.java.MutableMutabilityPlan;

import javax.json.JsonValue;
import java.io.Serializable;

/**
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonNodeTypeDescriptor
        extends AbstractTypeDescriptor<JsonValue> {

    public static final JsonNodeTypeDescriptor INSTANCE = new JsonNodeTypeDescriptor();

    private JsonbWrapper jsonbWrapper;

    public JsonNodeTypeDescriptor() {
        super(JsonValue.class, new MutableMutabilityPlan<JsonValue>() {
            @Override
            public Serializable disassemble(JsonValue value) {
                return JsonbUtil.toString(value);
            }

            @Override
            public JsonValue assemble(Serializable cached) {
                return JsonbUtil.toJsonNode((String) cached);
            }

            @Override
            protected JsonValue deepCopyNotNull(JsonValue value) {
                return JsonbWrapper.INSTANCE.clone(value);
            }
        });
        this.jsonbWrapper = JsonbWrapper.INSTANCE;
    }

    public JsonNodeTypeDescriptor(final JsonbWrapper jsonbWrapper) {
        super(JsonValue.class, new MutableMutabilityPlan<JsonValue>() {
            @Override
            public Serializable disassemble(JsonValue value) {
                return JsonbUtil.toString(value);
            }

            @Override
            public JsonValue assemble(Serializable cached) {
                return JsonbUtil.toJsonNode((String) cached);
            }

            @Override
            protected JsonValue deepCopyNotNull(JsonValue value) {
                return jsonbWrapper.clone(value);
            }
        });
        this.jsonbWrapper = jsonbWrapper;
    }

    @Override
    public boolean areEqual(JsonValue one, JsonValue another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return jsonbWrapper.toJsonNode(jsonbWrapper.toString(one)).equals(
                jsonbWrapper.toJsonNode(jsonbWrapper.toString(another)));
    }

    @Override
    public String toString(JsonValue value) {
        return jsonbWrapper.toString(value);
    }

    @Override
    public JsonValue fromString(String string) {
        return jsonbWrapper.toJsonNode(string);
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(JsonValue value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (X) toString(value);
        }
        if (JsonValue.class.isAssignableFrom(type)) {
            return (X) jsonbWrapper.toJsonNode(toString(value));
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> JsonValue wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        return fromString(value.toString());
    }

}

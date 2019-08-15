package com.vladmihalcea.hibernate.type.jsonp;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonBinarySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonNodeTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonbUtil;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.JsonbWrapper;

import javax.json.JsonValue;
import javax.json.bind.Jsonb;

/**
 * Maps a Json-P {@link JsonValue} object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setObject(int, Object)} at JDBC Driver level. For instance, if you are using PostgreSQL, you should be using {@link JsonNodeBinaryType} to map both {@code jsonb} and {@code json} column types to a Jackson {@link JsonNode} object.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonNodeBinaryType extends AbstractHibernateType<JsonValue> {

    public static final JsonNodeBinaryType INSTANCE = new JsonNodeBinaryType();

    public JsonNodeBinaryType() {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(JsonbUtil.getObjectMapperWrapper(Configuration.INSTANCE))
        );
    }

    public JsonNodeBinaryType(Configuration configuration) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(JsonbUtil.getObjectMapperWrapper(configuration)),
            configuration
        );
    }

    public JsonNodeBinaryType(Jsonb objectMapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(new JsonbWrapper(objectMapper))
        );
    }

    public JsonNodeBinaryType(JsonbWrapper jsonbWrapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(jsonbWrapper)
        );
    }

    public String getName() {
        return "jsonb-p-value";
    }
}
package com.vladmihalcea.hibernate.type.jsonp;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonNodeTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonStringSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonbUtil;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.JsonbWrapper;

import javax.json.JsonValue;
import javax.json.bind.Jsonb;

/**
 * Maps a Json-P {@link JsonValue} object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setString(int, String)} at JDBC Driver level. For instance, if you are using MySQL, you should be using {@link JsonNodeStringType} to map the {@code json} column type to a Jackson {@link JsonNode} object.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonNodeStringType extends AbstractHibernateType<JsonValue> {

    public static final JsonNodeStringType INSTANCE = new JsonNodeStringType();

    public JsonNodeStringType() {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(JsonbUtil.getObjectMapperWrapper(Configuration.INSTANCE))
        );
    }

    public JsonNodeStringType(Configuration configuration) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(JsonbUtil.getObjectMapperWrapper(configuration)),
            configuration
        );
    }

    public JsonNodeStringType(Jsonb objectMapper) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(new JsonbWrapper(objectMapper))
        );
    }

    public JsonNodeStringType(JsonbWrapper jsonbWrapper) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonNodeTypeDescriptor(jsonbWrapper)
        );
    }

    @Override
    public String getName() {
        return "jsonb-p-value";
    }
}
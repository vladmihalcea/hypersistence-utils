package com.vladmihalcea.hibernate.type.jsonp;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonBinarySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonbUtil;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.JsonbWrapper;
import org.hibernate.usertype.DynamicParameterizedType;

import javax.json.bind.Jsonb;
import java.lang.reflect.Type;
import java.util.Properties;

/**
 * Maps any given Java object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setObject(int, Object)} at JDBC Driver level. For instance, if you are using PostgreSQL, you should be using this {@link JsonBinaryType} to map both {@code jsonb} and {@code json} column types.
 * <p>
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonBinaryType extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    public static final JsonBinaryType INSTANCE = new JsonBinaryType();

    public JsonBinaryType() {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonbUtil.getObjectMapperWrapper(Configuration.INSTANCE))
        );
    }

    public JsonBinaryType(Configuration configuration) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonbUtil.getObjectMapperWrapper(configuration)),
            configuration
        );
    }

    public JsonBinaryType(Jsonb objectMapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new JsonbWrapper(objectMapper))
        );
    }

    public JsonBinaryType(JsonbWrapper jsonbWrapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(jsonbWrapper)
        );
    }

    public JsonBinaryType(Jsonb objectMapper, Type javaType) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new JsonbWrapper(objectMapper), javaType)
        );
    }

    public JsonBinaryType(JsonbWrapper jsonbWrapper, Type javaType) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(jsonbWrapper, javaType)
        );
    }

    public String getName() {
        return "jsonb-p";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((JsonTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }

}
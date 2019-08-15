package com.vladmihalcea.hibernate.type.jsonp;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonStringSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonTypeDescriptor;
import com.vladmihalcea.hibernate.type.jsonp.internal.JsonbUtil;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.JsonbWrapper;
import org.hibernate.usertype.DynamicParameterizedType;

import javax.json.bind.Jsonb;
import java.lang.reflect.Type;
import java.util.Properties;

/**
 * Maps any given Java object on a JSON column type that is managed via {@link java.sql.PreparedStatement#setString(int, String)} at JDBC Driver level. For instance, if you are using MySQL, you should be using this {@link JsonStringType} to map the {@code json} column type.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class JsonStringType extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    public static final JsonStringType INSTANCE = new JsonStringType();

    public JsonStringType() {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonbUtil.getObjectMapperWrapper(Configuration.INSTANCE))
        );
    }

    public JsonStringType(Configuration configuration) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(JsonbUtil.getObjectMapperWrapper(configuration)),
            configuration
        );
    }

    public JsonStringType(Jsonb objectMapper) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new JsonbWrapper(objectMapper))
        );
    }

    public JsonStringType(JsonbWrapper jsonbWrapper) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(jsonbWrapper)
        );
    }

    public JsonStringType(Jsonb objectMapper, Type javaType) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new JsonbWrapper(objectMapper), javaType)
        );
    }

    public JsonStringType(JsonbWrapper jsonbWrapper, Type javaType) {
        super(
            JsonStringSqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(jsonbWrapper, javaType)
        );
    }

    @Override
    public String getName() {
        return "json-p";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((JsonTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}
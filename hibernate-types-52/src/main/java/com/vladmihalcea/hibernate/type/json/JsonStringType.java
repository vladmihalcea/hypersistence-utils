package com.vladmihalcea.hibernate.type.json;

import java.util.Properties;

import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil;
import com.vladmihalcea.hibernate.type.json.internal.JsonStringSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;

/**
 * Maps any given Java object on a string-based JSON column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class JsonStringType
        extends AbstractSingleColumnStandardBasicType<Object> implements DynamicParameterizedType {

    public static final JsonStringType INSTANCE = new JsonStringType();

    public JsonStringType() {
    	this(JacksonUtil.OBJECT_MAPPER);
    }

    public JsonStringType(ObjectMapper mapper) {
        super(JsonStringSqlTypeDescriptor.INSTANCE, new JsonTypeDescriptor(mapper));
    }

    public String getName() {
        return "json";
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
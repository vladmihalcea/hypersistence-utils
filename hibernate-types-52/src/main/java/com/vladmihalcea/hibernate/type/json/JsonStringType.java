package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.type.json.internal.JsonStringSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps any given Java object on a string-based JSON column type.
 *
 *  @see <a href="https://vladmihalcea.com/2016/06/20/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> for more info.
 *
 * @author Vlad Mihalcea
 */
public class JsonStringType
        extends AbstractSingleColumnStandardBasicType<Object> implements DynamicParameterizedType {

    public JsonStringType() {
        super(JsonStringSqlTypeDescriptor.INSTANCE, new JsonTypeDescriptor());
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
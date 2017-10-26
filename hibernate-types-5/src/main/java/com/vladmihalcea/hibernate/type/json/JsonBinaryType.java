package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.type.json.internal.JsonBinarySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps any given Java object on a binary JSON column type.
 *
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/2016/06/20/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> for more info.
 *
 * @author Vlad Mihalcea
 */
public class JsonBinaryType
        extends AbstractSingleColumnStandardBasicType<Object> implements DynamicParameterizedType {

    public JsonBinaryType() {
        super(JsonBinarySqlTypeDescriptor.INSTANCE, new JsonTypeDescriptor());
    }

    public String getName() {
        return "jsonb";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((JsonTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }

}
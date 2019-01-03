package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.json.internal.JsonStringSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.Type;
import java.util.Properties;

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
        super(
                JsonStringSqlTypeDescriptor.INSTANCE,
                new JsonTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonStringType(ObjectMapper objectMapper) {
        super(
                JsonStringSqlTypeDescriptor.INSTANCE,
                new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonStringType(ObjectMapperWrapper objectMapperWrapper) {
        super(
                JsonStringSqlTypeDescriptor.INSTANCE,
                new JsonTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonStringType(ObjectMapper objectMapper, Type javaType) {
        super(
                JsonStringSqlTypeDescriptor.INSTANCE,
                new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonStringType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
                JsonStringSqlTypeDescriptor.INSTANCE,
                new JsonTypeDescriptor(objectMapperWrapper, javaType)
        );
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
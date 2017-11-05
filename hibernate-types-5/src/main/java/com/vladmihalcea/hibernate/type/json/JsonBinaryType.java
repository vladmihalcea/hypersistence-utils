package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.json.internal.JsonBinarySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import com.vladmihalcea.hibernate.type.util.PropertyLoader;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps any given Java object on a binary JSON column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-json-objects-using-generic-hibernate-types/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class JsonBinaryType
        extends AbstractSingleColumnStandardBasicType<Object> implements DynamicParameterizedType {

    public static final JsonBinaryType INSTANCE = new JsonBinaryType();

    public JsonBinaryType() {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(PropertyLoader.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonBinaryType(ObjectMapper objectMapper) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonBinaryType(ObjectMapper objectMapper, Class javaType) {
        super(
            JsonBinarySqlTypeDescriptor.INSTANCE,
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public String getName() {
        return "jsonb";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((JsonTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }

}
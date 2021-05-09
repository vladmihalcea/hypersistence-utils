package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.json.internal.JsonSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.json.internal.JsonTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.reflect.Type;
import java.util.Properties;

/**
 * Maps any given Java object on a JSON column type.
 * <p>
 * It works with PostgreSQL, MySQL, SQL Server, H2, HSQLDB.
 *
 * @author Vlad Mihalcea
 */
public class JsonType
        extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    public static final JsonType INSTANCE = new JsonType();

    public JsonType() {
        super(
            new JsonSqlTypeDescriptor(),
            new JsonTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper())
        );
    }

    public JsonType(Type javaType) {
        super(
            new JsonSqlTypeDescriptor(),
            new JsonTypeDescriptor(Configuration.INSTANCE.getObjectMapperWrapper(), javaType)
        );
    }

    public JsonType(Configuration configuration) {
        super(
            new JsonSqlTypeDescriptor(),
            new JsonTypeDescriptor(configuration.getObjectMapperWrapper()),
            configuration
        );
    }

    public JsonType(ObjectMapper objectMapper) {
        super(
            new JsonSqlTypeDescriptor(),
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper))
        );
    }

    public JsonType(ObjectMapperWrapper objectMapperWrapper) {
        super(
            new JsonSqlTypeDescriptor(),
            new JsonTypeDescriptor(objectMapperWrapper)
        );
    }

    public JsonType(ObjectMapper objectMapper, Type javaType) {
        super(
            new JsonSqlTypeDescriptor(),
            new JsonTypeDescriptor(new ObjectMapperWrapper(objectMapper), javaType)
        );
    }

    public JsonType(ObjectMapperWrapper objectMapperWrapper, Type javaType) {
        super(
            new JsonSqlTypeDescriptor(),
            new JsonTypeDescriptor(objectMapperWrapper, javaType)
        );
    }

    public String getName() {
        return "json";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((JsonTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }

}
package com.vladmihalcea.hibernate.type;

import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.ParameterizedType;

import java.sql.Types;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class DynamicMutableType<T, JDBC extends JdbcType, JAVA extends JavaType<T>> extends MutableType<T, JDBC, JAVA> implements DynamicParameterizedType {

    /**
     * {@inheritDoc}
     */
    public DynamicMutableType(Class<T> returnedClass, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor) {
        super(returnedClass, jdbcTypeDescriptor, javaTypeDescriptor);
    }

    public DynamicMutableType(Class<T> returnedClass, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor, Configuration configuration) {
        super(returnedClass, jdbcTypeDescriptor, javaTypeDescriptor, configuration);
    }

    @Override
    public void setParameterValues(Properties parameters) {
        JAVA javaTypeDescriptor = getJavaTypeDescriptor();
        if(javaTypeDescriptor instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) javaTypeDescriptor;
            parameterizedType.setParameterValues(parameters);
        }
    }
}
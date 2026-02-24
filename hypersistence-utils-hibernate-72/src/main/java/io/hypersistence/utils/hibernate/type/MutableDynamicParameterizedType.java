package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.model.domain.BasicDomainType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.ParameterizedType;

import java.sql.CallableStatement;
import java.sql.SQLException;
import java.util.Properties;

import static jakarta.persistence.metamodel.Type.PersistenceType.BASIC;

/**
 * @author Vlad Mihalcea
 */
public class MutableDynamicParameterizedType<T, JDBC extends JdbcType, JAVA extends JavaType<T>> extends MutableType<T, JDBC, JAVA> implements DynamicParameterizedType, BasicDomainType<T>{

    /**
     * {@inheritDoc}
     */
    public MutableDynamicParameterizedType(Class<T> returnedClass, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor) {
        super(returnedClass, jdbcTypeDescriptor, javaTypeDescriptor);
    }

    public MutableDynamicParameterizedType(Class<T> returnedClass, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor, Configuration configuration) {
        super(returnedClass, jdbcTypeDescriptor, javaTypeDescriptor, configuration);
    }

    @Override
    public void setParameterValues(Properties parameters) {
        JAVA javaTypeDescriptor = getJavaTypeDescriptor();
        if(javaTypeDescriptor instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) javaTypeDescriptor;
            parameterizedType.setParameterValues(parameters);
        }
        JDBC jdbcTypeDescriptor = getJdbcTypeDescriptor();
        if(jdbcTypeDescriptor instanceof ParameterizedType) {
            ParameterizedType parameterizedType = (ParameterizedType) jdbcTypeDescriptor;
            parameterizedType.setParameterValues(parameters);
        }
    }

    @Override
    public PersistenceType getPersistenceType() {
        return BASIC;
    }

    @Override
    public boolean canDoExtraction() {
        return true;
    }

    @Override
    public JdbcType getJdbcType() {
        return getJdbcTypeDescriptor();
    }

    @Override
    public T extract(CallableStatement statement, int paramIndex, SharedSessionContractImplementor session) throws SQLException {
        return getJdbcTypeDescriptor().getExtractor(getJavaTypeDescriptor()).extract(statement, paramIndex, session);
    }

    @Override
    public T extract(CallableStatement statement, String paramName, SharedSessionContractImplementor session) throws SQLException {
        return getJdbcTypeDescriptor().getExtractor(getJavaTypeDescriptor()).extract(statement, paramName, session);
    }
}
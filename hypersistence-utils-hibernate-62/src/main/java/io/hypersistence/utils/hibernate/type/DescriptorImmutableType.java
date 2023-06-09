package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.query.BindableType;
import org.hibernate.query.sqm.SqmExpressible;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.usertype.UserType;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * Very convenient base class for implementing immutable object types using Hibernate {@link UserType} using the {@link JdbcType} and {@link JavaType} descriptors.
 *
 * @author Vlad Mihalcea
 */
public abstract class DescriptorImmutableType<T, JDBC extends JdbcType, JAVA extends JavaType<T>> extends ImmutableType<T> implements BindableType<T>, SqmExpressible<T> {

    private final JDBC jdbcTypeDescriptor;
    private final JAVA javaTypeDescriptor;

    public DescriptorImmutableType(Class<T> clazz, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor) {
        super(clazz);
        this.jdbcTypeDescriptor = jdbcTypeDescriptor;
        this.javaTypeDescriptor = javaTypeDescriptor;
    }

    public DescriptorImmutableType(Class<T> clazz, JDBC jdbcTypeDescriptor, JAVA javaTypeDescriptor, Configuration configuration) {
        super(clazz, configuration);
        this.jdbcTypeDescriptor = jdbcTypeDescriptor;
        this.javaTypeDescriptor = javaTypeDescriptor;
    }

    @Override
    public T nullSafeGet(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws
        SQLException {
        return jdbcTypeDescriptor.getExtractor(javaTypeDescriptor).extract(rs, position, session);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int index, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        jdbcTypeDescriptor.getBinder(javaTypeDescriptor).bind(st, (T) value, index, session);
    }

    @Override
    protected T get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return nullSafeGet(rs, position, session, owner);
    }

    @Override
    protected void set(PreparedStatement st, T value, int index, SharedSessionContractImplementor session) throws SQLException {
        nullSafeSet(st, value, index, session);
    }

    @Override
    public int getSqlType() {
        return jdbcTypeDescriptor.getJdbcTypeCode();
    }

    @Override
    public Class<T> getBindableJavaType() {
        return returnedClass();
    }

    @Override
    public JavaType<T> getExpressibleJavaType() {
        return javaTypeDescriptor;
    }
}

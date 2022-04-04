package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;

import java.sql.*;

/**
 * @author Vlad Mihalcea
 */
public class JsonJdbcTypeDescriptor extends AbstractJsonJdbcTypeDescriptor {

    private volatile Dialect dialect;
    private volatile AbstractJsonJdbcTypeDescriptor sqlTypeDescriptor;

    @Override
    public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
        return new BasicBinder<X>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                sqlTypeDescriptor(st.getConnection()).getBinder(javaType).bind(
                    st, value, index, options
                );
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                sqlTypeDescriptor(st.getConnection()).getBinder(javaType).bind(
                    st, value, name, options
                );
            }
        };
    }

    @Override
    protected Object extractJson(ResultSet rs, int paramIndex) throws SQLException {
        return sqlTypeDescriptor(rs.getStatement().getConnection()).extractJson(rs, paramIndex);
    }

    @Override
    protected Object extractJson(CallableStatement statement, int index) throws SQLException {
        return sqlTypeDescriptor(statement.getConnection()).extractJson(statement, index);
    }

    @Override
    protected Object extractJson(CallableStatement statement, String name) throws SQLException {
        return sqlTypeDescriptor(statement.getConnection()).extractJson(statement, name);
    }

    private AbstractJsonJdbcTypeDescriptor sqlTypeDescriptor(Connection connection) {
        if(sqlTypeDescriptor == null) {
            sqlTypeDescriptor = resolveSqlTypeDescriptor(connection);
        }
        return sqlTypeDescriptor;
    }

    private AbstractJsonJdbcTypeDescriptor resolveSqlTypeDescriptor(Connection connection) {
        try {
            StandardDialectResolver dialectResolver = new StandardDialectResolver();
            dialect = dialectResolver.resolveDialect(
                new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData())
            );
            if(PostgreSQLDialect.class.isInstance(dialect)) {
                return JsonBinaryJdbcTypeDescriptor.INSTANCE;
            } else if(H2Dialect.class.isInstance(dialect)) {
                return JsonBytesJdbcTypeDescriptor.INSTANCE;
            } else {
                return JsonStringJdbcTypeDescriptor.INSTANCE;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }


}

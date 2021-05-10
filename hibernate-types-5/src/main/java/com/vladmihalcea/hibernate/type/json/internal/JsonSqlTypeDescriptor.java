package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.PostgreSQL81Dialect;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

import java.sql.*;

/**
 * @author Vlad Mihalcea
 */
public class JsonSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor {

    private volatile Dialect dialect;
    private volatile AbstractJsonSqlTypeDescriptor sqlTypeDescriptor;

    @Override
    public <X> ValueBinder<X> getBinder(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicBinder<X>(javaTypeDescriptor, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {

                sqlTypeDescriptor(st.getConnection()).getBinder(javaTypeDescriptor).bind(
                    st, value, index, options
                );
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                sqlTypeDescriptor(st.getConnection()).getBinder(javaTypeDescriptor).bind(
                    st, value, name, options
                );
            }
        };
    }

    @Override
    protected Object extractJson(ResultSet rs, String name) throws SQLException {
        return sqlTypeDescriptor(rs.getStatement().getConnection()).extractJson(rs, name);
    }

    @Override
    protected Object extractJson(CallableStatement statement, int index) throws SQLException {
        return sqlTypeDescriptor(statement.getConnection()).extractJson(statement, index);
    }

    @Override
    protected Object extractJson(CallableStatement statement, String name) throws SQLException {
        return sqlTypeDescriptor(statement.getConnection()).extractJson(statement, name);
    }

    private AbstractJsonSqlTypeDescriptor sqlTypeDescriptor(Connection connection) {
        if(sqlTypeDescriptor == null) {
            sqlTypeDescriptor = resolveSqlTypeDescriptor(connection);
        }
        return sqlTypeDescriptor;
    }

    private AbstractJsonSqlTypeDescriptor resolveSqlTypeDescriptor(Connection connection) {
        try {
            StandardDialectResolver dialectResolver = new StandardDialectResolver();
            dialect = dialectResolver.resolveDialect(
                new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData())
            );
            if(PostgreSQL81Dialect.class.isInstance(dialect)) {
                return JsonBinarySqlTypeDescriptor.INSTANCE;
            } else if(H2Dialect.class.isInstance(dialect)) {
                return JsonBytesSqlTypeDescriptor.INSTANCE;
            } else {
                return JsonStringSqlTypeDescriptor.INSTANCE;
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }


}

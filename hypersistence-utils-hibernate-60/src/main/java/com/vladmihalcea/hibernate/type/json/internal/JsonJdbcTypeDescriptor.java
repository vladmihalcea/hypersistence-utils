package com.vladmihalcea.hibernate.type.json.internal;

import com.vladmihalcea.hibernate.type.util.ParameterTypeUtils;
import com.vladmihalcea.hibernate.util.StringUtils;
import org.hibernate.dialect.*;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.ParameterizedType;

import java.sql.*;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class JsonJdbcTypeDescriptor extends AbstractJsonJdbcTypeDescriptor implements ParameterizedType {

    private volatile Dialect dialect;
    private volatile AbstractJsonJdbcTypeDescriptor jdbcTypeDescriptor;

    private volatile Properties properties;

    public JsonJdbcTypeDescriptor() {
    }

    public JsonJdbcTypeDescriptor(Properties properties) {
        this.properties = properties;
    }

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
        if (jdbcTypeDescriptor == null) {
            jdbcTypeDescriptor = resolveJdbcTypeDescriptor(connection);
        }
        return jdbcTypeDescriptor;
    }

    private AbstractJsonJdbcTypeDescriptor resolveJdbcTypeDescriptor(Connection connection) {
        try {
            StandardDialectResolver dialectResolver = new StandardDialectResolver();
            DatabaseMetaDataDialectResolutionInfoAdapter metaDataInfo = new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData());
            dialect = dialectResolver.resolveDialect(metaDataInfo);
            if (dialect instanceof PostgreSQLDialect) {
                return JsonBinaryJdbcTypeDescriptor.INSTANCE;
            } else if (dialect instanceof H2Dialect) {
                return JsonBytesJdbcTypeDescriptor.INSTANCE;
            } else if (dialect instanceof OracleDialect) {
                if (properties != null) {
                    DynamicParameterizedType.ParameterType parameterType = ParameterTypeUtils.resolve(properties);
                    if (parameterType != null) {
                        String columnType = ParameterTypeUtils.getColumnType(parameterType);
                        if (!StringUtils.isBlank(columnType)) {
                            switch (columnType) {
                                case "json":
                                    return JsonBytesJdbcTypeDescriptor.of(Database.ORACLE);
                                case "blob":
                                case "clob":
                                    return JsonBlobJdbcTypeDescriptor.INSTANCE;
                                case "varchar2":
                                case "nvarchar2":
                                    return JsonStringJdbcTypeDescriptor.INSTANCE;
                            }
                        }
                    }
                }
                if (metaDataInfo.getDatabaseMajorVersion() >= 21) {
                    return JsonBytesJdbcTypeDescriptor.of(Database.ORACLE);
                }
            }
            return JsonStringJdbcTypeDescriptor.INSTANCE;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getJdbcTypeCode() {
        return jdbcTypeDescriptor != null ?
            jdbcTypeDescriptor.getJdbcTypeCode() :
            super.getJdbcTypeCode();
    }

    @Override
    public void setParameterValues(Properties parameters) {
        if (properties == null) {
            properties = parameters;
        } else {
            properties.putAll(parameters);
        }
    }
}

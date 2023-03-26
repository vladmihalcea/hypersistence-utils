package io.hypersistence.utils.hibernate.type.json.internal;

import io.hypersistence.utils.hibernate.type.util.ParameterTypeUtils;
import io.hypersistence.utils.hibernate.util.StringUtils;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.H2Dialect;
import org.hibernate.dialect.Oracle8iDialect;
import org.hibernate.dialect.PostgreSQL81Dialect;
import org.hibernate.engine.jdbc.dialect.internal.StandardDialectResolver;
import org.hibernate.engine.jdbc.dialect.spi.DatabaseMetaDataDialectResolutionInfoAdapter;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.ParameterizedType;

import java.sql.*;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class JsonSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor implements ParameterizedType {

    private volatile Dialect dialect;
    private volatile AbstractJsonSqlTypeDescriptor sqlTypeDescriptor;

    private volatile Properties properties;

    public JsonSqlTypeDescriptor() {
    }

    public JsonSqlTypeDescriptor(Properties properties) {
        this.properties = properties;
    }

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
            DatabaseMetaDataDialectResolutionInfoAdapter metaDataInfo = new DatabaseMetaDataDialectResolutionInfoAdapter(connection.getMetaData());
            dialect = dialectResolver.resolveDialect(metaDataInfo);
            if (dialect instanceof PostgreSQL81Dialect) {
                return JsonBinarySqlTypeDescriptor.INSTANCE;
            } else if (dialect instanceof H2Dialect) {
                return JsonBytesSqlTypeDescriptor.INSTANCE;
            } else if (dialect instanceof Oracle8iDialect) {
                if (properties != null) {
                    DynamicParameterizedType.ParameterType parameterType = ParameterTypeUtils.resolve(properties);
                    if (parameterType != null) {
                        String columnType = ParameterTypeUtils.getColumnType(parameterType);
                        if (!StringUtils.isBlank(columnType)) {
                            if (columnType.equals("json")) {
                                return JsonBytesSqlTypeDescriptor.of(dialect.getClass());
                            } else if (columnType.equals("blob") || columnType.equals("clob")) {
                                return JsonBlobSqlTypeDescriptor.INSTANCE;
                            } else if (columnType.equals("varchar2") || columnType.equals("nvarchar2")) {
                                return JsonStringSqlTypeDescriptor.INSTANCE;
                            }
                        }
                    }
                }
                if (metaDataInfo.getDatabaseMajorVersion() >= 21) {
                    return JsonBytesSqlTypeDescriptor.of(dialect.getClass());
                }
            }
            return JsonStringSqlTypeDescriptor.INSTANCE;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public int getSqlType() {
        return sqlTypeDescriptor != null ?
            sqlTypeDescriptor.getSqlType() :
            super.getSqlType();
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

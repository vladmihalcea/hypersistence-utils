package io.hypersistence.utils.hibernate.type.search.internal;

import io.hypersistence.utils.common.ReflectionUtils;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.*;

public class PostgreSQLTSVectorSqlTypeDescriptor implements JdbcType {

    public static final PostgreSQLTSVectorSqlTypeDescriptor INSTANCE = new PostgreSQLTSVectorSqlTypeDescriptor();

    @Override
    public int getJdbcTypeCode() {
        return Types.OTHER;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaType<X> JavaType) {
        return new BasicBinder<X>(JavaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
                ReflectionUtils.invokeSetter(holder, "type", "tsvector");
                ReflectionUtils.invokeSetter(holder, "value", JavaType.unwrap(value, String.class, options));
                st.setObject(index, holder);
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
                ReflectionUtils.invokeSetter(holder, "type", "tsvector");
                ReflectionUtils.invokeSetter(holder, "value", JavaType.unwrap(value, String.class, options));

                st.setObject(name, holder);
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(final JavaType<X> JavaType) {
        return new BasicExtractor<X>(JavaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return JavaType.wrap(rs.getString(paramIndex), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return JavaType.wrap(statement.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return JavaType.wrap(statement.getString(name), options);
            }
        };
    }
}

package com.vladmihalcea.hibernate.type.search.internal;

import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;
import org.hibernate.type.descriptor.sql.BasicExtractor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

import java.sql.*;

public class PostgreSQLTSVectorSqlTypeDescriptor implements SqlTypeDescriptor {

    public static final PostgreSQLTSVectorSqlTypeDescriptor INSTANCE = new PostgreSQLTSVectorSqlTypeDescriptor();

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }

    @Override
    public boolean canBeRemapped() {
        return false;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicBinder<X>(javaTypeDescriptor, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
                ReflectionUtils.invokeSetter(holder, "type", "tsvector");
                ReflectionUtils.invokeSetter(holder, "value", javaTypeDescriptor.unwrap(value, String.class, options));
                st.setObject(index, holder);
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                Object holder = ReflectionUtils.newInstance("org.postgresql.util.PGobject");
                ReflectionUtils.invokeSetter(holder, "type", "tsvector");
                ReflectionUtils.invokeSetter(holder, "value", javaTypeDescriptor.unwrap(value, String.class, options));

                st.setObject(name, holder);
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicExtractor<X>(javaTypeDescriptor, this) {
            @Override
            protected X doExtract(ResultSet rs, String name, WrapperOptions options) throws SQLException {
                return javaTypeDescriptor.wrap(rs.getString( name ), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return javaTypeDescriptor.wrap(statement.getString(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return javaTypeDescriptor.wrap(statement.getString(name), options);
            }
        };
    }
}

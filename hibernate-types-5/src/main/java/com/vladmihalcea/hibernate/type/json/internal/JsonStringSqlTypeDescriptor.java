package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * @author Vlad Mihalcea
 */
public class JsonStringSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor {

    public static final JsonStringSqlTypeDescriptor INSTANCE = new JsonStringSqlTypeDescriptor();

    @Override
    public <X> ValueBinder<X> getBinder(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicBinder<X>(javaTypeDescriptor, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setString(index, javaTypeDescriptor.unwrap(value, String.class, options));
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                st.setString(name, javaTypeDescriptor.unwrap(value, String.class, options));
            }
        };
    }

    @Override
    protected Object extractJson(ResultSet rs, String name) throws SQLException {
        return rs.getString(name);
    }

    @Override
    protected Object extractJson(CallableStatement statement, int index) throws SQLException {
        return statement.getString(index);
    }

    @Override
    protected Object extractJson(CallableStatement statement, String name) throws SQLException {
        return statement.getString(name);
    }
}

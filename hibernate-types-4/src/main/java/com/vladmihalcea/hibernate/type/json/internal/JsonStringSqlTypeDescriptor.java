package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;

import java.sql.*;

/**
 * @author Vlad Mihalcea
 */
public class JsonStringSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor {

    public static final JsonStringSqlTypeDescriptor INSTANCE = new JsonStringSqlTypeDescriptor();

    @Override
    public int getSqlType() {
        return Types.VARCHAR;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicBinder<X>(javaTypeDescriptor, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setString(index, javaTypeDescriptor.unwrap(value, String.class, options));
            }
        };
    }

    @Override
    protected Object extractJson(ResultSet rs, String name) throws SQLException {
        return rs.getString(name);
    }
}

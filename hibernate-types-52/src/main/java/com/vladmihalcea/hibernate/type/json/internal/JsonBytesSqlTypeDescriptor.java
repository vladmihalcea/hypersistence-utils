package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.sql.*;

/**
 * @author Vlad Mihalcea
 */
public class JsonBytesSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor {

    public static final JsonBytesSqlTypeDescriptor INSTANCE = new JsonBytesSqlTypeDescriptor();

    public static final String CHARSET = "UTF8";

    @Override
    public int getSqlType() {
        return Types.BINARY;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicBinder<X>(javaTypeDescriptor, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setBytes(index, toJsonBytes(javaTypeDescriptor.unwrap(value, String.class, options)));
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                st.setBytes(name, toJsonBytes(javaTypeDescriptor.unwrap(value, String.class, options)));
            }
        };
    }

    @Override
    protected Object extractJson(ResultSet rs, String name) throws SQLException {
        return fromJsonBytes(rs.getBytes(name));
    }

    @Override
    protected Object extractJson(CallableStatement statement, int index) throws SQLException {
        return fromJsonBytes(statement.getBytes(index));
    }

    @Override
    protected Object extractJson(CallableStatement statement, String name) throws SQLException {
        return fromJsonBytes(statement.getBytes(name));
    }

    protected byte[] toJsonBytes(String jsonValue) {
        try {
            return jsonValue.getBytes(CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    protected String fromJsonBytes(byte[] jsonBytes) {
        if (jsonBytes == null) {
            return null;
        }
        try {
            return new String(jsonBytes, CHARSET);
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }
}

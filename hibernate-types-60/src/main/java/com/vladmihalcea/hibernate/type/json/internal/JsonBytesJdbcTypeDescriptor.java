package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;

import java.io.UnsupportedEncodingException;
import java.sql.*;

/**
 * @author Vlad Mihalcea
 */
public class JsonBytesJdbcTypeDescriptor extends AbstractJsonJdbcTypeDescriptor {

    public static final JsonBytesJdbcTypeDescriptor INSTANCE = new JsonBytesJdbcTypeDescriptor();

    public static final String CHARSET = "UTF8";

    @Override
    public int getJdbcTypeCode() {
        return Types.BINARY;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaType<X> JavaType) {
        return new BasicBinder<X>(JavaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setBytes(index, toJsonBytes(JavaType.unwrap(value, String.class, options)));
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                st.setBytes(name, toJsonBytes(JavaType.unwrap(value, String.class, options)));
            }
        };
    }

    @Override
    protected Object extractJson(ResultSet rs, int paramIndex) throws SQLException {
        return fromJsonBytes(rs.getBytes(paramIndex));
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

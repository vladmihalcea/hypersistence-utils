package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.dialect.Database;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;

import java.io.UnsupportedEncodingException;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Vlad Mihalcea
 */
public class JsonBytesSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor {

    public static final JsonBytesSqlTypeDescriptor INSTANCE = new JsonBytesSqlTypeDescriptor();

    private static final Map<Database, JsonBytesSqlTypeDescriptor> INSTANCE_MAP = new HashMap<>();

    static {
        INSTANCE_MAP.put(Database.H2, INSTANCE);
        INSTANCE_MAP.put(Database.ORACLE, new JsonBytesSqlTypeDescriptor(2016));
    }

    public static JsonBytesSqlTypeDescriptor of(Database database) {
        return INSTANCE_MAP.get(database);
    }

    public static final String CHARSET = "UTF8";

    private final int jdbcType;

    public JsonBytesSqlTypeDescriptor() {
        this.jdbcType = Types.BINARY;
    }

    public JsonBytesSqlTypeDescriptor(int jdbcType) {
        this.jdbcType = jdbcType;
    }

    @Override
    public int getSqlType() {
        return jdbcType;
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

package io.hypersistence.utils.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;

import java.sql.*;

/**
 * @author Vlad Mihalcea
 */
public class JsonStringJdbcTypeDescriptor extends AbstractJsonJdbcTypeDescriptor {

    public static final JsonStringJdbcTypeDescriptor INSTANCE = new JsonStringJdbcTypeDescriptor();

    @Override
    public int getJdbcTypeCode() {
        return Types.VARCHAR;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
        return new BasicBinder<X>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setString(index, javaType.unwrap(value, String.class, options));
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                st.setString(name, javaType.unwrap(value, String.class, options));
            }
        };
    }

    @Override
    protected Object extractJson(ResultSet rs, int paramIndex) throws SQLException {
        return rs.getString(paramIndex);
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

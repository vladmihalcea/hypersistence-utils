package io.hypersistence.utils.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;

import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * @author Vlad Mihalcea
 */
public class JsonBinaryJdbcTypeDescriptor extends AbstractJsonJdbcTypeDescriptor {

    public static final JsonBinaryJdbcTypeDescriptor INSTANCE = new JsonBinaryJdbcTypeDescriptor();

    @Override
    public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
        return new BasicBinder<X>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                st.setObject(index, javaType.unwrap(value, String.class, options), getJdbcTypeCode());
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                    throws SQLException {
                st.setObject(name, javaType.unwrap(value, String.class, options), getJdbcTypeCode());
            }
        };
    }
}

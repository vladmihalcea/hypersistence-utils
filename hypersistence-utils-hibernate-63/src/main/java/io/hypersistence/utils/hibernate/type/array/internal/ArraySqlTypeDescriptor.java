package io.hypersistence.utils.hibernate.type.array.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicBinder;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.*;

/**
 * @author Vlad Mihalcea
 */
public class ArraySqlTypeDescriptor implements JdbcType {

    public static final ArraySqlTypeDescriptor INSTANCE = new ArraySqlTypeDescriptor();

    @Override
    public int getJdbcTypeCode() {
        return Types.ARRAY;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
        return new BasicBinder<X>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                AbstractArrayTypeDescriptor<Object> abstractArrayTypeDescriptor = (AbstractArrayTypeDescriptor<Object>) javaType;
                st.setArray(index, st.getConnection().createArrayOf(
                    abstractArrayTypeDescriptor.getSqlArrayType(),
                    abstractArrayTypeDescriptor.unwrap(value, Object[].class, options)
                ));
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                throws SQLException {
                throw new UnsupportedOperationException("Binding by name is not supported!");
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(final JavaType<X> JavaType) {
        return new BasicExtractor<X>(JavaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return JavaType.wrap(rs.getArray(paramIndex), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return JavaType.wrap(statement.getArray(index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return JavaType.wrap(statement.getArray(name), options);
            }
            
            
        };
    }

}

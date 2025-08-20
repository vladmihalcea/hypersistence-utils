package io.hypersistence.utils.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BasicExtractor;
import org.hibernate.type.descriptor.jdbc.JdbcType;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public abstract class AbstractJsonJdbcTypeDescriptor implements JdbcType {

    @Override
    public int getJdbcTypeCode() {
        return Types.OTHER;
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(final JavaType<X> javaType) {
        return new BasicExtractor<X>(javaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return javaType.wrap(extractJson(rs, paramIndex), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return javaType.wrap(extractJson(statement, index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return javaType.wrap(extractJson(statement, name), options);
            }
        };
    }

    protected Object extractJson(ResultSet rs, int paramIndex) throws SQLException {
        return rs.getObject(paramIndex);
    }

    protected Object extractJson(CallableStatement statement, int index) throws SQLException {
        return statement.getObject(index);
    }

    protected Object extractJson(CallableStatement statement, String name) throws SQLException {
        return statement.getObject(name);
    }
}

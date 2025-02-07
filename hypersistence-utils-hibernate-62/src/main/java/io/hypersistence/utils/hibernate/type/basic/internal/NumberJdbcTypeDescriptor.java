package io.hypersistence.utils.hibernate.type.basic.internal;

import io.hypersistence.utils.common.StringUtils;
import io.hypersistence.utils.hibernate.type.util.ParameterTypeUtils;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.*;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.ParameterizedType;

import java.sql.*;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class NumberJdbcTypeDescriptor implements JdbcType, ParameterizedType {

    public static final NumberJdbcTypeDescriptor INSTANCE = new NumberJdbcTypeDescriptor();

    private volatile JdbcType jdbcTypeDescriptor;

    private volatile Properties properties;

    public NumberJdbcTypeDescriptor() {
    }

    public NumberJdbcTypeDescriptor(Properties properties) {
        this.properties = properties;
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaType<X> javaType) {
        return new BasicBinder<X>(javaType, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                sqlTypeDescriptor().getBinder(javaType).bind(
                    st, value, index, options
                );
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                throws SQLException {
                sqlTypeDescriptor().getBinder(javaType).bind(
                    st, value, name, options
                );
            }
        };
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return new BasicExtractor<X>(javaType, this) {
            @Override
            protected X doExtract(ResultSet rs, int paramIndex, WrapperOptions options) throws SQLException {
                return sqlTypeDescriptor().getExtractor(javaType).extract(
                    rs, paramIndex, options
                );
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return sqlTypeDescriptor().getExtractor(javaType).extract(
                    statement, index, options
                );
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return sqlTypeDescriptor().getExtractor(javaType).extract(
                    statement, name, options
                );
            }
        };
    }

    private JdbcType sqlTypeDescriptor() {
        if (jdbcTypeDescriptor == null) {
            jdbcTypeDescriptor = resolveJdbcTypeDescriptor();
        }
        return jdbcTypeDescriptor;
    }

    private JdbcType resolveJdbcTypeDescriptor() {
        DynamicParameterizedType.ParameterType parameterType = ParameterTypeUtils.resolve(properties);
        if (parameterType != null) {
            String columnType = ParameterTypeUtils.getColumnType(parameterType);
            if (!StringUtils.isBlank(columnType)) {
                switch (columnType) {
                    case "tinyint":
                        return TinyIntJdbcType.INSTANCE;
                    case "smallint":
                        return SmallIntJdbcType.INSTANCE;
                }
            }
        }
        return IntegerJdbcType.INSTANCE;
    }

    @Override
    public int getJdbcTypeCode() {
        return jdbcTypeDescriptor != null ?
            jdbcTypeDescriptor.getJdbcTypeCode() :
            Types.INTEGER;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        if (properties == null) {
            properties = parameters;
        } else {
            properties.putAll(parameters);
        }
    }
}

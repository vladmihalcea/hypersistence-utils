package io.hypersistence.utils.hibernate.type.basic.internal;

import io.hypersistence.utils.hibernate.type.util.ParameterTypeUtils;
import io.hypersistence.utils.hibernate.util.StringUtils;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.*;
import org.hibernate.usertype.DynamicParameterizedType;
import org.hibernate.usertype.ParameterizedType;

import java.sql.*;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class NumberSqlTypeDescriptor implements SqlTypeDescriptor, ParameterizedType {

    public static final NumberSqlTypeDescriptor INSTANCE = new NumberSqlTypeDescriptor();

    private volatile SqlTypeDescriptor jdbcTypeDescriptor;

    private volatile Properties properties;

    public NumberSqlTypeDescriptor() {
    }

    public NumberSqlTypeDescriptor(Properties properties) {
        this.properties = properties;
    }

    @Override
    public int getSqlType() {
        return jdbcTypeDescriptor != null ?
            jdbcTypeDescriptor.getSqlType() :
            Types.INTEGER;
    }

    @Override
    public boolean canBeRemapped() {
        return true;
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicExtractor<X>(javaTypeDescriptor, this) {
            @Override
            protected X doExtract(ResultSet rs, String name, WrapperOptions options) throws SQLException {
                return sqlTypeDescriptor().getExtractor(javaTypeDescriptor).extract(
                    rs, name, options
                );
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return sqlTypeDescriptor().getExtractor(javaTypeDescriptor).extract(
                    statement, index, options
                );
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return sqlTypeDescriptor().getExtractor(javaTypeDescriptor).extract(
                    statement, new String[] {name}, options
                );
            }
        };
    }

    @Override
    public <X> ValueBinder<X> getBinder(final JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicBinder<X>(javaTypeDescriptor, this) {
            @Override
            protected void doBind(PreparedStatement st, X value, int index, WrapperOptions options) throws SQLException {
                sqlTypeDescriptor().getBinder(javaTypeDescriptor).bind(
                    st, value, index, options
                );
            }

            @Override
            protected void doBind(CallableStatement st, X value, String name, WrapperOptions options)
                throws SQLException {
                sqlTypeDescriptor().getBinder(javaTypeDescriptor).bind(
                    st, value, name, options
                );
            }
        };
    }

    private SqlTypeDescriptor sqlTypeDescriptor() {
        if (jdbcTypeDescriptor == null) {
            jdbcTypeDescriptor = resolveJdbcTypeDescriptor();
        }
        return jdbcTypeDescriptor;
    }

    private SqlTypeDescriptor resolveJdbcTypeDescriptor() {
        DynamicParameterizedType.ParameterType parameterType = ParameterTypeUtils.resolve(properties);
        if (parameterType != null) {
            String columnType = ParameterTypeUtils.getColumnType(parameterType);
            if (!StringUtils.isBlank(columnType)) {
                switch (columnType) {
                    case "tinyint":
                        return TinyIntTypeDescriptor.INSTANCE;
                    case "smallint":
                        return SmallIntTypeDescriptor.INSTANCE;
                }
            }
        }
        return IntegerTypeDescriptor.INSTANCE;
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

package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.DynamicImmutableType;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.metamodel.model.convert.spi.EnumValueConverter;
import org.hibernate.type.EnumType;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.ObjectJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.ObjectJdbcType;
import org.hibernate.type.spi.TypeConfiguration;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Properties;

/**
 * Maps an {@link Enum} to a PostgreSQL ENUM column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class PostgreSQLEnumType extends DynamicImmutableType<Enum> {

    public static final PostgreSQLEnumType INSTANCE = new PostgreSQLEnumType();

    private EnumType enumType;

    private JdbcType jdbcType;

    public PostgreSQLEnumType() {
        super(Enum.class);
        enumType = new EnumType();
        TypeConfiguration typeConfiguration = new TypeConfiguration();
        enumType.setTypeConfiguration(typeConfiguration);
    }

    @Override
    public void setParameterValues(Properties parameters) {
        enumType.setParameterValues(parameters);
        jdbcType = new ObjectJdbcType(getSqlType());
        ReflectionUtils.setFieldValue(enumType, "jdbcType", jdbcType);

        JavaType javaType = new ObjectJavaType();
        ValueExtractor valueExtractor = jdbcType.getExtractor(javaType);
        ValueBinder valueBinder = jdbcType.getBinder(javaType);

        EnumValueConverter enumValueConverter = enumType.getEnumValueConverter();
        ReflectionUtils.setFieldValue(enumValueConverter, "jdbcType", jdbcType);
        ReflectionUtils.setFieldValue(enumValueConverter, "valueExtractor", valueExtractor);
        ReflectionUtils.setFieldValue(enumValueConverter, "valueBinder", valueBinder);

        ReflectionUtils.setFieldValue(enumType, "jdbcValueExtractor", valueExtractor);
        ReflectionUtils.setFieldValue(enumType, "jdbcValueBinder", valueBinder);
    }

    @Override
    protected Enum get(ResultSet rs, int position, SharedSessionContractImplementor session, Object owner) throws SQLException {
        return enumType.nullSafeGet(rs, position, session, owner);
    }

    @Override
    protected void set(PreparedStatement st, Enum value, int index, SharedSessionContractImplementor session) throws SQLException {
        enumType.nullSafeSet(st, value, index, session);
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }
}

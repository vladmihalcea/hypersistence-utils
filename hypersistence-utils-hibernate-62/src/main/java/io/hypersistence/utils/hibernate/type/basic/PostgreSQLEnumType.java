package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.ImmutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.util.ReflectionUtils;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.EnumType;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.converter.internal.NamedEnumValueConverter;
import org.hibernate.type.descriptor.java.EnumJavaType;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.java.ObjectJavaType;
import org.hibernate.type.descriptor.jdbc.JdbcType;
import org.hibernate.type.descriptor.jdbc.ObjectJdbcType;
import org.hibernate.type.spi.TypeConfiguration;
import org.hibernate.usertype.DynamicParameterizedType;

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
public class PostgreSQLEnumType extends ImmutableDynamicParameterizedType<Enum> {

    public static final PostgreSQLEnumType INSTANCE = new PostgreSQLEnumType();

    private EnumType enumType;

    private JdbcType jdbcType;

    public PostgreSQLEnumType() {
        super(Enum.class);
    }

    @Override
    public void setParameterValues(Properties parameters) {
        jdbcType = new ObjectJdbcType(getSqlType());
        Class enumClass = ReflectionUtils.getClass(parameters.getProperty(DynamicParameterizedType.RETURNED_CLASS));
        TypeConfiguration typeConfiguration = new TypeConfiguration();
        final EnumJavaType enumJavaType = (EnumJavaType) typeConfiguration.getJavaTypeRegistry().getDescriptor(enumClass);
        enumType = new EnumType(
            enumClass,
            new NamedEnumValueConverter(
                enumJavaType,
                jdbcType,
                typeConfiguration.getJavaTypeRegistry().getDescriptor(String.class)
            ),
            typeConfiguration
        );
        ReflectionUtils.setFieldValue(enumType, "jdbcType", jdbcType);

        JavaType javaType = new ObjectJavaType();
        ValueExtractor valueExtractor = jdbcType.getExtractor(javaType);
        ValueBinder valueBinder = jdbcType.getBinder(javaType);
        
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
    public Class<Enum> returnedClass() {
        if (enumType != null) {
            Class enumReturnClass = enumType.returnedClass();
            if (enumReturnClass != null) {
                return enumReturnClass;
            }
        }
        return super.returnedClass();
    }

    @Override
    public int getSqlType() {
        return Types.OTHER;
    }
}

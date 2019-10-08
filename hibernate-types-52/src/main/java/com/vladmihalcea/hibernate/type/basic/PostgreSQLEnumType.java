package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.java.EnumJavaTypeDescriptor;
import org.hibernate.type.descriptor.java.spi.JavaTypeDescriptorRegistry;
import org.hibernate.type.spi.TypeConfiguration;

import java.sql.PreparedStatement;
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
public class PostgreSQLEnumType extends org.hibernate.type.EnumType {

    public static final PostgreSQLEnumType INSTANCE = new PostgreSQLEnumType();

    private final Configuration configuration;

    /**
     * Initialization constructor taking the default {@link Configuration} object.
     */
    public PostgreSQLEnumType() {
        this(Configuration.INSTANCE);
    }

    /**
     * Initialization constructor taking a custom {@link Configuration} object.
     *
     * @param configuration custom {@link Configuration} object.
     */
    public PostgreSQLEnumType(Configuration configuration) {
        this.configuration = configuration;
    }

    /**
     * Initialization constructor taking the {@link Class}.
     *
     * @param enumClass The enum type
     */
    public PostgreSQLEnumType(Class<? extends Enum> enumClass) {
        this();

        setTypeConfiguration(new TypeConfiguration() {
            @Override
            public JavaTypeDescriptorRegistry getJavaTypeDescriptorRegistry() {
                return new JavaTypeDescriptorRegistry(this) {
                    @Override
                    public EnumJavaTypeDescriptor getDescriptor(Class javaType) {
                        return new EnumJavaTypeDescriptor(enumClass);
                    }
                };
            }
        });

        Properties properties = new Properties();
        properties.setProperty("enumClass", enumClass.getName());
        properties.setProperty("useNamed", Boolean.TRUE.toString());
        setParameterValues(properties);
    }

    public void nullSafeSet(
            PreparedStatement st,
            Object value,
            int index,
            SharedSessionContractImplementor session)
            throws HibernateException, SQLException {
        st.setObject(index, value != null ? ((Enum) value).name() : null, Types.OTHER);
    }
}

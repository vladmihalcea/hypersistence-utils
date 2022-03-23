package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;

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

        Class typeConfigurationClass = ReflectionUtils.getClassOrNull("org.hibernate.type.spi.TypeConfiguration");

        if(typeConfigurationClass != null) {
            Object typeConfiguration = ReflectionUtils.newInstance(typeConfigurationClass);

            Class enumJavaTypeDescriptorClass = ReflectionUtils.getClassOrNull("org.hibernate.type.descriptor.java.EnumJavaTypeDescriptor");

            Object enumJavaTypeDescriptor = ReflectionUtils.newInstance(enumJavaTypeDescriptorClass, new Object[] {enumClass}, new Class[]{enumClass.getClass()});

            Object javaTypeDescriptorRegistry = ReflectionUtils.invokeGetter(typeConfiguration, "javaTypeDescriptorRegistry");

            ReflectionUtils.invokeMethod(
                javaTypeDescriptorRegistry,
                ReflectionUtils.getMethod(javaTypeDescriptorRegistry, "addDescriptor", JavaTypeDescriptor.class),
                enumJavaTypeDescriptor
            );

            ReflectionUtils.invokeSetter(this, "typeConfiguration", typeConfiguration);
        }

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

    @Override
    public int[] sqlTypes() {
        return new int[]{Types.VARCHAR};
    }
}

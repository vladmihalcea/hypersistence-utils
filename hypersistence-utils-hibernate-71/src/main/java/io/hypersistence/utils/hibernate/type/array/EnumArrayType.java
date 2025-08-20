package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.array.internal.EnumArrayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.type.util.ParameterizedParameterType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.annotation.Annotation;
import java.util.Properties;

/**
 * Maps an {@code Enum[]} array on a database ARRAY type. Multidimensional arrays are supported as well, as explained in <a href="https://vladmihalcea.com/multidimensional-array-jpa-hibernate/">this article</a>.
 * <p>
 * The {@code SQL_ARRAY_TYPE} parameter is used to define the enum type name in the database.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/map-postgresql-enum-array-jpa-entity-property-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Nazir El-Kayssi
 * @author Vlad Mihalcea
 */
public class EnumArrayType extends AbstractArrayType<Enum[]> {

    public static final EnumArrayType INSTANCE = new EnumArrayType();
    private static final String DEFAULT_TYPE_NAME = "%s_enum_array_type";

    private String name;

    public EnumArrayType() {
        super(new EnumArrayTypeDescriptor());
    }

    public EnumArrayType(Configuration configuration) {
        super(
            new EnumArrayTypeDescriptor(),
            configuration
        );
    }

    public EnumArrayType(Class arrayClass, String sqlArrayType) {
        super(new EnumArrayTypeDescriptor(arrayClass));
        Properties parameters = new Properties();
        parameters.setProperty(SQL_ARRAY_TYPE, sqlArrayType);
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterizedParameterType(arrayClass));
        setParameterValues(parameters);
    }

    public EnumArrayType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return name;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        DynamicParameterizedType.ParameterType parameterType = (ParameterType) parameters.get(DynamicParameterizedType.PARAMETER_TYPE);
        Annotation[] annotations = parameterType.getAnnotationsMethod();
        if (name == null) {
            name = String.format(DEFAULT_TYPE_NAME, parameters.getProperty(SQL_ARRAY_TYPE));
        }
        super.setParameterValues(parameters);
    }

}
package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.EnumArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.ParameterizedParameterType;
import org.hibernate.annotations.Type;
import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.annotation.Annotation;
import java.util.Properties;

/**
 * Maps an {@code Enum[]} array on a database ARRAY type.
 * <p>
 * The {@code SQL_ARRAY_TYPE} parameter is used to define the enum type name in the database.
 *
 * @author Nazir El-Kayssi
 * @author Vlad Mihalcea
 */
public class EnumArrayType extends AbstractArrayType<Enum[]> {

    public static final EnumArrayType INSTANCE = new EnumArrayType();
    private static final String DEFAULT_TYPE_NAME = "%s_enum_array_type";

    private String name;

    public EnumArrayType() {
        super(
            new EnumArrayTypeDescriptor()
        );
    }

    public EnumArrayType(Configuration configuration) {
        super(
            new EnumArrayTypeDescriptor(),
            configuration
        );
    }

    public EnumArrayType(Class<? extends Enum> enumClass, String sqlArrayType) {
        this();
        Properties parameters = new Properties();
        parameters.setProperty(SQL_ARRAY_TYPE, sqlArrayType);
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterizedParameterType(enumClass));
        setParameterValues(parameters);
    }

    public String getName() {
        return name;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        DynamicParameterizedType.ParameterType parameterType = (ParameterType) parameters.get(DynamicParameterizedType.PARAMETER_TYPE);
        Annotation[] annotations = parameterType.getAnnotationsMethod();
        if (annotations != null) {
            for (int i = 0; i < annotations.length; i++) {
                Annotation annotation = annotations[i];
                if (Type.class.isAssignableFrom(annotation.annotationType())) {
                    Type type = (Type) annotation;
                    name = type.type();
                    break;
                }
            }
        }
        if (name == null) {
            name = String.format(DEFAULT_TYPE_NAME, parameters.getProperty(SQL_ARRAY_TYPE));
        }
        ((EnumArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }

}
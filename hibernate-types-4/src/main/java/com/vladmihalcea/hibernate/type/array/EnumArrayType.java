package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.ArraySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.EnumArrayTypeDescriptor;
import org.hibernate.annotations.Type;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
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
public class EnumArrayType
        extends AbstractSingleColumnStandardBasicType<Enum[]>
        implements DynamicParameterizedType {

    public static final EnumArrayType INSTANCE = new EnumArrayType();
    public static final String SQL_ARRAY_TYPE = "sql_array_type";
    private static final String DEFAULT_TYPE_NAME = "%s_enum_array_type";

    private String name;

    public EnumArrayType() {
        super(ArraySqlTypeDescriptor.INSTANCE, new EnumArrayTypeDescriptor());
    }

    public String getName() {
        return name;
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
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
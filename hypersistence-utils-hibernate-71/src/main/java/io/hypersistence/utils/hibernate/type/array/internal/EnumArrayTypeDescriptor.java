package io.hypersistence.utils.hibernate.type.array.internal;

import java.util.Properties;

/**
 * @author Nazir El-Kayssi
 * @author Vlad Mihalcea
 */
public class EnumArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<Enum[]> {

    private String sqlArrayType;

    public EnumArrayTypeDescriptor() {
        super(Enum[].class);
    }

    public EnumArrayTypeDescriptor(Class enumClass) {
        super(enumClass);
    }

    @Override
    protected String getSqlArrayType() {
        return sqlArrayType;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        sqlArrayType = parameters.getProperty(AbstractArrayType.SQL_ARRAY_TYPE);
        super.setParameterValues(parameters);
    }
}

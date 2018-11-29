package com.vladmihalcea.hibernate.type.array.internal;

import java.util.Properties;

import com.vladmihalcea.hibernate.type.array.EnumArrayType;

/**
 * @author Nazir El-Kayssi
 * @author Vlad Mihalcea
 */
public class EnumArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<Enum[]> {

    public static final EnumArrayTypeDescriptor INSTANCE = new EnumArrayTypeDescriptor();
    
    private String sqlArrayType;

    public EnumArrayTypeDescriptor() {
        super(Enum[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return sqlArrayType;
    }
    
    @Override
    public void setParameterValues(Properties parameters) {
      sqlArrayType = parameters.getProperty(EnumArrayType.SQL_ARRAY_TYPE);
      super.setParameterValues(parameters);
    }
}

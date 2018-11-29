package com.vladmihalcea.hibernate.type.array.internal;

import java.util.Properties;

import com.vladmihalcea.hibernate.type.array.EnumArrayType;

/**
 * @author Nazir El-Kayssi
 */
public class EnumArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<Enum[]> {

    public static final EnumArrayTypeDescriptor INSTANCE = new EnumArrayTypeDescriptor();
    
    private String typeDbName;

    public EnumArrayTypeDescriptor() {
        super(Enum[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return typeDbName;
    }
    
    @Override
    public void setParameterValues(Properties parameters) {
      typeDbName = parameters.getProperty(EnumArrayType.TYPE_DB_NAME);
      super.setParameterValues(parameters);
    }
}

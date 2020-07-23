package com.vladmihalcea.hibernate.type.array.internal;

/**
 * TypeDescriptor class for Boolean array
 *
 * @author jeet.choudhary7@gmail.com
 * @version 1.0
 * @since July 18, 2020
 */
public class BooleanArrayTypeDescriptor extends AbstractArrayTypeDescriptor<boolean[]> {

    public BooleanArrayTypeDescriptor() {

        super(boolean[].class);
    }

    @Override
    protected String getSqlArrayType() {
        String sqlArrayType = super.getSqlArrayType();
        return sqlArrayType != null ? sqlArrayType : "boolean";
    }
}
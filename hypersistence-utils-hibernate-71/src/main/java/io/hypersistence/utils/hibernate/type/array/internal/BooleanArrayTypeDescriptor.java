package io.hypersistence.utils.hibernate.type.array.internal;

/**
 * @author jeet.choudhary7@gmail.com
 * @version 2.9.13
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
package com.vladmihalcea.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class String2DArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<String[][]> {

    public String2DArrayTypeDescriptor() {
        super(String[][].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "text";
    }
}

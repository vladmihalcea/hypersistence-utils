package com.vladmihalcea.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class StringArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<String[]> {

    public StringArrayTypeDescriptor() {
        super(String[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "text";
    }
}

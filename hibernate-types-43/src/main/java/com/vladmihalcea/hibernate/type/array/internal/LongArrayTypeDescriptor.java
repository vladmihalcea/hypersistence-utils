package com.vladmihalcea.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class LongArrayTypeDescriptor
		extends AbstractArrayTypeDescriptor<long[]> {

    public static final LongArrayTypeDescriptor INSTANCE = new LongArrayTypeDescriptor();

    public LongArrayTypeDescriptor() {
		super(long[].class);
    }

    @Override
    protected String getSqlArrayType() {
		return "bigint";
    }
}

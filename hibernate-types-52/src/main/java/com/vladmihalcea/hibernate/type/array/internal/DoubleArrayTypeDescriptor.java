package com.vladmihalcea.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class DoubleArrayTypeDescriptor
		extends AbstractArrayTypeDescriptor<double[]> {

    public DoubleArrayTypeDescriptor() {
		super(double[].class);
    }

    @Override
    protected String getSqlArrayType() {
		return "float8";
    }
}

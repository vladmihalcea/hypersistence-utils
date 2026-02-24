package io.hypersistence.utils.hibernate.type.array.internal;

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
        String sqlArrayType = super.getSqlArrayType();
        return sqlArrayType != null ? sqlArrayType : "float8";
    }
}

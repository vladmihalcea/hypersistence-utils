package io.hypersistence.utils.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 * @author Andreas Eberle
 */
public class FloatArrayTypeDescriptor
		extends AbstractArrayTypeDescriptor<float[]> {

    public FloatArrayTypeDescriptor() {
		super(float[].class);
    }

    @Override
    protected String getSqlArrayType() {
        String sqlArrayType = super.getSqlArrayType();
        return sqlArrayType != null ? sqlArrayType : "float4";
    }
}

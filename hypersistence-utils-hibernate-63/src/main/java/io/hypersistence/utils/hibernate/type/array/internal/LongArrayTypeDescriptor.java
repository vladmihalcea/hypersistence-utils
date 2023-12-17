package io.hypersistence.utils.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class LongArrayTypeDescriptor
		extends AbstractArrayTypeDescriptor<long[]> {

    public LongArrayTypeDescriptor() {
		super(long[].class);
    }

    @Override
    protected String getSqlArrayType() {
		return "bigint";
    }
}

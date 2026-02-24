package io.hypersistence.utils.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class IntArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<int[]> {

    public IntArrayTypeDescriptor() {
        super(int[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "integer";
    }
}

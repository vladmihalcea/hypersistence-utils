package io.hypersistence.utils.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class LocalDateArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<java.time.LocalDate[]> {

    public LocalDateArrayTypeDescriptor() {
        super(java.time.LocalDate[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "date";
    }
}

package io.hypersistence.utils.hibernate.type.array.internal;

/**
 * @author Vlad Mihalcea
 */
public class LocalDateTimeArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<java.time.LocalDateTime[]> {

    public LocalDateTimeArrayTypeDescriptor() {
        super(java.time.LocalDateTime[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "timestamp";
    }
}

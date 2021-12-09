package com.vladmihalcea.hibernate.type.array.internal;

public class LocalDateArrayTypeDescriptor extends AbstractArrayTypeDescriptor<java.time.LocalDate[]>  {


    public LocalDateArrayTypeDescriptor() {
        super(java.time.LocalDate[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "date";
    }
}

package com.vladmihalcea.hibernate.type.array.internal;

import java.util.Date;

/**
 * @author Vlad Mihalcea
 */
public class TimestampArrayTypeDescriptor extends AbstractArrayTypeDescriptor<Date[]> {

    public TimestampArrayTypeDescriptor() {
        super(Date[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "timestamp";
    }
}

package io.hypersistence.utils.hibernate.type.array.internal;

import java.util.Date;

/**
 * @author Guillaume Briand
 */
public class DateArrayTypeDescriptor extends AbstractArrayTypeDescriptor<Date[]> {

    public DateArrayTypeDescriptor() {
        super(Date[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "date";
    }
}

package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.basic.internal.MonthDayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.DateTypeDescriptor;

import java.time.MonthDay;

/**
 * Maps a Java {@link java.time.MonthDay} object to a {@code DATE} column type.
 *
 * @author Mladen Savic (mladensavic94@gmail.com)
 */

public class MonthDayDateType extends AbstractHibernateType<MonthDay> {

    public static final MonthDayDateType INSTANCE = new MonthDayDateType();


    public MonthDayDateType() {
        super(DateTypeDescriptor.INSTANCE, MonthDayTypeDescriptor.INSTANCE);
    }

    public MonthDayDateType(Configuration configuration) {
        super(DateTypeDescriptor.INSTANCE, MonthDayTypeDescriptor.INSTANCE, configuration);
    }

    @Override
    public String getName() {
        return "monthday-date";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}

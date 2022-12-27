package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.basic.internal.MonthDayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.DateTypeDescriptor;
import org.hibernate.type.descriptor.sql.IntegerTypeDescriptor;

import java.time.MonthDay;

/**
 * Maps a Java {@link java.time.MonthDay} object to a {@code INT} column type.
 *
 * @author Mladen Savic (mladensavic94@gmail.com)
 */
public class MonthDayIntegerType extends AbstractHibernateType<MonthDay> {

    public static final MonthDayIntegerType INSTANCE = new MonthDayIntegerType();


    public MonthDayIntegerType() {
        super(IntegerTypeDescriptor.INSTANCE, MonthDayTypeDescriptor.INSTANCE);
    }

    public MonthDayIntegerType(Configuration configuration) {
        super(IntegerTypeDescriptor.INSTANCE, MonthDayTypeDescriptor.INSTANCE, configuration);
    }

    @Override
    public String getName() {
        return "monthday-int";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}

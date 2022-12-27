package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.AbstractHibernateType;
import io.hypersistence.utils.hibernate.type.basic.internal.MonthDayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
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

package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.MutableType;
import com.vladmihalcea.hibernate.type.basic.internal.MonthDayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.jdbc.DateJdbcType;

import java.time.MonthDay;

/**
 * Maps a Java {@link java.time.MonthDay} object to a {@code DATE} column type.
 *
 * @author Mladen Savic (mladensavic94@gmail.com)
 */

public class MonthDayDateType extends MutableType<MonthDay, DateJdbcType, MonthDayTypeDescriptor> {

    public static final MonthDayDateType INSTANCE = new MonthDayDateType();

    public MonthDayDateType() {
        super(MonthDay.class, DateJdbcType.INSTANCE, MonthDayTypeDescriptor.INSTANCE);
    }

    public MonthDayDateType(Configuration configuration) {
        super(MonthDay.class, DateJdbcType.INSTANCE, MonthDayTypeDescriptor.INSTANCE, configuration);
    }

    public MonthDayDateType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "monthday-date";
    }
}

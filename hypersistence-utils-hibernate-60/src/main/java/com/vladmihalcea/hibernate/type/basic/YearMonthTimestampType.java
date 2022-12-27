package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.MutableType;
import com.vladmihalcea.hibernate.type.basic.internal.YearMonthTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.jdbc.TimestampJdbcType;

import java.time.YearMonth;

/**
 * Maps a Java {@link YearMonth} object to a {@code TIMESTAMP} column type.
 * <p>
 *
 * @author Vlad Mihalcea
 */
public class YearMonthTimestampType extends MutableType<YearMonth, TimestampJdbcType, YearMonthTypeDescriptor> {

    public static final YearMonthTimestampType INSTANCE = new YearMonthTimestampType();

    public YearMonthTimestampType() {
        super(
            YearMonth.class,
            TimestampJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE
        );
    }

    public YearMonthTimestampType(Configuration configuration) {
        super(
            YearMonth.class,
            TimestampJdbcType.INSTANCE,
            YearMonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public YearMonthTimestampType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "yearmonth-timestamp";
    }
}
package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.basic.internal.Iso8601MonthMonthTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.IntegerTypeDescriptor;

import java.time.Month;

/**
 * Maps a {@link Month} object type to a {@code INT}  column type
 * which is saved as value from 1 (January) to 12 (December),
 * according to the ISO 8601 standard.
 *
 * @author Martin Panzer
 */
public class Iso8601MonthType extends AbstractHibernateType<Month> {

    public static final Iso8601MonthType INSTANCE = new Iso8601MonthType();

    public Iso8601MonthType() {
        super(
            IntegerTypeDescriptor.INSTANCE,
            Iso8601MonthMonthTypeDescriptor.INSTANCE
        );
    }

    public Iso8601MonthType(Configuration configuration) {
        super(
            IntegerTypeDescriptor.INSTANCE,
            Iso8601MonthMonthTypeDescriptor.INSTANCE,
            configuration
        );
    }

    @Override
    public String getName() {
        return "month";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}
package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.basic.internal.MonthTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.IntegerTypeDescriptor;

import java.time.Month;

/**
 * Maps an {@link Month} object type to a {@code INT}  column type
 * which is saved as value from 1 (January) to 12 (December).
 *
 * @author Martin Panzer
 */
public class MonthType extends AbstractHibernateType<Month> {

    public static final MonthType INSTANCE = new MonthType();

    public MonthType() {
        super(
                IntegerTypeDescriptor.INSTANCE,
                MonthTypeDescriptor.INSTANCE
        );
    }

    public MonthType(Configuration configuration) {
        super(
                IntegerTypeDescriptor.INSTANCE,
                MonthTypeDescriptor.INSTANCE,
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
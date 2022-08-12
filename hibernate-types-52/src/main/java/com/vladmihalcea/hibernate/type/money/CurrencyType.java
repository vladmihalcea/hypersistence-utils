package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.money.internal.CurrencyTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.DateTypeDescriptor;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

import javax.money.CurrencyUnit;

/**
 * Maps a Java {@link CurrencyUnit} object to a {@code VARCHAR} column type.
 *
 * @author Piotr Olaszewski
 */
public class CurrencyType extends AbstractHibernateType<CurrencyUnit> {
    public static final CurrencyType INSTANCE = new CurrencyType();

    public CurrencyType() {
        super(VarcharTypeDescriptor.INSTANCE, CurrencyTypeDescriptor.INSTANCE);
    }

    public CurrencyType(Configuration configuration) {
        super(
                DateTypeDescriptor.INSTANCE,
                CurrencyTypeDescriptor.INSTANCE,
                configuration
        );
    }

    @Override
    public String getName() {
        return "currency";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}

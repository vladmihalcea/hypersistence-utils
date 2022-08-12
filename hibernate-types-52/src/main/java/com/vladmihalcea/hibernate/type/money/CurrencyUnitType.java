package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.money.internal.CurrencyUnitTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

import javax.money.CurrencyUnit;

/**
 * Maps a Java {@link CurrencyUnit} object to a {@code VARCHAR} column type.
 *
 * @author Piotr Olaszewski
 */
public class CurrencyUnitType extends AbstractHibernateType<CurrencyUnit> {
    public static final CurrencyUnitType INSTANCE = new CurrencyUnitType();

    public CurrencyUnitType() {
        super(VarcharTypeDescriptor.INSTANCE, CurrencyUnitTypeDescriptor.INSTANCE);
    }

    public CurrencyUnitType(Configuration configuration) {
        super(
            VarcharTypeDescriptor.INSTANCE,
            CurrencyUnitTypeDescriptor.INSTANCE,
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

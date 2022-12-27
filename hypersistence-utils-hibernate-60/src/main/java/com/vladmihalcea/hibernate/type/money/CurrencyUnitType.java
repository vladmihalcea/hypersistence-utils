package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.type.MutableType;
import com.vladmihalcea.hibernate.type.money.internal.CurrencyUnitTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

import javax.money.CurrencyUnit;

/**
 * Maps a Java {@link CurrencyUnit} object to a {@code VARCHAR} column type.
 *
 * @author Piotr Olaszewski
 */
public class CurrencyUnitType extends MutableType<CurrencyUnit, VarcharJdbcType, CurrencyUnitTypeDescriptor> {
    public CurrencyUnitType() {
        super(CurrencyUnit.class, VarcharJdbcType.INSTANCE, CurrencyUnitTypeDescriptor.INSTANCE);
    }
}

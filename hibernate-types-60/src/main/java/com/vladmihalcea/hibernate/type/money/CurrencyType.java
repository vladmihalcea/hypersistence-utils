package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.type.MutableType;
import com.vladmihalcea.hibernate.type.money.internal.CurrencyTypeDescriptor;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

import javax.money.CurrencyUnit;

/**
 * Maps a Java {@link CurrencyUnit} object to a {@code VARCHAR} column type.
 *
 * @author Piotr Olaszewski
 */
public class CurrencyType extends MutableType<CurrencyUnit, VarcharJdbcType, CurrencyTypeDescriptor> {
    public CurrencyType() {
        super(CurrencyUnit.class, VarcharJdbcType.INSTANCE, CurrencyTypeDescriptor.INSTANCE);
    }
}

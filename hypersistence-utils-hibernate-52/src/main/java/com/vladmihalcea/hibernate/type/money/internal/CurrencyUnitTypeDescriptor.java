package com.vladmihalcea.hibernate.type.money.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;

import javax.money.CurrencyUnit;
import javax.money.Monetary;

/**
 * @author Piotr Olaszewski
 */
public class CurrencyUnitTypeDescriptor extends AbstractTypeDescriptor<CurrencyUnit> {

    public static final CurrencyUnitTypeDescriptor INSTANCE = new CurrencyUnitTypeDescriptor();

    public CurrencyUnitTypeDescriptor() {
        super(CurrencyUnit.class);
    }

    @Override
    public CurrencyUnit fromString(String string) {
        return Monetary.getCurrency(string);
    }

    @Override
    public <X> X unwrap(CurrencyUnit value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }

        if (String.class.isAssignableFrom(type)) {
            return (X) value.getCurrencyCode();
        }

        throw unknownUnwrap(type);
    }

    @Override
    public <X> CurrencyUnit wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }

        if (value instanceof String) {
            return fromString((String) value);
        }

        throw unknownWrap(value.getClass());
    }
}

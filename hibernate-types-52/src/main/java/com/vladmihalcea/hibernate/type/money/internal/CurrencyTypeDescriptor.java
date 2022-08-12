package com.vladmihalcea.hibernate.type.money.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;

import javax.money.CurrencyUnit;

import static javax.money.Monetary.getCurrency;

/**
 * @author Piotr Olaszewski
 */
public class CurrencyTypeDescriptor extends AbstractTypeDescriptor<CurrencyUnit> {
    public static final CurrencyTypeDescriptor INSTANCE = new CurrencyTypeDescriptor();

    public CurrencyTypeDescriptor() {
        super(CurrencyUnit.class);
    }

    @Override
    public CurrencyUnit fromString(String string) {
        return getCurrency(string);
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
            return getCurrency((String) value);
        }

        throw unknownWrap(value.getClass());
    }
}

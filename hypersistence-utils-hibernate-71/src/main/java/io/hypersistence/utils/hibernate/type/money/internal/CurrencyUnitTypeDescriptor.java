package io.hypersistence.utils.hibernate.type.money.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;

import javax.money.CurrencyUnit;

import javax.money.Monetary;

/**
 * @author Piotr Olaszewski
 */
public class CurrencyUnitTypeDescriptor extends AbstractClassJavaType<CurrencyUnit> {

    public static final CurrencyUnitTypeDescriptor INSTANCE = new CurrencyUnitTypeDescriptor();

    public CurrencyUnitTypeDescriptor() {
        super(CurrencyUnit.class);
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
            return Monetary.getCurrency((String) value);
        }

        throw unknownWrap(value.getClass());
    }
}

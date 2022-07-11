package com.vladmihalcea.hibernate.type.money;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.BigDecimalType;
import org.hibernate.type.StringType;
import org.hibernate.type.Type;
import org.hibernate.usertype.CompositeUserType;
import org.javamoney.moneta.Money;

import javax.money.MonetaryAmount;
import java.io.Serializable;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Objects;

import static java.sql.Types.DECIMAL;
import static java.sql.Types.VARCHAR;

/**
 * Maps a {@link MonetaryAmount} object type to composite columns (with amount and with currency).
 *
 * @author Piotr Olaszewski
 */
public class MonetaryAmountType implements CompositeUserType {

    @Override
    public String[] getPropertyNames() {
        return new String[]{"amount", "currency"};
    }

    @Override
    public Type[] getPropertyTypes() {
        return new Type[]{BigDecimalType.INSTANCE, StringType.INSTANCE};
    }

    @Override
    public Object getPropertyValue(Object component, int property) throws HibernateException {
        MonetaryAmount monetaryAmount = (MonetaryAmount) component;
        if (property == 0) {
            return monetaryAmount.getNumber().numberValue(BigDecimal.class);
        } else if (property == 1) {
            return monetaryAmount.getCurrency();
        }

        throw new IllegalArgumentException("Invalid property index " + property + " for class " + component.getClass());
    }

    @Override
    public void setPropertyValue(Object component, int property, Object value) throws HibernateException {
        throw new HibernateException("Call setPropertyValue on immutable type " + component.getClass());
    }

    @Override
    public Class returnedClass() {
        return MonetaryAmount.class;
    }

    @Override
    public boolean equals(Object x, Object y) throws HibernateException {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(Object x) throws HibernateException {
        return x.hashCode();
    }

    @Override
    public Object nullSafeGet(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws HibernateException, SQLException {
        if (rs.wasNull()) {
            return null;
        }

        String amountColumnName = names[0];
        String currencyColumnName = names[1];

        BigDecimal amount = rs.getBigDecimal(amountColumnName);
        String currency = rs.getString(currencyColumnName);

        return Money.of(amount, currency);
    }

    @Override
    public void nullSafeSet(PreparedStatement st, Object value, int amountColumnIndex, SharedSessionContractImplementor session) throws HibernateException, SQLException {
        int currencyColumnIndex = amountColumnIndex + 1;

        if (value == null) {
            st.setNull(amountColumnIndex, DECIMAL);
            st.setNull(currencyColumnIndex, VARCHAR);
        } else {
            MonetaryAmount monetaryAmount = (MonetaryAmount) value;

            BigDecimal amount = monetaryAmount.getNumber().numberValue(BigDecimal.class);
            String currency = monetaryAmount.getCurrency().getCurrencyCode();

            st.setBigDecimal(amountColumnIndex, amount);
            st.setString(currencyColumnIndex, currency);
        }
    }

    @Override
    public Object deepCopy(Object value) throws HibernateException {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(Object value, SharedSessionContractImplementor session) throws HibernateException {
        return (Serializable) value;
    }

    @Override
    public Object assemble(Serializable cached, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        return cached;
    }

    @Override
    public Object replace(Object original, Object target, SharedSessionContractImplementor session, Object owner) throws HibernateException {
        return original;
    }
}

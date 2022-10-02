package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.type.ImmutableCompositeType;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.EntityMode;
import org.hibernate.FetchMode;
import org.hibernate.HibernateException;
import org.hibernate.engine.spi.CascadeStyle;
import org.hibernate.engine.spi.CascadeStyles;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.type.BigDecimalType;
import org.hibernate.type.CompositeType;
import org.hibernate.type.StringType;
import org.hibernate.type.Type;
import org.javamoney.moneta.Money;

import javax.money.MonetaryAmount;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

import static java.sql.Types.DECIMAL;
import static java.sql.Types.VARCHAR;

/**
 * Maps a {@link MonetaryAmount} object type onto two columns (amount and currency).
 *
 * <p>
 * For more details about how to use it,
 * check out <a href="https://vladmihalcea.com/monetaryamount-jpa-hibernate/">this article</a>
 * on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Piotr Olaszewski
 * @author Edgar Asatryan
 */
public class MonetaryAmountType extends ImmutableCompositeType<MonetaryAmount> implements CompositeType {

    public static final MonetaryAmountType INSTANCE = new MonetaryAmountType();

    public MonetaryAmountType() {
        super(MonetaryAmount.class);
    }

    public MonetaryAmountType(Configuration configuration) {
        super(MonetaryAmount.class, configuration);
    }

    @Override
    public String[] getPropertyNames() {
        return new String[]{"amount", "currency"};
    }

    @Override
    public boolean isComponentType() {
        return true;
    }

    @Override
    public Type[] getSubtypes() {
        return getPropertyTypes();
    }

    @Override
    public boolean[] getPropertyNullability() {
        return new boolean[]{false, false};
    }

    @Override
    public Object[] getPropertyValues(Object component, SharedSessionContractImplementor session) throws HibernateException {
        return new Object[]{getPropertyValue(component, 0), getPropertyValue(component, 1)};
    }

    @Override
    public Object[] getPropertyValues(Object component, EntityMode entityMode) throws HibernateException {
        return new Object[]{getPropertyValue(component, 0), getPropertyValue(component, 1)};
    }

    @Override
    public Object getPropertyValue(Object component, int index, SharedSessionContractImplementor session) throws HibernateException {
        return getPropertyValue(component, index);
    }

    @Override
    public void setPropertyValues(Object component, Object[] values, EntityMode entityMode) throws HibernateException {
        throw new HibernateException("Call setPropertyValues on immutable type " + component.getClass());
    }

    @Override
    public CascadeStyle getCascadeStyle(int index) {
        return CascadeStyles.ALL;
    }

    @Override
    public FetchMode getFetchMode(int index) {
        return FetchMode.JOIN;
    }

    @Override
    public boolean isMethodOf(Method method) {
        return Arrays.asList(MonetaryAmount.class.getMethods()).contains(method);
    }

    @Override
    public boolean isEmbedded() {
        return false;
    }

    @Override
    public boolean hasNotNullProperty() {
        return true;
    }

    @Override
    public int getPropertyIndex(String propertyName) {
        switch (propertyName) {
            case "amount": return 0;
            case "property": return 1;
            default: throw new IllegalArgumentException("Unknown property name: " + propertyName);
        }
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
    protected MonetaryAmount get(ResultSet rs, String[] names, SharedSessionContractImplementor session, Object owner) throws SQLException {
        String amountColumnName = names[0];
        String currencyColumnName = names[1];

        BigDecimal amount = rs.getBigDecimal(amountColumnName);
        if(amount == null) {
            return null;
        }
        String currency = rs.getString(currencyColumnName);
        if(currency == null) {
            return null;
        }

        return Money.of(amount, currency);
    }

    @Override
    protected void set(PreparedStatement st, MonetaryAmount value, int amountColumnIndex, SharedSessionContractImplementor session) throws SQLException {
        int currencyColumnIndex = amountColumnIndex + 1;

        if (value == null) {
            st.setNull(amountColumnIndex, DECIMAL);
            st.setNull(currencyColumnIndex, VARCHAR);
        } else {
            BigDecimal amount = value.getNumber().numberValue(BigDecimal.class);
            String currency = value.getCurrency().getCurrencyCode();

            st.setBigDecimal(amountColumnIndex, amount);
            st.setString(currencyColumnIndex, currency);
        }
    }
}

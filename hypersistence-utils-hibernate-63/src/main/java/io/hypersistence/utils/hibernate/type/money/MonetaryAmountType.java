package io.hypersistence.utils.hibernate.type.money;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.spi.ValueAccess;
import org.hibernate.usertype.CompositeUserType;
import org.javamoney.moneta.Money;

import javax.money.MonetaryAmount;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

/**
 * Maps a {@link MonetaryAmount} object type onto two columns (amount and currency).
 *
 * <p>
 * For more details about how to use it,
 * check out <a href="https://vladmihalcea.com/monetaryamount-jpa-hibernate/">this article</a>
 * on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Piotr Olaszewski
 */
public class MonetaryAmountType implements CompositeUserType<MonetaryAmount>
{
    public static class MonetaryAmountMapper {
        BigDecimal amount;
        String currency;
    }

    public MonetaryAmountType() {
    }

    @Override
    public Object getPropertyValue(MonetaryAmount component, int property) throws HibernateException {
        // alphabetical (amount, currency)
        switch (property) {
            case 0:
                return component.getNumber().numberValue(BigDecimal.class);
            case 1:
                return component.getCurrency().getCurrencyCode();
        }
        return null;
    }

    @Override
    public MonetaryAmount instantiate(ValueAccess values, SessionFactoryImplementor sessionFactory) {
        // alphabetical (amount, currency)
        BigDecimal amount = values.getValue(0, BigDecimal.class);
        String currency = values.getValue(1, String.class);
        return Money.of(amount, currency);
    }

    @Override
    public Class<?> embeddable() {
        return MonetaryAmountMapper.class;
    }

    @Override
    public Class<MonetaryAmount> returnedClass() {
        return MonetaryAmount.class;
    }

    @Override
    public boolean equals(MonetaryAmount x, MonetaryAmount y) {
        return Objects.equals(x, y);
    }

    @Override
    public int hashCode(MonetaryAmount x) {
        return x.hashCode();
    }

    @Override
    public MonetaryAmount deepCopy(MonetaryAmount value) {
        return value;
    }

    @Override
    public boolean isMutable() {
        return false;
    }

    @Override
    public Serializable disassemble(MonetaryAmount value) {
        return (Serializable) value;
    }

    @Override
    public MonetaryAmount assemble(Serializable cached, Object owner) {
        return (MonetaryAmount) cached;
    }

    @Override
    public MonetaryAmount replace(MonetaryAmount detached, MonetaryAmount managed, Object owner) {
        return detached;
    }
}

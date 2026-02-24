package io.hypersistence.utils.hibernate.type.money;

import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.money.CurrencyUnit;

import static javax.money.Monetary.getCurrency;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Piotr Olaszewski
 */
public class MySQLCurrencyUnitTypeTest extends AbstractMySQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                Order.class
        };
    }

    @Test
    public void testPersistAndReadCurrency() {
        Order _order = doInJPA(entityManager -> {
            Order order = new Order();
            order.setCurrency(getCurrency("EUR"));

            entityManager.persist(order);

            return order;
        });

        doInJPA(entityManager -> {
            Order order = entityManager.find(Order.class, _order.getId());

            assertEquals(order.getCurrency(), getCurrency("EUR"));
        });
    }

    @Test
    public void testSearchByCurrency() {
        doInJPA(entityManager -> {
            Order order1 = new Order();
            order1.setCurrency(getCurrency("EUR"));
            entityManager.persist(order1);

            Order order2 = new Order();
            order2.setCurrency(getCurrency("PLN"));
            entityManager.persist(order2);
        });

        doInJPA(entityManager -> {
            CurrencyUnit currency = getCurrency("PLN");
            Order order = entityManager.createQuery("select o from Order o where o.currency = :currency", Order.class)
                    .setParameter("currency", currency)
                    .getSingleResult();

            assertEquals(Long.valueOf(2), order.getId());
        });
    }

    @Test
    public void testReturnNullCurrency() {
        Long _id = doInJPA(entityManager -> {
            Order order = new Order();
            entityManager.persist(order);

            return order.getId();
        });

        doInJPA(entityManager -> {
            Order order = entityManager.createQuery("select o from Order o where o.id = :id", Order.class)
                    .setParameter("id", _id)
                    .getSingleResult();

            assertNull(order.getCurrency());
        });

        doInJPA(entityManager -> {
            CurrencyUnit currency = entityManager.createQuery("select o.currency from Order o where o.id = :id", CurrencyUnit.class)
                    .setParameter("id", _id)
                    .getSingleResult();

            assertNull(currency);
        });
    }

    @Entity(name = "Order")
    @Table(name = "orders")
    public static class Order {
        @Id
        @GeneratedValue
        private Long id;

        @Type(CurrencyUnitType.class)
        @Column(name = "currency", columnDefinition = "char(3)")
        private CurrencyUnit currency;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public CurrencyUnit getCurrency() {
            return currency;
        }

        public void setCurrency(CurrencyUnit currency) {
            this.currency = currency;
        }
    }
}

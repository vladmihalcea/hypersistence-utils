package io.hypersistence.utils.hibernate.type.money;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.CompositeType;
import org.javamoney.moneta.Money;
import org.junit.Test;

import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Piotr Olaszewski
 */
public class PostgreSQLMonetaryAmountTypeTest extends AbstractPostgreSQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                Salary.class
        };
    }

    @Test
    public void testPersistAndReadMoney() {
        Salary _salary = doInJPA(entityManager -> {
            Salary salary = new Salary();
            salary.setSalary(Money.of(new BigDecimal("10.23"), "USD"));

            entityManager.persist(salary);

            return salary;
        });

        doInJPA(entityManager -> {
            Salary salary = entityManager.find(Salary.class, _salary.getId());

            assertEquals(salary.getSalary(), Money.of(new BigDecimal("10.23"), "USD"));
        });
    }

    @Test
    public void testSearchByMoney() {
        doInJPA(entityManager -> {
            Salary salary1 = new Salary();
            salary1.setSalary(Money.of(new BigDecimal("10.23"), "USD"));
            entityManager.persist(salary1);

            Salary salary2 = new Salary();
            salary2.setSalary(Money.of(new BigDecimal("20.23"), "EUR"));
            entityManager.persist(salary2);
        });

        doInJPA(entityManager -> {
            Money money = Money.of(new BigDecimal("10.23"), "USD");
            Salary salary = entityManager.createQuery("select s from Salary s where s.salary = :salary", Salary.class)
                    .setParameter("salary", money)
                    .getSingleResult();

            assertEquals(1, salary.getId());
        });
    }

    @Test
    public void testReturnNullMoney() {
        Long _id = doInJPA(entityManager -> {
            Salary salary = new Salary();
            entityManager.persist(salary);
            return salary.getId();
        });

        doInJPA(entityManager -> {
            Salary salary = entityManager.createQuery("select s from Salary s where s.id = :id", Salary.class)
                    .setParameter("id", _id)
                    .getSingleResult();

            assertNull(salary.getSalary());
        });

        doInJPA(entityManager -> {
            MonetaryAmount money = entityManager.createQuery("select s.salary from Salary s where s.id = :id", MonetaryAmount.class)
                    .setParameter("id", _id)
                    .getSingleResult();

            assertNull(money);
        });
    }

    @Test
    public void testSearchByComponents() {
        doInJPA(entityManager -> {
            Salary salary1 = new Salary();
            salary1.setSalary(Money.of(new BigDecimal("10.23"), "USD"));
            entityManager.persist(salary1);

            Salary salary2 = new Salary();
            salary2.setSalary(Money.of(new BigDecimal("20.23"), "EUR"));
            entityManager.persist(salary2);
        });

        doInJPA(entityManager -> {
            BigDecimal amount = BigDecimal.TEN;
            List<Salary> salaries = entityManager.createQuery("select s from Salary s where s.salary.amount >= :amount", Salary.class)
                .setParameter("amount", amount)
                .getResultList();


            assertEquals(1L, salaries.get(0).getId());
            assertEquals(2L, salaries.get(1).getId());
        });

        doInJPA(entityManager -> {
            String currency = "USD";
            Salary salary = entityManager.createQuery("select s from Salary s where s.salary.currency = :currency", Salary.class)
                .setParameter("currency", currency)
                .getSingleResult();

            assertEquals(1L, salary.getId());
        });
    }

    @Entity(name = "Salary")
    @Table(name = "salary")
    public static class Salary {
        @Id
        @GeneratedValue
        private long id;

        private String other;

        @AttributeOverride(name = "amount", column = @Column(name = "salary_amount"))
        @AttributeOverride(name = "currency", column = @Column(name = "salary_currency"))
        @CompositeType(MonetaryAmountType.class)
        private MonetaryAmount salary;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public MonetaryAmount getSalary() {
            return salary;
        }

        public void setSalary(MonetaryAmount salary) {
            this.salary = salary;
        }

        public String getOther() {
            return other;
        }

        public void setOther(String other) {
            this.other = other;
        }
    }
}

package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.javamoney.moneta.Money;
import org.junit.Test;

import javax.money.MonetaryAmount;
import javax.persistence.*;
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
        Salary _salaryInUSD = doInJPA(entityManager -> {
            Salary salary = new Salary();
            salary.setSalary(Money.of(new BigDecimal("10.23"), "USD"));

            entityManager.persist(salary);

            return salary;
        });

        Salary _salaryInPYG = doInJPA(entityManager -> {
            Salary salary = new Salary();
            salary.setSalary(Money.of(new BigDecimal("123.456"), "PYG"));

            entityManager.persist(salary);

            return salary;
        });

        doInJPA(entityManager -> {
            Salary salaryInUSD = entityManager.find(Salary.class, _salaryInUSD.getId());
            Salary salaryInPYG = entityManager.find(Salary.class, _salaryInPYG.getId());

            assertEquals(salaryInUSD.getSalary(), Money.of(new BigDecimal("10.23"), "USD"));
            assertEquals(salaryInPYG.getSalary(), Money.of(new BigDecimal("123.00"), "PYG"));
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

            assertEquals(1L, salary.getId());
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
    @TypeDef(name = "monetary-amount-currency", typeClass = MonetaryAmountType.class, defaultForType = MonetaryAmount.class)
    public static class Salary {
        @Id
        @GeneratedValue
        private long id;

        private String other;

        @Columns(columns = {
                @Column(name = "salary_amount"),
                @Column(name = "salary_currency")
        })
        @Type(type = "monetary-amount-currency")
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
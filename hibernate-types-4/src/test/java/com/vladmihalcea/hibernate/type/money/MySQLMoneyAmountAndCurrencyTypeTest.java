package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.util.AbstractMySQLIntegrationTest;
import com.vladmihalcea.hibernate.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.javamoney.moneta.Money;
import org.junit.Test;

import javax.money.MonetaryAmount;
import javax.persistence.*;
import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;

/**
 * @author Piotr Olaszewski
 */
public class MySQLMoneyAmountAndCurrencyTypeTest extends AbstractMySQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class[]{
                Salary.class
        };
    }

    @Test
    public void testPersistAndReadMoney() {
        final Salary _salary = doInJPA(new JPATransactionFunction<Salary>() {
            @Override
            public Salary apply(EntityManager entityManager) {
                Salary salary = new Salary();
                salary.setSalary(Money.of(new BigDecimal("10.23"), "USD"));

                entityManager.persist(salary);

                return salary;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Salary salary = entityManager.find(Salary.class, _salary.getId());

                assertEquals(salary.getSalary(), Money.of(new BigDecimal("10.23"), "USD"));

                return null;
            }
        });
    }

    @Test
    public void testSearchByMoney() {
        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Salary salary1 = new Salary();
                salary1.setSalary(Money.of(new BigDecimal("10.23"), "USD"));
                entityManager.persist(salary1);

                Salary salary2 = new Salary();
                salary2.setSalary(Money.of(new BigDecimal("20.23"), "EUR"));
                entityManager.persist(salary2);

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Money money = Money.of(new BigDecimal("10.23"), "USD");
                Salary salary = entityManager.createQuery("select s from Salary s where salary = :salary", Salary.class)
                        .setParameter("salary", money)
                        .getSingleResult();

                assertEquals(Long.valueOf(1), salary.getId());

                return null;
            }
        });
    }

    @Entity(name = "Salary")
    @Table(name = "salary")
    @TypeDef(name = "monetary-amount-currency", typeClass = MoneyAmountAndCurrencyType.class, defaultForType = MonetaryAmount.class)
    public static class Salary {
        @Id
        @GeneratedValue
        private Long id;

        @Columns(columns = {
                @Column(name = "salary_amount"),
                @Column(name = "salary_currency")
        })
        @Type(type = "monetary-amount-currency")
        private MonetaryAmount salary;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public MonetaryAmount getSalary() {
            return salary;
        }

        public void setSalary(MonetaryAmount salary) {
            this.salary = salary;
        }
    }
}

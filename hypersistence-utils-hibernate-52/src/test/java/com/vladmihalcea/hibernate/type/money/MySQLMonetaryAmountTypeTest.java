package com.vladmihalcea.hibernate.type.money;

import com.vladmihalcea.hibernate.util.AbstractMySQLIntegrationTest;
import org.hibernate.annotations.Columns;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.envers.Audited;
import org.javamoney.moneta.Money;
import org.junit.Test;

import javax.money.MonetaryAmount;
import javax.persistence.*;
import java.math.BigDecimal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Piotr Olaszewski
 */
public class MySQLMonetaryAmountTypeTest extends AbstractMySQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class[]{
            Salary.class,
            Bonus.class,
        };
    }

    @Test
    public void testReadAndWrite() {
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

        Salary salary = doInJPA(entityManager -> {
            return entityManager.find(Salary.class, _salary.getId());
        });

        salary.setSalary(Money.of(new BigDecimal("10.49"), "USD"));

        doInJPA(entityManager -> {
            entityManager.merge(salary);
        });
    }

    @Test
    public void testSaveUsingMerge() {
        Salary _salary = doInJPA(entityManager -> {
            Salary salary = new Salary();
            salary.setSalary(Money.of(new BigDecimal("10.23"), "USD"));

            return entityManager.merge(salary);
        });

        Bonus _bonus = doInJPA(entityManager -> {
            Bonus bonus = new Bonus();
            bonus.setId(1L);
            bonus.setBonus(Money.of(new BigDecimal("1.23"), "EUR"));

            return entityManager.merge(bonus);
        });

        doInJPA(entityManager -> {
            Salary salary = entityManager.find(Salary.class, _salary.getId());
            assertEquals(salary.getSalary(), Money.of(new BigDecimal("10.23"), "USD"));

            Bonus bonus = entityManager.find(Bonus.class, 1L);
            assertEquals(bonus.getBonus(), Money.of(new BigDecimal("1.23"), "EUR"));
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

            assertEquals(Long.valueOf(1), salary.getId());
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

    @Entity(name = "Salary")
    @Table(name = "salary")
    @TypeDef(name = "monetary-amount-currency", typeClass = MonetaryAmountType.class, defaultForType = MonetaryAmount.class)
    public static class Salary {
        @Id
        @GeneratedValue
        private Long id;

        private String other;

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

        public String getOther() {
            return other;
        }

        public void setOther(String other) {
            this.other = other;
        }
    }

    @Audited
    @Entity(name = "Bonus")
    @Table(name = "bonus")
    @TypeDef(name = "monetary-amount-currency", typeClass = MonetaryAmountType.class, defaultForType = MonetaryAmount.class)
    public static class Bonus {
        @Id
        private Long id;

        @Columns(columns = {
            @Column(name = "salary_amount"),
            @Column(name = "salary_currency")
        })
        @Type(type = "monetary-amount-currency")
        private MonetaryAmount bonus;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public MonetaryAmount getBonus() {
            return bonus;
        }

        public void setBonus(MonetaryAmount bonus) {
            this.bonus = bonus;
        }
    }
}

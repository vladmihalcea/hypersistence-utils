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

/**
 * @author Nikola Malenic
 */
public class MySQLMonetaryAmountTypeEmbeddedTest extends AbstractPostgreSQLIntegrationTest {
    @Override
    protected Class<?>[] entities() {
        return new Class[]{
            Salary.class
        };
    }

    @Test
    public void testReadAndWrite() {
        Salary _salary = doInJPA(entityManager -> {
            Salary salary = new Salary();
            salary.setEmbeddedSalary(new EmbeddableMonetaryAmount(Money.of(new BigDecimal("10.23"), "USD")));

            entityManager.persist(salary);

            return salary;
        });

        doInJPA(entityManager -> {
            Salary salary = entityManager.find(Salary.class, _salary.getId());

            assertEquals(salary.getEmbeddedSalary().amount, Money.of(new BigDecimal("10.23"), "USD"));
        });

        Salary salary = doInJPA(entityManager -> {
            return entityManager.find(Salary.class, _salary.getId());
        });

        salary.setEmbeddedSalary(new EmbeddableMonetaryAmount(Money.of(new BigDecimal("10.49"), "USD")));

        doInJPA(entityManager -> {
            entityManager.merge(salary);
        });
    }

    @Test
    public void testSearchByMoney() {
        doInJPA(entityManager -> {
            Salary salary1 = new Salary();
            salary1.setEmbeddedSalary(new EmbeddableMonetaryAmount(Money.of(new BigDecimal("10.23"), "USD")));
            entityManager.persist(salary1);

            Salary salary2 = new Salary();
            salary2.setEmbeddedSalary(new EmbeddableMonetaryAmount((Money.of(new BigDecimal("20.23"), "EUR"))));
            entityManager.persist(salary2);
        });

        doInJPA(entityManager -> {
            Money money = Money.of(new BigDecimal("10.23"), "USD");
            Salary salary = entityManager.createQuery("select s from Salary s where s" +
                                                      ".embeddedSalary.amount = :amount", Salary.class)
                .setParameter("amount", money)
                .getSingleResult();

            assertEquals(1, salary.getId());
        });
    }

    @Test
    public void testSearchByComponents() {
        doInJPA(entityManager -> {
            Salary salary1 = new Salary();
            salary1.setEmbeddedSalary(new EmbeddableMonetaryAmount((Money.of(new BigDecimal("10.23"), "USD"))));
            entityManager.persist(salary1);

            Salary salary2 = new Salary();
            salary2.setEmbeddedSalary(new EmbeddableMonetaryAmount((Money.of(new BigDecimal("20.23"), "EUR"))));
            entityManager.persist(salary2);
        });

        doInJPA(entityManager -> {
            BigDecimal amount = BigDecimal.TEN;
            List<Salary> salaries = entityManager.createQuery("select s from Salary s where s.embeddedSalary.amount.amount >= :amount", Salary.class)
                .setParameter("amount", amount)
                .getResultList();


            assertEquals(1L, salaries.get(0).getId());
            assertEquals(2L, salaries.get(1).getId());
        });

        doInJPA(entityManager -> {
            String currency = "USD";
            Salary salary = entityManager.createQuery("select s from Salary s where s.embeddedSalary.amount.currency = :currency", Salary.class)
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

        @Embedded
        private EmbeddableMonetaryAmount embeddedSalary;

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public EmbeddableMonetaryAmount getEmbeddedSalary() {
            return embeddedSalary;
        }

        public void setEmbeddedSalary(EmbeddableMonetaryAmount embeddedSalary) {
            this.embeddedSalary = embeddedSalary;
        }

        public String getOther() {
            return other;
        }

        public void setOther(String other) {
            this.other = other;
        }
    }

    @Embeddable
    public static class EmbeddableMonetaryAmount {
        @AttributeOverride(name = "amount", column = @Column(name = "salary_amount"))
        @AttributeOverride(name = "currency", column = @Column(name = "salary_currency"))
        @CompositeType(MonetaryAmountType.class)
        private MonetaryAmount amount;

        public EmbeddableMonetaryAmount(MonetaryAmount amount) {
            this.amount = amount;
        }

        public EmbeddableMonetaryAmount() {

        }


        public MonetaryAmount getAmount() {
            return amount;
        }

        public void setAmount(MonetaryAmount amount) {
            this.amount = amount;
        }
    }
}

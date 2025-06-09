package io.hypersistence.utils.hibernate.type.money;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.CompositeType;
import org.javamoney.moneta.Money;
import org.junit.Test;

import javax.money.MonetaryAmount;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Nikola Malenic
 */
public class PostgreSQLMonetaryAmountTypeElementCollectionTest extends AbstractPostgreSQLIntegrationTest {
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
            salary.getComponents().add(new SalaryComponent(Money.of(new BigDecimal("10.23"), "USD")));

            entityManager.persist(salary);

            return salary;
        });

        doInJPA(entityManager -> {
            Salary salary = entityManager.find(Salary.class, _salary.getId());

            assertEquals(salary.getComponents().get(0).getValue(), Money.of(new BigDecimal("10.23"), "USD"));
        });
    }

    @Test
    public void testSearchByMoneyInElementCollection() {
        doInJPA(entityManager -> {
            Salary salary1 = new Salary();
            salary1.getComponents().add(new SalaryComponent(Money.of(new BigDecimal("10.23"), "USD")));
            salary1.getComponents().add(new SalaryComponent(Money.of(new BigDecimal("20.23"), "USD")));
            entityManager.persist(salary1);

            Salary salary2 = new Salary();
            salary2.getComponents().add(new SalaryComponent(Money.of(new BigDecimal("30.23"), "EUR")));
            entityManager.persist(salary2);
        });

        doInJPA(entityManager -> {
            Money money = Money.of(new BigDecimal("10.23"), "USD");
            Salary salary = entityManager.createQuery("select s from Salary s join s.components sc where sc.value = :salary", Salary.class)
                .setParameter("salary", money)
                .getSingleResult();

            assertEquals(1, salary.getId());
        });
    }

    @Entity(name = "Salary")
    @Table(name = "salary")
    public static class Salary {
        @Id
        @GeneratedValue
        private long id;

        private String other;

        @ElementCollection
        private List<SalaryComponent> components = new ArrayList<>();

        public long getId() {
            return id;
        }

        public void setId(long id) {
            this.id = id;
        }

        public List<SalaryComponent> getComponents() {
            return components;
        }

        public String getOther() {
            return other;
        }

        public void setOther(String other) {
            this.other = other;
        }
    }

    @Embeddable
    public static class SalaryComponent {
        @AttributeOverride(name = "amount", column = @Column(name = "salary_amount"))
        @AttributeOverride(name = "currency", column = @Column(name = "salary_currency"))
        @CompositeType(MonetaryAmountType.class)
        private MonetaryAmount value;

        public SalaryComponent(MonetaryAmount value) {
            this.value = value;
        }

        public SalaryComponent() {

        }

        public MonetaryAmount getValue() {
            return value;
        }

        public void setValue(MonetaryAmount value) {
            this.value = value;
        }
    }
}

package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.time.Month;
import java.time.Year;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class YearAndMonthTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Publisher.class
        };
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Publisher publisher = new Publisher();
            publisher.setName("vladmihalcea.com");
            publisher.setEstYear(Year.of(2013));
            publisher.setSalesMonth(Month.NOVEMBER);

            entityManager.persist(publisher);
        });

        doInJPA(entityManager -> {
            Publisher publisher = entityManager
                .unwrap(Session.class)
                .bySimpleNaturalId(Publisher.class)
                .load("vladmihalcea.com");

            assertEquals(Year.of(2013), publisher.getEstYear());
            assertEquals(Month.NOVEMBER, publisher.getSalesMonth());
        });

        doInJPA(entityManager -> {
            Publisher book = entityManager
                .createQuery(
                    "select p " +
                        "from Publisher p " +
                        "where " +
                        "   p.estYear = :estYear and " +
                        "   p.salesMonth = :salesMonth", Publisher.class)
                .setParameter("estYear", Year.of(2013))
                .setParameter("salesMonth", Month.NOVEMBER)
                .getSingleResult();

            assertEquals("vladmihalcea.com", book.getName());
        });
    }

    @Entity(name = "Publisher")
    @Table(name = "publisher")
    public static class Publisher {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String name;

        @Type(YearType.class)
        @Column(name = "est_year", columnDefinition = "smallint")
        private Year estYear;

        @Column(name = "sales_month", columnDefinition = "smallint")
        @Enumerated
        private Month salesMonth;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public Year getEstYear() {
            return estYear;
        }

        public void setEstYear(Year estYear) {
            this.estYear = estYear;
        }

        public Month getSalesMonth() {
            return salesMonth;
        }

        public void setSalesMonth(Month salesMonth) {
            this.salesMonth = salesMonth;
        }
    }
}

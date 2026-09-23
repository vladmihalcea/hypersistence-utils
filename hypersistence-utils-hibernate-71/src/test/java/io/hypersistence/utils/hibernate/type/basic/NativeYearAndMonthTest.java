package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;
import jakarta.persistence.*;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.junit.Test;

import java.time.Month;
import java.time.Year;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

/**
 * @author Vlad Mihalcea
 */
public class NativeYearAndMonthTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Properties properties() {
        Properties properties = super.properties();
        // Use Hibernate's native Month mapping as well as its native Year mapping.
        properties.put(HibernateTypesContributor.ENABLE_TYPES_CONTRIBUTOR, false);
        return properties;
    }

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Publisher.class,
            SmallIntPublisher.class
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

    @Test
    public void testSmallIntJdbcType() {
        // The default INTEGER binding works above, but cannot replace YearType's SMALLINT binding.
        RuntimeException exception = assertThrows(RuntimeException.class, () ->
            doInJPA(entityManager -> {
                SmallIntPublisher publisher = new SmallIntPublisher();
                publisher.id = 1L;
                publisher.estYear = Year.of(2013);
                entityManager.persist(publisher);
                entityManager.flush();
            })
        );

        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause instanceof HibernateException);
        assertTrue(cause.getMessage(), cause.getMessage().contains("java.time.Year"));
        assertTrue(cause.getMessage(), cause.getMessage().contains("java.lang.Short"));
    }

    @Entity(name = "SmallIntPublisher")
    @Table(name = "smallint_publisher")
    public static class SmallIntPublisher {

        @Id
        private Long id;

        @JdbcTypeCode(SqlTypes.SMALLINT)
        private Year estYear;
    }

    @Entity(name = "Publisher")
    @Table(name = "publisher")
    public static class Publisher {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String name;

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

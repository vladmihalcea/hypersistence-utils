package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.query.NativeQuery;
import org.junit.Ignore;
import org.junit.Test;

import java.time.YearMonth;
import java.util.List;
import java.util.Properties;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLYearMonthEpochTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Book.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(AvailableSettings.STATEMENT_BATCH_SIZE, 50);
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Book book = new Book();
            book.setIsbn("978-9730228236");
            book.setTitle("High-Performance Java Persistence");
            book.setPublishedOn(YearMonth.of(2016, 10));

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Book book = entityManager
                    .unwrap(Session.class)
                    .bySimpleNaturalId(Book.class)
                    .load("978-9730228236");

            assertEquals(YearMonth.of(2016, 10), book.getPublishedOn());
        });

        doInJPA(entityManager -> {
            Book book = entityManager.createQuery(
                "select b " +
                "from Book b " +
                "where " +
                "   b.title = :title and " +
                "   b.publishedOn = :publishedOn", Book.class)
            .setParameter("title", "High-Performance Java Persistence")
            .setParameter("publishedOn", YearMonth.of(2016, 10))
            .getSingleResult();

            assertEquals("978-9730228236", book.getIsbn());
        });
    }

    @Test
    public void testNull() {
        doInJPA(entityManager -> {
            Book book = new Book();
            book.setIsbn("123-456");
            book.setPublishedOn(null);

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Book book = entityManager
                .unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("123-456");

            assertNull(book.getPublishedOn());
        });
    }

    @Test
    @Ignore
    public void testIndexing() {
        doInJPA(entityManager -> {

            YearMonth yearMonth = YearMonth.of(1970, 1);

            for (int i = 0; i < 5000; i++) {
                yearMonth = yearMonth.plusMonths(1);

                Book book = new Book();
                book.setTitle(
                        String.format(
                                "IT industry newsletter - %s edition", yearMonth
                        )
                );
                book.setPublishedOn(yearMonth);

                entityManager.persist(book);
            }
        });

        List<String> executionPlanLines = doInJPA(entityManager -> {
            return entityManager.createNativeQuery(
                "EXPLAIN ANALYZE " +
                "SELECT " +
                "    b.published_on " +
                "FROM " +
                "    book b " +
                "WHERE " +
                "   b.published_on BETWEEN :startYearMonth AND :endYearMonth ")
            .unwrap(NativeQuery.class)
            .setParameter("startYearMonth", YearMonth.of(2010, 12), YearMonthEpochType.INSTANCE)
            .setParameter("endYearMonth", YearMonth.of(2018, 1), YearMonthEpochType.INSTANCE)
            .getResultList();
        });

        LOGGER.info("Execution plan: \n{}", executionPlanLines.stream().collect(Collectors.joining("\n")));
    }

    @Entity(name = "Book")
    @Table(
            name = "book",
            indexes = @Index(
                    name = "idx_book_published_on",
                    columnList = "published_on"
            )
    )
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        private String title;

        @Type(YearMonthEpochType.class)
        @Column(name = "published_on", columnDefinition = "integer")
        private YearMonth publishedOn;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getIsbn() {
            return isbn;
        }

        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public YearMonth getPublishedOn() {
            return publishedOn;
        }

        public void setPublishedOn(YearMonth publishedOn) {
            this.publishedOn = publishedOn;
        }
    }
}

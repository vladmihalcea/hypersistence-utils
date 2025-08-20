package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.time.YearMonth;
import java.util.TimeZone;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Vlad Mihalcea
 */
public class MySQLYearMonthDateTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Book.class
        };
    }

    private TimeZone defaultTimeZone;

    @Override
    protected void afterInit() {
        defaultTimeZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/Athens"));
    }

    @Override
    public void destroy() {
        super.destroy();
        TimeZone.setDefault(defaultTimeZone);
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
            Book book = entityManager
                    .createQuery(
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

    @Entity(name = "Book")
    @Table(name = "book")
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        private String title;

        @Type(YearMonthDateType.class)
        @Column(name = "published_on", columnDefinition = "date")
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

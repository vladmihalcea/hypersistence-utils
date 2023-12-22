package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractOracleIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.junit.Test;

import jakarta.persistence.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * @author Vlad Mihalcea
 */
public class OracleBinaryDoubleTest extends AbstractOracleIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Book book = new Book();
            book.setIsbn("978-9730228236");
            book.setTitle("High-Performance Java Persistence");
            book.setRating(1.234d);

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Book book = entityManager
                .unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            assertEquals(1.234d, book.getRating(), 0.001);
        });
    }

    @Test
    public void testNull() {
        doInJPA(entityManager -> {
            Book book = new Book();
            book.setIsbn("123-456");
            book.setRating(null);

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Book book = entityManager
                .unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("123-456");

            assertNull(book.getRating());
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

        @Column(columnDefinition = "binary_double")
        private Double rating;

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

        public Double getRating() {
            return rating;
        }

        public void setRating(Double rating) {
            this.rating = rating;
        }
    }
}

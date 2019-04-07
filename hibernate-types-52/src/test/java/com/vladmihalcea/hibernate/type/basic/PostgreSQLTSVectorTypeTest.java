package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 * @author Philip Riecks
 */
public class PostgreSQLTSVectorTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
                Book.class
        };
    }

    @Override
    public void afterInit() {
        doInJDBC(connection -> {
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO book (id, isbn, text) VALUES (1, 'a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11', to_tsvector('This book" +
                        " is a journey into Java data access performance tuning. From connection management, to batch" +
                        " updates, fetch sizes and concurrency control mechanisms, it unravels the inner workings of" +
                        " the most common Java data access frameworks'))");
            } catch (SQLException e) {
                fail(e.getMessage());
            }
        });
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Book book = entityManager.find(Book.class, 1L);

            assertTrue(book.getText().contains(":"));
            assertTrue(book.getText().contains("'"));
            assertTrue(book.getText().contains("java"));
            assertTrue(book.getText().contains("size"));
            assertTrue(book.getText().contains("access"));
            assertTrue(book.getText().contains("batch"));
            assertTrue(book.getText().contains("book"));
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @TypeDef(name = "tsvector", typeClass = PostgreSQLTSVectorType.class, defaultForType = String.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        @Type(type = "tsvector")
        @Column(columnDefinition = "tsvector")
        private String text;

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

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}

package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonPojoTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Test
    public void test() {

        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .setProperty(new Property("title", "High-Performance Java Persistence"))
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            Property bookProperties = book.getProperty();
            assertEquals("High-Performance Java Persistence", bookProperties.getValue());
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .createSelectionQuery("SELECT b from Book b WHERE property = :b", Book.class)
                .setParameter(
                    "b",
                    new Property("title", "High-Performance Java Persistence"),
                    new JsonType(Property.class)
                )
                .getSingleResult();

            assertEquals(
                "978-9730228236",
                book.getIsbn()
            );
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        @Column(length = 15)
        private String isbn;

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private Property property;

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public Property getProperty() {
            return property;
        }

        public Book setProperty(Property property) {
            this.property = property;
            return this;
        }
    }

    public static class Property implements Serializable {

        private String key;
        private String value;

        public Property() {
        }

        public Property(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getValue() {
            return value;
        }

        public void setValue(String value) {
            this.value = value;
        }
    }
}
package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonMapTest extends AbstractPostgreSQLIntegrationTest {

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
                    .addProperty("title", "High-Performance Java Persistence")
                    .addProperty("author", "Vlad Mihalcea")
                    .addProperty("publisher", "Amazon")
                    .addProperty("price", "$44.95")
                    .setAdditionalProperties(
                        Map.of(PropertyType.FORMAT, new BookEnumSet(Set.of(FormatType.PAPERBACK)))
                    )
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            Map<String, String> bookProperties = book.getProperties();

            assertEquals(
                "High-Performance Java Persistence",
                bookProperties.get("title")
            );

            assertEquals(
                "Vlad Mihalcea",
                bookProperties.get("author")
            );

            assertEquals(
                FormatType.PAPERBACK,
                book.getAdditionalProperties().get(PropertyType.FORMAT).iterator().next()
            );
        });

        //With explicit type binding
        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .createSelectionQuery("SELECT b from Book b WHERE properties = :b", Book.class)
                .setParameter(
                    "b",
                    Map.of(
                        "title", "High-Performance Java Persistence",
                        "author", "Vlad Mihalcea",
                        "publisher", "Amazon",
                        "price", "$44.95"
                    ),
                    new JsonType(Map.class)
                )
                .getSingleResult();

            assertEquals(
                "978-9730228236",
                book.getIsbn()
            );
        });

        //Without explicit type binding
        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .createSelectionQuery("SELECT b from Book b WHERE properties = :b", Book.class)
                .setParameter(
                    "b",
                    Map.of(
                        "title", "High-Performance Java Persistence",
                        "author", "Vlad Mihalcea",
                        "publisher", "Amazon",
                        "price", "$44.95"
                    )
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

        @Type(JsonType.class)
        @Column(columnDefinition = "jsonb")
        private Map<String, String> properties = new HashMap<>();

        @Type(JsonType.class)
        @Column(name = "additional_properties", columnDefinition = "jsonb")
        private Map<PropertyType, BookEnumSet> additionalProperties;

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public Map<String, String> getProperties() {
            return properties;
        }

        public Book setProperties(Map<String, String> properties) {
            this.properties = properties;
            return this;
        }

        public Book addProperty(String key, String value) {
            properties.put(key, value);
            return this;
        }

        public Map<PropertyType, BookEnumSet> getAdditionalProperties() {
            return additionalProperties;
        }

        public Book setAdditionalProperties(Map<PropertyType, BookEnumSet> additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }
    }

    public enum PropertyType {
        FORMAT
    }

    public enum FormatType {
        EBOOK,
        PAPERBACK
    }

    public static class BookEnumSet extends HashSet<FormatType> {
        public BookEnumSet() {
        }

        public BookEnumSet(Collection<? extends FormatType> c) {
            super(c);
        }
    }
}
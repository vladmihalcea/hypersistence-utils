package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

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
                        Map.of(PropertyType.FORMAT, Set.of(FormatType.PAPERBACK))
                    )
                    .setDocument(
                        new Document(Map.of(
                            "title", "High-Performance Java Persistence",
                            "author", "Vlad Mihalcea"
                        ))
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

            assertEquals(
                "High-Performance Java Persistence",
                book.getDocument().get("title")
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
        @Column(columnDefinition = "jsonb")
        private Document document = new Document();

        @Type(JsonType.class)
        @Column(name = "additional_properties", columnDefinition = "jsonb")
        private Map<PropertyType, Set<FormatType>> additionalProperties;

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

        public Map<PropertyType, Set<FormatType>> getAdditionalProperties() {
            return additionalProperties;
        }

        public Book setAdditionalProperties(Map<PropertyType, Set<FormatType>> additionalProperties) {
            this.additionalProperties = additionalProperties;
            return this;
        }

        public Document getDocument() {
            return document;
        }

        public Book setDocument(Document document) {
            this.document = document;
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

    public static class Document implements Map<String, Object>, Serializable {
        private final LinkedHashMap<String, Object> documentAsMap;

        public Document() {
            documentAsMap = new LinkedHashMap<>();
        }

        public Document(final Map<String, ?> map) {
            documentAsMap = new LinkedHashMap<>(map);
        }

        @Override
        public int size() {
            return documentAsMap.size();
        }

        @Override
        public boolean isEmpty() {
            return documentAsMap.isEmpty();
        }

        @Override
        public boolean containsKey(Object key) {
            return documentAsMap.containsKey(key);
        }

        @Override
        public boolean containsValue(Object value) {
            return documentAsMap.containsValue(value);
        }

        @Override
        public Object get(Object key) {
            return documentAsMap.get(key);
        }

        @Override
        public Object put(String key, Object value) {
            return documentAsMap.put(key, value);
        }

        @Override
        public Object remove(Object key) {
            return documentAsMap.remove(key);
        }

        @Override
        public void putAll(Map<? extends String, ?> m) {
            documentAsMap.putAll(m);
        }

        @Override
        public void clear() {
            documentAsMap.clear();
        }

        @Override
        public Set<String> keySet() {
            return documentAsMap.keySet();
        }

        @Override
        public Collection<Object> values() {
            return documentAsMap.values();
        }

        @Override
        public Set<Entry<String, Object>> entrySet() {
            return documentAsMap.entrySet();
        }
    }
}

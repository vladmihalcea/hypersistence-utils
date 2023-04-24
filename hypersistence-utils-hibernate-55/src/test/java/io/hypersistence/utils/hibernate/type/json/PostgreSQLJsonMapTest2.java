package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;
import org.springframework.util.Assert;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Version;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonMapTest2 extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Test
    public void test() {
        Map<String, Book.BookInventory> bookTypeMap = new HashMap<>();
        bookTypeMap.put("FICTION",
                new Book.BookInventory(new Book.BookInventory.Inventory(10, 1),
                        Collections.singletonMap(501L, new Book.BookInventory.Inventory(1,1))));

        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .addProperty("Publisher 1", bookTypeMap)
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            Map<String, Map<String, Book.BookInventory>>  bookProperties = book.getProperties();

            assertEquals(
                    bookTypeMap,
                    bookProperties.get("Publisher 1")
            );

            assertEquals(Long.valueOf(0), book.getVersion());
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        @Column(length = 15)
        private String isbn;

        @Version
        private Long version;

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Map<String, Map<String, BookInventory>> properties = new HashMap<>();

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public Long getVersion() {
            return version;
        }

        public Map<String, Map<String, BookInventory>> getProperties() {
            return properties;
        }

        public Book setProperties(Map<String, Map<String, BookInventory>> properties) {
            this.properties = properties;
            return this;
        }

        public Book addProperty(String key, Map<String, BookInventory> value) {
            properties.put(key, value);
            return this;
        }

        public record BookInventory(Inventory global, Map<Long, Inventory> info) {

            public BookInventory {
                Assert.notNull(info, "listings must be provided");
            }

            public record Inventory(Integer maxItems, Integer remainingItems) {
            }
        }
    }
}

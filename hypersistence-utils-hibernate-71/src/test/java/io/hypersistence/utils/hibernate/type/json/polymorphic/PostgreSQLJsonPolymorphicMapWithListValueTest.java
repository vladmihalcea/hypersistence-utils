package io.hypersistence.utils.hibernate.type.json.polymorphic;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonPolymorphicMapWithListValueTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Test
    public void tesListOf() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .addCoupon(
                        "PPP",
                        List.of("4.99", "5.99")
                    )
                    .addCoupon(
                        "Black Friday",
                        List.of("2.99", "3.99")
                    )
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            Map<String, List<String>> topics = book.getCoupons();

            assertEquals(2, topics.size());
            List<String> prices = topics.get("PPP");
            assertTrue(prices.contains("4.99"));
            assertTrue(prices.contains("5.99"));

            prices = topics.get("Black Friday");
            assertTrue(prices.contains("2.99"));
            assertTrue(prices.contains("3.99"));
        });
    }

    @Test
    public void tesCollectionsSingletonList() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .addCoupon(
                        "PPP",
                        Collections.singletonList("4.99")
                    )
                    .addCoupon(
                        "Black Friday",
                        Collections.singletonList("2.99")
                    )
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            Map<String, List<String>> topics = book.getCoupons();

            assertEquals(2, topics.size());
            List<String> prices = topics.get("PPP");
            assertTrue(prices.contains("4.99"));

            prices = topics.get("Black Friday");
            assertTrue(prices.contains("2.99"));
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
        private Map<String, List<String>> coupons = new HashMap<>();

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public Map<String, List<String>> getCoupons() {
            return coupons;
        }

        public Book setCoupons(Map<String, List<String>> coupons) {
            this.coupons = coupons;
            return this;
        }

        public Book addCoupon(String code, List<String> topic) {
            coupons.put(code, topic);
            return this;
        }
    }
}
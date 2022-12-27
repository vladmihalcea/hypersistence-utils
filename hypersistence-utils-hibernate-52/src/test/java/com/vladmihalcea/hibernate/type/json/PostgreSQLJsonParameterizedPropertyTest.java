package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.query.NativeQuery;
import org.junit.Test;

import javax.persistence.*;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonParameterizedPropertyTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Override
    protected void afterInit() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .setProperties(
                        "{" +
                        "   \"title\": \"High-Performance Java Persistence\"," +
                        "   \"author\": \"Vlad Mihalcea\"," +
                        "   \"publisher\": \"Amazon\"," +
                        "   \"price\": 44.99" +
                        "}"
                    )
            );
        });
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            QueryCountHolder.clear();

            book.setProperties(
                "{" +
                "   \"title\": \"High-Performance Java Persistence\"," +
                "   \"author\": \"Vlad Mihalcea\"," +
                "   \"publisher\": \"Amazon\"," +
                "   \"price\": 44.99," +
                "   \"url\": \"https://www.amazon.com/High-Performance-Java-Persistence-Vlad-Mihalcea/dp/973022823X/\"" +
                "}"
            );
        });

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getTotal());
        assertEquals(1, queryCount.getUpdate());

        doInJPA(entityManager -> {
            JsonNode properties = (JsonNode) entityManager
                .createNativeQuery(
                    "SELECT " +
                    "  properties AS properties " +
                    "FROM book " +
                    "WHERE " +
                    "  isbn = :isbn")
                .setParameter("isbn", "978-9730228236")
                .unwrap(NativeQuery.class)
                .addScalar("properties", new JsonBinaryType(JsonNode.class))
                .getSingleResult();

            assertEquals("High-Performance Java Persistence", properties.get("title").asText());
        });
    }

    @Test
    public void testNullValue() {
        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            QueryCountHolder.clear();

            book.setProperties(null);
        });

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(1, queryCount.getTotal());
        assertEquals(1, queryCount.getUpdate());

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            assertNull(book.getProperties());
        });
    }

    @Test
    public void testLoad() {
        QueryCountHolder.clear();

        doInJPA(entityManager -> {
            Session session = entityManager.unwrap(Session.class);
            Book book = session
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            assertTrue(book.getProperties().contains("\"price\": 44.99"));
        });

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(2, queryCount.getTotal());
        assertEquals(2, queryCount.getSelect());
        assertEquals(0, queryCount.getUpdate());
    }

    @MappedSuperclass
    @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
    public static class AbstractBook<T> {

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private T properties;

        public T getProperties() {
            return properties;
        }

        public AbstractBook setProperties(T properties) {
            this.properties = properties;
            return this;
        }
    }

    @Entity(name = "Book")
    @Table(name = "book")
    public static class Book extends AbstractBook<String> {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }
    }
}

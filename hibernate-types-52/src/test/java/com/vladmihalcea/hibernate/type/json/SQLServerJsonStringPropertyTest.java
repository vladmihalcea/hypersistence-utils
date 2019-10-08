package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import com.vladmihalcea.hibernate.type.util.AbstractSQLServerIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.query.NativeQuery;
import org.junit.Test;

import javax.persistence.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * @author Vlad Mihalcea
 */
public class SQLServerJsonStringPropertyTest extends AbstractSQLServerIntegrationTest {

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

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            LOGGER.info("Book details: {}", book.getProperties());

            assertTrue(book.getProperties().contains("\"price\": 44.99"));

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
                .addScalar("properties", new JsonStringType(JsonNode.class))
                .getSingleResult();

            assertEquals("High-Performance Java Persistence", properties.get("title").asText());
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @TypeDef(name = "json", typeClass = JsonStringType.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        @Type(type = "json")
        @Column(columnDefinition = "NVARCHAR(1000) CHECK(ISJSON(properties) = 1)")
        private String properties;

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public String getProperties() {
            return properties;
        }

        public Book setProperties(String properties) {
            this.properties = properties;
            return this;
        }
    }
}

package com.vladmihalcea.hibernate.type.jsonp;

import com.vladmihalcea.hibernate.type.jsonp.internal.JsonbUtil;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.json.JsonValue;
import javax.persistence.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 * @author Jan-Willem Gmelig Meyling
 */
public class PostgreSQLJsonNodeBinaryTypeTest extends AbstractPostgreSQLIntegrationTest {

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
            book.setProperties(
                JsonbUtil.toJsonNode(
                    "{" +
                    "   \"title\": \"High-Performance Java Persistence\"," +
                    "   \"author\": \"Vlad Mihalcea\"," +
                    "   \"publisher\": \"Amazon\"," +
                    "   \"price\": 44.99" +
                    "}"
                )
            );

            entityManager.persist(book);
        });

        doInJPA(entityManager -> {
            Session session = entityManager.unwrap(Session.class);
            Book book = session
                    .bySimpleNaturalId(Book.class)
                    .load("978-9730228236");

            LOGGER.info("Book details: {}", book.getProperties());

            assertEquals(expectedPrice(), book.getProperties().asJsonObject().get("price").toString());

            book.setProperties(
                JsonbUtil.toJsonNode(
                    "{" +
                    "   \"title\": \"High-Performance Java Persistence\"," +
                    "   \"author\": \"Vlad Mihalcea\"," +
                    "   \"publisher\": \"Amazon\"," +
                    "   \"price\": 44.99," +
                    "   \"url\": \"https://www.amazon.com/High-Performance-Java-Persistence-Vlad-Mihalcea/dp/973022823X/\"" +
                    "}"
                )
            );
        });
    }

    protected String initialPrice() {
        return "44.99";
    }

    protected String expectedPrice() {
        return "44.99";
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @TypeDef(name = "jsonb-p-value", typeClass = JsonNodeBinaryType.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        @Type(type = "jsonb-p-value")
        @Column(columnDefinition = "jsonb")
        private JsonValue properties;

        public String getIsbn() {
            return isbn;
        }

        public void setIsbn(String isbn) {
            this.isbn = isbn;
        }

        public JsonValue getProperties() {
            return properties;
        }

        public void setProperties(JsonValue properties) {
            this.properties = properties;
        }
    }
}

package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
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

            SQLStatementCountValidator.reset();

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

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertUpdateCount(1);

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

            SQLStatementCountValidator.reset();

            book.setProperties(null);
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertUpdateCount(1);

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            assertNull(book.getProperties());
        });
    }

    @Test
    public void testLoad() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            Session session = entityManager.unwrap(Session.class);
            Book book = session
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            assertTrue(book.getProperties().contains("\"price\": 44.99"));
        });

        SQLStatementCountValidator.assertTotalCount(2);
        SQLStatementCountValidator.assertSelectCount(2);
        SQLStatementCountValidator.assertUpdateCount(0);
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

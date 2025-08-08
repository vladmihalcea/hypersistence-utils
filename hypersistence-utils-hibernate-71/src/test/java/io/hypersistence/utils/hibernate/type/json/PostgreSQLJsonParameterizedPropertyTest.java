package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.JsonNodeUtil;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import jakarta.persistence.*;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.JavaObjectType;
import org.hibernate.type.descriptor.java.JavaType;
import org.junit.Test;

import java.util.Collections;
import java.util.Properties;

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

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) -> {
                    typeContributions.contributeType(new JsonStringType(JsonNode.class));
                }
            ));
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
                .addScalar("properties", JsonNodeUtil.SCALAR_JSON_NODE_TYPE)
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

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertSelectCount(1);
        SQLStatementCountValidator.assertUpdateCount(0);
    }

    @MappedSuperclass
    public static class AbstractBook<T> {

        @Type(JsonBinaryType.class)
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

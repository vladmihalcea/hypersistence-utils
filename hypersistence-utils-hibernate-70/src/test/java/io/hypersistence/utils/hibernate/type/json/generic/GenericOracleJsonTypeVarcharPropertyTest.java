package io.hypersistence.utils.hibernate.type.json.generic;

import com.fasterxml.jackson.databind.JsonNode;
import io.hypersistence.utils.hibernate.type.json.JsonStringType;
import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.hypersistence.utils.hibernate.util.AbstractOracleIntegrationTest;
import io.hypersistence.utils.test.transaction.EntityManagerTransactionFunction;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.hibernate.query.NativeQuery;
import org.junit.Test;

import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
 */
public class GenericOracleJsonTypeVarcharPropertyTest extends AbstractOracleIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Book.class
        };
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
        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
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

                return null;
            }
        });

        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Book book = entityManager
                    .createQuery(
                        "select b " +
                        "from Book b " +
                        "where b.isbn = :isbn", Book.class)
                    .setParameter("isbn", "978-9730228236")
                    .getSingleResult();

                LOGGER.info("Book details: {}", book.getProperties());

                assertTrue(book.getProperties().replaceAll(" ", "").contains("\"price\":44.99"));

                book.setProperties(
                    "{" +
                        "   \"title\": \"High-Performance Java Persistence\"," +
                        "   \"author\": \"Vlad Mihalcea\"," +
                        "   \"publisher\": \"Amazon\"," +
                        "   \"price\": 44.99," +
                        "   \"url\": \"https://www.amazon.com/High-Performance-Java-Persistence-Vlad-Mihalcea/dp/973022823X/\"" +
                        "}"
                );

                return null;
            }
        });

        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                JsonNode properties = (JsonNode) entityManager
                    .createNativeQuery(
                        "SELECT " +
                        "  properties AS properties " +
                        "FROM book " +
                        "WHERE " +
                        "  isbn = :isbn")
                    .setParameter("isbn", "978-9730228236")
                    .unwrap(NativeQuery.class)
                    .addScalar("properties", JsonNode.class)
                    .uniqueResult();

                assertEquals("High-Performance Java Persistence", properties.get("title").asText());

                return null;
            }
        });

        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Book book = entityManager.unwrap(Session.class)
                    .bySimpleNaturalId(Book.class)
                    .load("978-9730228236");

                book.setProperties(null);

                return null;
            }
        });

        doInJPA(new EntityManagerTransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Book book = entityManager.unwrap(Session.class)
                    .bySimpleNaturalId(Book.class)
                    .load("978-9730228236");

                assertNull(book.getProperties());

                return null;
            }
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        @Type(JsonType.class)
        @Column(columnDefinition = "VARCHAR2(1000)")
        @Check(constraints = "properties IS JSON")
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

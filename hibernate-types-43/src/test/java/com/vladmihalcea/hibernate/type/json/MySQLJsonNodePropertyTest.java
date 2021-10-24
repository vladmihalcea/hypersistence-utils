package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.util.AbstractMySQLIntegrationTest;
import com.vladmihalcea.hibernate.util.transaction.JPATransactionFunction;
import net.ttddyy.dsproxy.QueryCount;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Victor Noël
 */
public class MySQLJsonNodePropertyTest extends AbstractMySQLIntegrationTest {

    private final ObjectMapper mapper = newMapper();

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }


    @Override
    protected void afterInit() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                try {
                    entityManager.persist(
                        new Book()
                            .setIsbn("978-9730228236")
                            .setProperties(mapper.readTree("{\"field\": 0.05}"))
                    );
                } catch (Exception e) {
                    throw new IllegalStateException(e);
                }

                return null;
            }
        });
    }

    @Test
    public void test() {
        QueryCountHolder.clear();

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Book book = (Book) entityManager.unwrap(Session.class)
                    .bySimpleNaturalId(Book.class)
                    .load("978-9730228236");
                assertEquals(0.05, book.getProperties().get("field").asDouble(), 0.0);

                return null;
            }
        });

        QueryCount queryCount = QueryCountHolder.getGrandTotal();
        assertEquals(0, queryCount.getUpdate());
    }

    public static class MyJsonType extends JsonType {
        public MyJsonType() {
            super(newMapper());
        }
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @TypeDef(name = "json", typeClass = MyJsonType.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        private String isbn;

        @Type(type = "json")
        @Column(columnDefinition = "json")
        private JsonNode properties;

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public JsonNode getProperties() {
            return properties;
        }

        public Book setProperties(JsonNode properties) {
            this.properties = properties;
            return this;
        }
    }

    private static ObjectMapper newMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS, true);
        return mapper;
    }
}

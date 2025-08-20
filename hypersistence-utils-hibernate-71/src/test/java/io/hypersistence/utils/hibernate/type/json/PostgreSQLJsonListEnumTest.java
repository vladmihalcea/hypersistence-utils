package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonListEnumTest extends AbstractPostgreSQLIntegrationTest {

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
                    .addProperty(PropertyType.BEST_SELLER)
                    .addProperty(PropertyType.FREE_CHAPTER)
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            List<PropertyType> bookProperties = book.getProperties();
            assertEquals(2, bookProperties.size());
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

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private List<PropertyType> propertyTypes = new ArrayList<>();

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public List<PropertyType> getProperties() {
            return propertyTypes;
        }

        public Book setProperties(List<PropertyType> properties) {
            this.propertyTypes = properties;
            return this;
        }

        public Book addProperty(PropertyType propertyType) {
            propertyTypes.add(propertyType);
            return this;
        }
    }

    public enum PropertyType {
        BEST_SELLER,
        FREE_CHAPTER
    }
}
package com.vladmihalcea.hibernate.type.json.typeinfo;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.*;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.vladmihalcea.hibernate.type.json.JsonType;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import static org.junit.Assert.*;

public class PolymorphicOracleJsonTypeInfoTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Book.class
        };
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .addProperty(new PropertyA("bar"))
                    .addProperty(new PropertyB())
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            List<PropertyBase> properties = book.getProperties();
            assertEquals(PropertyA.class, properties.get(0).getClass());
            assertEquals("bar", ((PropertyA) properties.get(0)).getFoo());
            assertEquals(PropertyB.class, properties.get(1).getClass());
        });
    }

    @Entity(name = "Book")
    @Table(name = "book")
    @TypeDef(name = "json", typeClass = JsonType.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        @Column(length = 15)
        private String isbn;

        @Type(type = "json")
        @Column(columnDefinition = "VARCHAR2(1000) CONSTRAINT IS_VALID_JSON CHECK (properties IS JSON)")
        private List<PropertyBase> properties = new ArrayList<>();

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public List<PropertyBase> getProperties() {
            return properties;
        }

        public Book setProperties(List<PropertyBase> properties) {
            this.properties = properties;
            return this;
        }

        public Book addProperty(PropertyBase property) {
            this.properties.add(property);
            return this;
        }
    }

    @SuppressWarnings("ClassReferencesSubclass")
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
    @JsonSubTypes({
            @JsonSubTypes.Type(PropertyA.class),
            @JsonSubTypes.Type(PropertyB.class)
    })
    public interface PropertyBase extends Serializable {}

    @JsonTypeName("a")
    public static class PropertyA implements PropertyBase {
        private final String foo;

        public PropertyA(@JsonProperty("foo") String foo) {
            this.foo = foo;
        }

        public String getFoo() {
            return foo;
        }
    }

    @JsonTypeName("b")
    public static class PropertyB implements PropertyBase { }
}

package com.vladmihalcea.hibernate.type.json.polymorphic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.junit.Test;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonPolymorphicListCustomTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        ObjectMapper objectMapper = new ObjectMapperWrapper().getObjectMapper();
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) ->
                    typeContributions.contributeType(
                        new JsonBinaryType(
                            objectMapper.activateDefaultTypingAsProperty(
                                objectMapper.getPolymorphicTypeValidator(),
                                ObjectMapper.DefaultTyping.OBJECT_AND_NON_CONCRETE,
                                "className"
                            ),
                            ArrayList.class
                        ),
                        "json-polymorphic-list"
                    )
            )
        );
    }

    @Test
    public void test() {

        Timestamp validUntil = Timestamp.valueOf(LocalDateTime.now().plusDays(10));

        doInJPA(entityManager -> {
            entityManager.persist(
                new Book()
                    .setIsbn("978-9730228236")
                    .addTopic(new Post("High-Performance Java Persistence")
                        .setContent("It rocks!")
                    )
                    .addTopic(new Announcement("Black Friday - 50% discount")
                        .setValidUntil(validUntil)
                    )
            );
        });

        doInJPA(entityManager -> {
            Book book = entityManager.unwrap(Session.class)
                .bySimpleNaturalId(Book.class)
                .load("978-9730228236");

            List<Topic> topics = book.getTopics();
            assertEquals(2, topics.size());
            Post post = (Post) topics.get(0);
            assertEquals("It rocks!", post.getContent());
            Announcement announcement = (Announcement) topics.get(1);
            assertEquals(
                validUntil.getTime(),
                announcement.getValidUntil().getTime()
            );
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

        @Type(type = "json-polymorphic-list")
        @Column(columnDefinition = "jsonb")
        private List<Topic> topics = new ArrayList<>();

        public String getIsbn() {
            return isbn;
        }

        public Book setIsbn(String isbn) {
            this.isbn = isbn;
            return this;
        }

        public List<Topic> getTopics() {
            return topics;
        }

        public Book setTopics(List<Topic> topics) {
            this.topics = topics;
            return this;
        }

        public Book addTopic(Topic topic) {
            topics.add(topic);
            return this;
        }
    }

    public static abstract class Topic implements Serializable {

        private String title;

        public Topic() {
        }

        public Topic(String title) {
            this.title = title;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        String getType() {
            return getClass().getSimpleName();
        }
    }

    public static class Post extends Topic {

        private String content;

        public Post() {
        }

        public Post(String title) {
            super(title);
        }

        public String getContent() {
            return content;
        }

        public Post setContent(String content) {
            this.content = content;
            return this;
        }
    }

    public static class Announcement extends Topic {

        private Date validUntil;

        public Announcement() {
        }

        public Announcement(String title) {
            super(title);
        }

        public Date getValidUntil() {
            return validUntil;
        }

        public Announcement setValidUntil(Date validUntil) {
            this.validUntil = validUntil;
            return this;
        }
    }
}
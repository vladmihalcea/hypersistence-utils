package com.vladmihalcea.hibernate.type.json.polymorphic;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.Session;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonPolymorphicListJacksonTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Book.class
        };
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
    @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class)
    public static class Book {

        @Id
        @GeneratedValue
        private Long id;

        @NaturalId
        @Column(length = 15)
        private String isbn;

        @Type(type = "jsonb")
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

    @JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type"
    )
    @JsonSubTypes({
        @JsonSubTypes.Type(
            name = "topic.post",
            value = Post.class
        ),
        @JsonSubTypes.Type(
            name = "topic.announcement",
            value = Announcement.class
        ),
    })
    public abstract static class Topic implements Serializable {

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

        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME)
        public abstract String getType();
    }

    @JsonTypeName(Post.QUALIFIER)
    public static class Post extends Topic {

        public static final String QUALIFIER = "topic.post";

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

        @Override
        public String getType() {
            return Post.QUALIFIER;
        }
    }

    @JsonTypeName("topic.announcement")
    public static class Announcement extends Topic {

        public static final String QUALIFIER = "topic.announcement";

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

        @Override
        public String getType() {
            return QUALIFIER;
        }
    }
}
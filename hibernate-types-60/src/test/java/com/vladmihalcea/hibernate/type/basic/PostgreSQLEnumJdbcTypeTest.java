package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLEnumJdbcTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
        };
    }

    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()) {
                try {
                    statement.executeUpdate(
                        "DROP TYPE post_status_info CASCADE"
                    );
                } catch (SQLException ignore) {
                }
                statement.executeUpdate(
                    "CREATE TYPE post_status_info AS ENUM ('PENDING', 'APPROVED', 'SPAM')"
                );
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Before
    public void setUp() {
        doInJPA(entityManager -> {
            Post post = new Post();
            post.setId(1L);
            post.setTitle("High-Performance Java Persistence");
            post.setStatus(PostStatus.PENDING);
            entityManager.persist(post);
        });
    }

    @Test
    @Ignore("Fails because of https://github.com/vladmihalcea/hibernate-types/issues/532")
    public void test() {
        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertEquals(PostStatus.PENDING, post.getStatus());
        });
    }

    @Test
    @Ignore("Fails because of https://github.com/vladmihalcea/hibernate-types/issues/532")
    public void testTypedParameterValue() {
        doInJPA(entityManager -> {
            entityManager.createQuery("SELECT a FROM Post a WHERE a.status = :paramValue", Post.class)
                .setParameter("paramValue", PostStatus.APPROVED)
                .getResultList();
        });
    }

    public enum PostStatus {
        PENDING,
        APPROVED,
        SPAM;

        @Override
        public String toString() {
            return String.format("The %s enum is mapped to ordinal: %d", name(), ordinal());
        }
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Enumerated(EnumType.STRING)
        @Column(columnDefinition = "post_status_info")
        @JdbcType(PostStatusJdbcType.class)
        private PostStatus status;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public PostStatus getStatus() {
            return status;
        }

        public void setStatus(PostStatus status) {
            this.status = status;
        }
    }

    public static class PostStatusJdbcType extends PostgreSQLEnumJdbcType {
        public PostStatusJdbcType() {
            super("post_status_info");
        }
    }
}

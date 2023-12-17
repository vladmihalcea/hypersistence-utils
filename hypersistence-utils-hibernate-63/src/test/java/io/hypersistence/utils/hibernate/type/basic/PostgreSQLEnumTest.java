package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.usertype.UserType;
import org.junit.Before;
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
public class PostgreSQLEnumTest extends AbstractPostgreSQLIntegrationTest {

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
    public void test() {
        UserType<?> a = new PostgreSQLEnumType();
        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);
            assertEquals(PostStatus.PENDING, post.getStatus());
        });
    }

    @Test
    public void testTypedParameterValue() {
        doInJPA(entityManager -> {
            entityManager.createQuery("SELECT a FROM Post a WHERE a.status = :paramValue", Post.class)
                .setParameter("paramValue", PostStatus.APPROVED)
                .getResultList();
        });
    }

    @Test
    public void testConstructorExpressionWithEnumWrapper() {
        doInJPA(entityManager -> {
            StatusWrapper statusWrapper = entityManager.createQuery(
                "SELECT " +
                "   new io.hypersistence.utils.hibernate.type.basic.PostgreSQLEnumTest$StatusWrapper(p.status) " +
                "FROM Post p " +
                "WHERE p.id = :id"
                , StatusWrapper.class)
            .setParameter("id", 1L)
            .getSingleResult();

            assertEquals(PostStatus.PENDING, statusWrapper.getStatus());
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
        @Type(PostgreSQLEnumType.class)
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

    public static class StatusWrapper {
        private PostStatus status;

        public StatusWrapper(PostStatus status) {
            this.status = status;
        }

        public PostStatus getStatus() {
            return status;
        }
    }
}

package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.jpa.TypedParameterValue;
import org.hibernate.type.CustomType;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.*;
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

    public static final CustomType POST_STATUS_TYPE = new CustomType(PostgreSQLEnumType.INSTANCE);

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Post.class,
        };
    }

    public void init() {
        DataSource dataSource = newDataSource();
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            Statement statement = null;
            try {
                statement = connection.createStatement();
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
            finally {
                if (statement != null) {
                    try {
                        statement.close();
                    } catch (SQLException e) {
                        fail(e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    fail(e.getMessage());
                }
            }
        }
        super.init();
    }

    @Before
    public void setUp() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Post post = new Post();
                post.setId(1L);
                post.setTitle("High-Performance Java Persistence");
                post.setStatus(PostStatus.PENDING);
                entityManager.persist(post);

                return null;
            }
        });
    }

    @Test
    public void test() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Post post = entityManager.find(Post.class, 1L);
                assertEquals(PostStatus.PENDING, post.getStatus());

                return null;
            }
        });
    }

    @Test
    public void testTypedParameterValue() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                entityManager.createQuery("SELECT a FROM Post a WHERE a.status = :paramValue", Post.class)
                        .setParameter("paramValue", new TypedParameterValue(POST_STATUS_TYPE, PostStatus.APPROVED))
                        .getResultList();
                return null;
            }
        });
    }

    public enum PostStatus {
        PENDING,
        APPROVED,
        SPAM
    }

    @Entity(name = "Post")
    @Table(name = "post")
    @TypeDef(name = "pgsql_enum", typeClass = PostgreSQLEnumType.class)
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Enumerated(EnumType.STRING)
        @Column(columnDefinition = "post_status_info")
        @Type(type = "pgsql_enum")
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
}

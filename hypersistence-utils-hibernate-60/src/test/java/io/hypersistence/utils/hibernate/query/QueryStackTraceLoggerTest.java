package io.hypersistence.utils.hibernate.query;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class QueryStackTraceLoggerTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
            PostComment.class
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(
            "hibernate.session_factory.statement_inspector",
            new QueryStackTraceLogger("io.hypersistence.utils.hibernate.query")
        );
    }

    @Test
    public void testJPQL() {
        doInJPA(entityManager -> {
            entityManager.createQuery(
                "select " +
                "   YEAR(p.createdOn) as year, " +
                "   count(p) as postCount " +
                "from " +
                "   Post p " +
                "group by " +
                "   YEAR(p.createdOn)", Tuple.class)
            .getResultList();
        });
    }

    @Test
    public void testCriteriaAPI() {
        doInJPA(entityManager -> {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();

            CriteriaQuery<PostComment> criteria = builder.createQuery(PostComment.class);

            Root<PostComment> postComment = criteria.from(PostComment.class);
            Join<PostComment, Post> post = postComment.join("post");

            criteria.where(
                builder.like(post.get("title"), "%Java%")
            );

            criteria.orderBy(
                builder.asc(postComment.get("id"))
            );

            entityManager.createQuery(criteria).getResultList();
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Column(name = "created_on")
        private LocalDate createdOn;

        public Long getId() {
            return id;
        }

        public Post setId(Long id) {
            this.id = id;
            return this;
        }

        public String getTitle() {
            return title;
        }

        public Post setTitle(String title) {
            this.title = title;
            return this;
        }

        public LocalDate getCreatedOn() {
            return createdOn;
        }

        public Post setCreatedOn(LocalDate createdOn) {
            this.createdOn = createdOn;
            return this;
        }
    }

    @Entity(name = "PostComment")
    @Table(name = "post_comment")
    public static class PostComment {

        @Id
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY)
        private Post post;

        private String review;

        public Long getId() {
            return id;
        }

        public PostComment setId(Long id) {
            this.id = id;
            return this;
        }

        public Post getPost() {
            return post;
        }

        public PostComment setPost(Post post) {
            this.post = post;
            return this;
        }

        public String getReview() {
            return review;
        }

        public PostComment setReview(String review) {
            this.review = review;
            return this;
        }
    }
}

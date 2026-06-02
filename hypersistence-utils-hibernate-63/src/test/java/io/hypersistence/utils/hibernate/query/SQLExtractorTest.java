package io.hypersistence.utils.hibernate.query;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaDelete;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.CriteriaUpdate;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.ParameterExpression;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertNotNull;

/**
 * @author Vlad Mihalcea
 */
public class SQLExtractorTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
            PostComment.class
        };
    }

    @Test
    public void testSelectJPQL() {
        doInJPA(entityManager -> {
            Query jpql = entityManager
                    .createQuery(
                            "select "
                            + "   YEAR(p.createdOn) as year, "
                            + "   count(p) as postCount "
                            + "from "
                            + "   Post p "
                            + "group by "
                            + "   YEAR(p.createdOn)", Tuple.class);

            String sql = SQLExtractor.from(jpql);

            assertNotNull(sql);

            LOGGER.info(
                    "The JPQL query: [\n{}\n]\ngenerates the following SQL query: [\n{}\n]",
                    jpql.unwrap(org.hibernate.query.Query.class).getQueryString(),
                    sql
            );
        });
    }

    @Test
    public void testUpdateJPQL() {
        doInJPA(entityManager -> {
            Query jpql = entityManager.createQuery(
                    "update Post "
                    + "set title = :newTitle "
                    + "where title like :titlePattern"
            );

            jpql.setParameter("newTitle", "Hibernate Tips");
            jpql.setParameter("titlePattern", "%Java%");

            String sql = SQLExtractor.from(jpql);

            assertNotNull(sql);

            LOGGER.info(
                    "The JPQL query: [\n{}\n]\ngenerates the following SQL query: [\n{}\n]",
                    jpql.unwrap(org.hibernate.query.Query.class).getQueryString(),
                    sql
            );
        });
    }

    @Test
    public void testDeleteJPQL() {
        doInJPA(entityManager -> {
            Query jpql = entityManager.createQuery(
                    "delete from PostComment "
                    + "where review like :reviewPattern"
            );

            jpql.setParameter("reviewPattern", "%spam%");

            String sql = SQLExtractor.from(jpql);

            assertNotNull(sql);

            LOGGER.info(
                    "The JPQL query: [\n{}\n]\ngenerates the following SQL query: [\n{}\n]",
                    jpql.unwrap(org.hibernate.query.Query.class).getQueryString(),
                    sql
            );
        });
    }

    @Test
    public void testSelectCriteriaAPI() {
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

            Query criteriaQuery = entityManager.createQuery(criteria);

            String sql = SQLExtractor.from(criteriaQuery);

            assertNotNull(sql);

            LOGGER.info(
                    "The Criteria API query: [\n{}\n]\ngenerates the following SQL query: [\n{}\n]",
                    criteriaQuery.unwrap(org.hibernate.query.Query.class).getQueryString(),
                    sql
            );
        });
    }

    @Test
    public void testUpdateCriteriaAPI() {
        doInJPA(entityManager -> {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();

            CriteriaUpdate<Post> criteria = builder.createCriteriaUpdate(Post.class);
            Root<Post> post = criteria.from(Post.class);

            ParameterExpression<String> newTitle = builder.parameter(String.class, "newTitle");
            ParameterExpression<String> titlePattern = builder.parameter(String.class, "titlePattern");

            Path<String> title = post.get("title");

            criteria
                    .set(title, newTitle)
                    .where(
                            builder.like(title, titlePattern)
                    );

            Query criteriaQuery = entityManager.createQuery(criteria);

            criteriaQuery.setParameter("newTitle", "Hibernate Tips");
            criteriaQuery.setParameter("titlePattern", "%Java%");

            String sql = SQLExtractor.from(criteriaQuery);

            assertNotNull(sql);

            LOGGER.info(
                    "The Criteria API query: [\n{}\n]\ngenerates the following SQL query: [\n{}\n]",
                    criteriaQuery.unwrap(org.hibernate.query.Query.class).getQueryString(),
                    sql
            );
        });
    }

    @Test
    public void testDeleteCriteriaAPI() {
        doInJPA(entityManager -> {
            CriteriaBuilder builder = entityManager.getCriteriaBuilder();

            CriteriaDelete<PostComment> criteria = builder.createCriteriaDelete(PostComment.class);
            Root<PostComment> postComment = criteria.from(PostComment.class);

            ParameterExpression<String> reviewPattern = builder.parameter(String.class, "reviewPattern");

            criteria.where(
                    builder.like(postComment.get("review"), reviewPattern)
            );

            Query criteriaQuery = entityManager.createQuery(criteria);

            criteriaQuery.setParameter("reviewPattern", "%spam%");

            String sql = SQLExtractor.from(criteriaQuery);

            assertNotNull(sql);

            LOGGER.info(
                    "The Criteria API query: [\n{}\n]\ngenerates the following SQL query: [\n{}\n]",
                    criteriaQuery.unwrap(org.hibernate.query.Query.class).getQueryString(),
                    sql
            );
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

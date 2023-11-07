package io.hypersistence.utils.hibernate.query;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Query;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import org.junit.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
    public void testJPQL() {
        doInJPA(entityManager -> {
            Query jpql = entityManager
            .createQuery(
                "select " +
                "   YEAR(p.createdOn) as year, " +
                "   count(p) as postCount " +
                "from " +
                "   Post p " +
                "group by " +
                "   YEAR(p.createdOn)", Tuple.class);

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
    public void testCriteriaAPI() {
        doInJPA(entityManager -> {
            Query criteriaQuery = createTestQuery(entityManager);

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
    public void testCriteriaAPIWithProxy() {
        doInJPA(entityManager -> {
            Query criteriaQuery = createTestQuery(entityManager);
            Query proxiedQuery = proxy(criteriaQuery);

            String sql = SQLExtractor.from(proxiedQuery);

            assertNotNull(sql);

            LOGGER.info(
                "The Criteria API query: [\n{}\n]\ngenerates the following SQL query: [\n{}\n]",
                criteriaQuery.unwrap(org.hibernate.query.Query.class).getQueryString(),
                sql
            );
        });
    }

    private static Query proxy(Query criteriaQuery) {
        return (Query) Proxy.newProxyInstance(Query.class.getClassLoader(), new Class[]{Query.class}, new HibernateLikeInvocationHandler(criteriaQuery));
    }

    private static Query createTestQuery(EntityManager entityManager) {
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

        return entityManager.createQuery(criteria);
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

    private static class HibernateLikeInvocationHandler implements InvocationHandler {
        private final Query target; // has to be named "target" because this is how Hibernate implements it, and the extracting code has to be quite invasive to get the query from the Hibernate proxy

        public HibernateLikeInvocationHandler(Query query) {
            this.target = query;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            return method.invoke(target, args);
        }
    }
}

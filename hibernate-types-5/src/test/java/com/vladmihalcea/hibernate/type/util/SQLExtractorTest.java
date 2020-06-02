package com.vladmihalcea.hibernate.type.util;

import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.junit.Test;

import javax.persistence.*;
import javax.persistence.criteria.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

import static org.junit.Assert.assertEquals;
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
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Query query = entityManager
                .createQuery(
                    "select " +
                    "   YEAR(p.createdOn) as year, " +
                    "   count(p) as postCount " +
                    "from " +
                    "   Post p " +
                    "group by " +
                    "   YEAR(p.createdOn)", Tuple.class);

                String sql = SQLExtractor.from(query);
                assertNotNull(sql);
                LOGGER.info("SQL query: {}", sql);

                return null;
            }
        });
    }

    @Test
    public void testCriteriaAPI() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                CriteriaBuilder builder = entityManager.getCriteriaBuilder();

                CriteriaQuery<PostComment> criteria = builder.createQuery(PostComment.class);

                Root<PostComment> postComment = criteria.from(PostComment.class);
                Join<PostComment, Post> post = postComment.join("post");

                Path<String> postTitle =  post.get("title");

                criteria.where(
                    builder.like(postTitle, "%Java%")
                );

                criteria.orderBy(
                    builder.asc(postComment.get("id"))
                );

                Query query = entityManager.createQuery(criteria);

                String sql = SQLExtractor.from(query);
                assertNotNull(sql);
                LOGGER.info("SQL query: {}", sql);

                return null;
            }
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Temporal(TemporalType.DATE)
        @Column(name = "created_on")
        private Date createdOn;

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

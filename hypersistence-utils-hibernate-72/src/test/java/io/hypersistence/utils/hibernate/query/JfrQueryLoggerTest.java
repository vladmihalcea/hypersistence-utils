package io.hypersistence.utils.hibernate.query;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.invoke.MethodHandles;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

import static org.junit.Assert.assertEquals;

/**
 * @author Philippe Marschall
 */
public class JfrQueryLoggerTest extends AbstractPostgreSQLIntegrationTest {

    private static final Path RECORDING_LOCATION = Path.of(
        "target",
        MethodHandles.lookup().lookupClass().getSimpleName() + ".jfr"
    );

    private Recording recording;

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
            PostComment.class
        };
    }

    @Override
    protected void afterInit() {
        try {
            Files.deleteIfExists(RECORDING_LOCATION);
            recording = new Recording();
            recording.enable(JfrQueryLogger.QueryEvent.class);
            recording.setMaxSize(128L * 1024L);
            recording.setToDisk(true);
            recording.setDestination(RECORDING_LOCATION);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        recording.start();
    }

    private void assertQueryLogged(int expectedCount) {
        recording.close();
        Set<String> queries = new HashSet<>();
        try (RecordingFile recordingFile = new RecordingFile(RECORDING_LOCATION)) {
            while (recordingFile.hasMoreEvents()) {
                RecordedEvent event = recordingFile.readEvent();
                queries.add(event.getString("sql"));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        assertEquals(expectedCount, queries.size());
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(
            "hibernate.session_factory.statement_inspector",
            new JfrQueryLogger()
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
        assertQueryLogged(1);
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
        assertQueryLogged(1);
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

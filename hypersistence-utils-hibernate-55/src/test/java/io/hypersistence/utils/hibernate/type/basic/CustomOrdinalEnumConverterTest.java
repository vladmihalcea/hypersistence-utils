package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import org.junit.Test;

import javax.persistence.*;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class CustomOrdinalEnumConverterTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class
        };
    }

    @Override
    protected void afterInit() {
        executeStatement("ALTER TABLE post DROP COLUMN status");
        executeStatement("ALTER TABLE post ADD COLUMN status NUMERIC(3)");
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Post()
                    .setId(1)
                    .setTitle("To be moderated")
                    .setStatus(PostStatus.REQUIRES_MODERATOR_INTERVENTION)
            );
            entityManager.persist(
                new Post()
                    .setId(2)
                    .setTitle("Pending")
                    .setStatus(PostStatus.PENDING)
            );
            entityManager.persist(
                new Post()
                    .setId(3)
                    .setTitle("Approved")
                    .setStatus(PostStatus.APPROVED)
            );
            entityManager.persist(
                new Post()
                    .setId(4)
                    .setTitle("Spam post")
                    .setStatus(PostStatus.SPAM)
            );
        });

        doInJPA(entityManager -> {
            assertEquals(
                PostStatus.REQUIRES_MODERATOR_INTERVENTION,
                entityManager.find(Post.class, 1).getStatus()
            );
            assertEquals(
                PostStatus.PENDING,
                entityManager.find(Post.class, 2).getStatus()
            );
            assertEquals(
                PostStatus.APPROVED,
                entityManager.find(Post.class, 3).getStatus()
            );
            assertEquals(
                PostStatus.SPAM,
                entityManager.find(Post.class, 4).getStatus()
            );
        });

        doInJPA(entityManager -> {
            List<Tuple> tuples = entityManager.createNativeQuery(
                "select id, title, status " +
                "from post " +
                "order by id", Tuple.class
            ).getResultList();

            Tuple tuple;
            int index = 0;

            tuple = tuples.get(index++);
            assertEquals(index, tuple.get("id"));
            assertEquals(PostStatus.REQUIRES_MODERATOR_INTERVENTION.statusCode, ((Number) tuple.get("status")).intValue());

            tuple = tuples.get(index++);
            assertEquals(index, tuple.get("id"));
            assertEquals(PostStatus.PENDING.statusCode, ((Number) tuple.get("status")).intValue());

            tuple = tuples.get(index++);
            assertEquals(index, tuple.get("id"));
            assertEquals(PostStatus.APPROVED.statusCode, ((Number) tuple.get("status")).intValue());

            tuple = tuples.get(index++);
            assertEquals(index, tuple.get("id"));
            assertEquals(PostStatus.SPAM.statusCode, ((Number) tuple.get("status")).intValue());
        });
    }

    public enum PostStatus {
        PENDING(100),
        APPROVED(10),
        SPAM(50),
        REQUIRES_MODERATOR_INTERVENTION(1);

        private final int statusCode;

        PostStatus(int statusCode) {
            this.statusCode = statusCode;
        }

        public int getStatusCode() {
            return statusCode;
        }
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        private Integer id;

        @Column(length = 250)
        private String title;

        @Column(columnDefinition = "NUMERIC(3)")
        @Convert(converter = PostStatusConverter.class)
        private PostStatus status;

        public Integer getId() {
            return id;
        }

        public Post setId(Integer id) {
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

        public PostStatus getStatus() {
            return status;
        }

        public Post setStatus(PostStatus status) {
            this.status = status;
            return this;
        }
    }

    @Converter
    public static class PostStatusConverter extends CustomOrdinalEnumConverter<PostStatus> {

        public PostStatusConverter() {
            super(PostStatus.class);
        }

        @Override
        public Integer convertToDatabaseColumn(PostStatus enumValue) {
            return enumValue.getStatusCode();
        }
    }
}

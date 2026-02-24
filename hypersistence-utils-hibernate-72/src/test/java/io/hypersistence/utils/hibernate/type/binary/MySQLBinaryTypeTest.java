package io.hypersistence.utils.hibernate.type.binary;

import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import jakarta.persistence.*;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Vlad Mihalcea
 */
public class MySQLBinaryTypeTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
        };
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Post()
                    .setTitle("High-Performance Java Persistence")
                    .setImage(new byte[]{1, 2, 3})
            );
        });
        doInJPA(entityManager -> {
            Post post = entityManager.find(Post.class, 1L);

            assertArrayEquals(
                new byte[]{1, 2, 3},
                post.getImage()
            );
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        @GeneratedValue
        private Long id;

        private String title;

        @Column(columnDefinition = "BINARY(3)")
        private byte[] image;

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

        public byte[] getImage() {
            return image;
        }

        public Post setImage(byte[] image) {
            this.image = image;
            return this;
        }
    }
}
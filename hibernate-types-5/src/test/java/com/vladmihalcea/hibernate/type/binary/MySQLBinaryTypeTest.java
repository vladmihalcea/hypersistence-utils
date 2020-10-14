package com.vladmihalcea.hibernate.type.binary;

import com.vladmihalcea.hibernate.type.util.AbstractMySQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.*;
import java.util.Arrays;

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
        doInJPA(new JPATransactionFunction<Void>() {
                    @Override
                    public Void apply(EntityManager entityManager) {
                        Post post = new Post("First post");
                        post.setImage(new byte[]{1, 2, 3});
                        entityManager.persist(post);
                        return null;
                    }
                }
        );

        doInJPA(new JPATransactionFunction<Void>() {
                    @Override
                    public Void apply(EntityManager entityManager) {
                        Post post = entityManager.find(Post.class, 1L);
                        assertArrayEquals(new byte[]{1, 2, 3}, Arrays.copyOf(post.getImage(), 3));

                        return null;
                    }
                }
        );
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        @GeneratedValue
        private Long id;

        private String title;

        @Type(type = "com.vladmihalcea.hibernate.type.binary.MySQLBinaryType")
        private byte[] image;

        public Post() {
        }

        public Post(String title) {
            this.title = title;
        }

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

        public byte[] getImage() {
            return image;
        }

        public void setImage(byte[] image) {
            this.image = image;
        }
    }
}
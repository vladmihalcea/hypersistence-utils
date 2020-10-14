package com.vladmihalcea.hibernate.type.binary;

import com.vladmihalcea.hibernate.type.util.AbstractMySQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;

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
                entityManager.persist(
                    new Post()
                        .setTitle("High-Performance Java Persistence")
                        .setImage(new byte[]{1, 2, 3})
                );

                return null;
            }
        }
        );

        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
                Post post = entityManager.find(Post.class, 1L);

                assertArrayEquals(
                    new byte[]{1, 2, 3},
                    post.getImage()
                );

                return null;
            }
        }
        );
    }

    @Entity(name = "Post")
    @Table(name = "post")
    @TypeDef(typeClass = MySQLBinaryType.class, defaultForType = byte[].class)
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
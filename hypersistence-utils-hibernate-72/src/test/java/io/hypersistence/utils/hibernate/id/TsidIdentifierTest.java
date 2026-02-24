package io.hypersistence.utils.hibernate.id;

import io.hypersistence.tsid.TSID;
import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.junit.Test;

import java.util.function.Supplier;

public class TsidIdentifierTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Post.class,
            Tag.class
        };
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Post()
                    .setTitle("High-Performance Java Persistence")
            );
            entityManager.flush();
            entityManager.merge(
                new Post()
                    .setTitle("High-Performance Java Persistence")
            );
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id @Tsid
        private Long id;

        private String title;

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

    @Entity(name = "Tag")
    @Table(name = "tag")
    public static class Tag {

        @Id
        @Tsid(CustomTsidSupplier.class)
        private Long id;

        private String name;

        public Long getId() {
            return id;
        }

        public Tag setId(Long id) {
            this.id = id;
            return this;
        }

        public String getName() {
            return name;
        }

        public Tag setName(String name) {
            this.name = name;
            return this;
        }
    }

    public static class CustomTsidSupplier implements Supplier<TSID.Factory> {

        @Override
        public TSID.Factory get() {
            return TSID.Factory.builder()
                .withNodeBits(1)
                .build();
        }
    }
}

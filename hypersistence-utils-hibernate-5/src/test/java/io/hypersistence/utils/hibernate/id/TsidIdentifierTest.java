package io.hypersistence.utils.hibernate.id;

import io.hypersistence.tsid.TSID;
import io.hypersistence.utils.hibernate.util.AbstractTest;
import io.hypersistence.utils.hibernate.util.transaction.JPATransactionVoidFunction;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.junit.Test;

import javax.persistence.*;
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
        doInJPA(new JPATransactionVoidFunction() {
            @Override
            public void accept(EntityManager entityManager) {
                entityManager.persist(
                    new Post()
                        .setTitle("High-Performance Java Persistence")
                );
                entityManager.flush();
                entityManager.merge(
                    new Post()
                        .setTitle("High-Performance Java Persistence")
                );
            }
        });
    }

    @Entity(name = "Post")
    @Table(name = "post")
    public static class Post {

        @Id
        @GeneratedValue(generator = "tsid")
        @GenericGenerator(
            name = "tsid",
            strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator"
        )
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
        @GeneratedValue(generator = "tsid")
        @GenericGenerator(
            name = "tsid",
            strategy = "io.hypersistence.utils.hibernate.id.TsidGenerator",
            parameters = @Parameter(
                name = TsidGenerator.TSID_FACTORY_SUPPLIER_PARAM,
                value = "io.hypersistence.utils.hibernate.id.TsidIdentifierTest$CustomTsidSupplier"
            )
        )
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

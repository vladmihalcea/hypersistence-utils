package com.vladmihalcea.hibernate.type.util.dto;

import com.vladmihalcea.hibernate.type.util.AbstractTest;
import com.vladmihalcea.hibernate.type.util.ClassImportIntegrator;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.integrator.spi.Integrator;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Id;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class DTOProjectionImportRelativePathTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
        };
    }

    @Override
    protected Integrator integrator() {
        return new ClassImportIntegrator(Arrays.asList(PostDTO.class))
            .excludePath("com.vladmihalcea.hibernate.type");
    }

    @Override
    public void afterInit() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Post post = new Post();
                post.setId(1L);
                post.setTitle("High-Performance Java Persistence");
                post.setCreatedBy("Vlad Mihalcea");
                post.setCreatedOn(Timestamp.valueOf("2020-11-02 12:00:00"));
                post.setUpdatedBy("Vlad Mihalcea");
                post.setUpdatedOn(Timestamp.valueOf("2020-11-02 12:00:00"));

                entityManager.persist(post);

                return null;
            }
        });
    }

    @Test
    public void testConstructorExpression() {
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                List<PostDTO> postDTOs = entityManager.createQuery(
                    "select new util.dto.PostDTO(" +
                        "    p.id, " +
                        "    p.title " +
                        ") " +
                        "from Post p " +
                        "where p.createdOn > :fromTimestamp", PostDTO.class)
                    .setParameter(
                        "fromTimestamp",
                        Timestamp.valueOf("2020-01-01 00:00:00")
                    )
                    .getResultList();

                assertEquals(1, postDTOs.size());

                return null;
            }
        });
    }

    @Entity(name = "Post")
    public static class Post {

        @Id
        private Long id;

        private String title;

        @Column(name = "created_on")
        private Timestamp createdOn;

        @Column(name = "created_by")
        private String createdBy;

        @Column(name = "updated_on")
        private Timestamp updatedOn;

        @Column(name = "updated_by")
        private String updatedBy;

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

        public Timestamp getCreatedOn() {
            return createdOn;
        }

        public void setCreatedOn(Timestamp createdOn) {
            this.createdOn = createdOn;
        }

        public String getCreatedBy() {
            return createdBy;
        }

        public void setCreatedBy(String createdBy) {
            this.createdBy = createdBy;
        }

        public Timestamp getUpdatedOn() {
            return updatedOn;
        }

        public void setUpdatedOn(Timestamp updatedOn) {
            this.updatedOn = updatedOn;
        }

        public String getUpdatedBy() {
            return updatedBy;
        }

        public void setUpdatedBy(String updatedBy) {
            this.updatedBy = updatedBy;
        }
    }
}

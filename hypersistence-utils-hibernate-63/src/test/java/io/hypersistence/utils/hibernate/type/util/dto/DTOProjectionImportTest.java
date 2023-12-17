package io.hypersistence.utils.hibernate.type.util.dto;

import io.hypersistence.utils.hibernate.type.util.ClassImportIntegrator;
import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.integrator.spi.Integrator;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class DTOProjectionImportTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Post.class,
        };
    }

    @Override
    protected Integrator integrator() {
        return new ClassImportIntegrator(Arrays.asList(PostDTO.class));
    }

    @Override
    public void afterInit() {
        doInJPA(entityManager -> {
            Post post = new Post();
            post.setId(1L);
            post.setTitle("High-Performance Java Persistence");
            post.setCreatedBy("Vlad Mihalcea");
            post.setCreatedOn(Timestamp.from(
                LocalDateTime.of(2020, 11, 2, 12, 0, 0).toInstant(ZoneOffset.UTC)
            ));
            post.setUpdatedBy("Vlad Mihalcea");
            post.setUpdatedOn(Timestamp.from(
                LocalDateTime.now().toInstant(ZoneOffset.UTC)
            ));

            entityManager.persist(post);
        });
    }

    @Test
    public void testConstructorExpression() {
        doInJPA(entityManager -> {
            List<PostDTO> postDTOs = entityManager.createQuery(
                "select new PostDTO(" +
                "    p.id, " +
                "    p.title " +
                ") " +
                "from Post p " +
                "where p.createdOn > :fromTimestamp", PostDTO.class)
            .setParameter(
                "fromTimestamp",
                Timestamp.from(
                    LocalDate.of(2020, 1, 1)
                        .atStartOfDay()
                        .toInstant(ZoneOffset.UTC)
                )
            )
            .getResultList();

            assertEquals(1, postDTOs.size());
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

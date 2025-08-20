package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Vlad Mihalcea
 */
public class MatrixArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    @Test
    public void testNonEmptyArray() {
        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            entityManager.persist(
                new Event()
                    .setId(1L)
                    .setMatrix(new String[][]{
                        {"A", "B", "C"},
                        {"1", "2", "3"},
                    })
            );
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);
            assertArrayEquals(
                new String[][]{
                    {"A", "B", "C"},
                    {"1", "2", "3"},
                },
                event.getMatrix()
            );
        });
    }

    @Test
    public void testEmptyArray() {
        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            entityManager.persist(
                new Event()
                    .setId(1L)
                    .setMatrix(new String[][]{})
            );
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);
            assertArrayEquals(
                new String[][]{},
                event.getMatrix()
            );
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        private Long id;

        @Type(StringArrayType.class)
        @Column(name = "test_2d_array", columnDefinition = "text[]")
        private String[][] matrix;

        public Long getId() {
            return id;
        }

        public Event setId(Long id) {
            this.id = id;
            return this;
        }

        public String[][] getMatrix() {
            return matrix;
        }

        public Event setMatrix(String[][] matrix) {
            this.matrix = matrix;
            return this;
        }
    }
}

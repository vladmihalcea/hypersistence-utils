package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

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

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL95ArrayDialect.class.getName();
            }
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
    @TypeDefs({
        @TypeDef(
            typeClass = StringArrayType.class,
            defaultForType = String[][].class
        )
    })
    public static class Event {

        @Id
        private Long id;

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

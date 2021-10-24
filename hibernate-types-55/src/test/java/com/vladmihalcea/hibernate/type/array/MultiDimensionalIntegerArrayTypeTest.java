package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.type.LongType;
import org.hibernate.type.StringType;
import org.junit.Test;

import javax.persistence.*;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class MultiDimensionalIntegerArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Plane.class,
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
    public void test() {
        doInJPA(entityManager -> {
            entityManager.persist(
                new Plane()
                    .setId(1L)
                    .setName("ATR-42")
                    .setSeatGrid(
                        new Integer[][] {
                            {1, 1, 1, 1},
                            {0, 0, 2, 0},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {2, 2, 2, 2},
                            {1, 1, 1, 1}
                        }
                    )
            );
        });

        doInJPA(entityManager -> {
            Plane plane = entityManager.find(Plane.class, 1L);

            assertEquals("ATR-42", plane.getName());
            assertEquals(1, plane.getSeatStatus(1, 'A'));
            assertEquals(1, plane.getSeatStatus(1, 'B'));
            assertEquals(1, plane.getSeatStatus(1, 'C'));
            assertEquals(1, plane.getSeatStatus(1, 'D'));
            assertEquals(0, plane.getSeatStatus(2, 'A'));
            assertEquals(0, plane.getSeatStatus(2, 'B'));
            assertEquals(2, plane.getSeatStatus(2, 'C'));
            assertEquals(0, plane.getSeatStatus(2, 'D'));
        });

        doInJPA(entityManager -> {
            List<Tuple> tuples = entityManager
                .createNativeQuery(
                    "SELECT * " +
                    " FROM plane ", Tuple.class)
                .unwrap(org.hibernate.query.NativeQuery.class)
                .addScalar(
                    "seat_grid",
                    new IntArrayType(
                        ReflectionUtils.getField(Plane.class, "seatGrid").getType()
                    )
                )
                .addScalar("name", StringType.INSTANCE)
                .addScalar("id", LongType.INSTANCE)
                .getResultList();

            Tuple plane = tuples.get(0);
            assertEquals("ATR-42", plane.get("name"));
        });
    }

    @Entity(name = "Plane")
    @Table(name = "plane")
    @TypeDef(name = "int-array", typeClass = IntArrayType.class)
    public static class Plane {

        @Id
        private Long id;

        private String name;

        @Type(type = "int-array")
        @Column(name = "seat_grid", columnDefinition = "int[][]")
        private Integer[][] seatGrid;

        public Long getId() {
            return id;
        }

        public Plane setId(Long id) {
            this.id = id;
            return this;
        }

        public String getName() {
            return name;
        }

        public Plane setName(String name) {
            this.name = name;
            return this;
        }

        public Integer[][] getSeatGrid() {
            return seatGrid;
        }

        public Plane setSeatGrid(Integer[][] seatGrid) {
            this.seatGrid = seatGrid;
            return this;
        }

        public int getSeatStatus(int row, char letter) {
            return seatGrid[row - 1][letter - 65];
        }
    }

}

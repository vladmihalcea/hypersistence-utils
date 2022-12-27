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
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class MultiDimensionalStringArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

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
                        new String[][] {
                            {"BLOCKED", "BLOCKED", "BLOCKED", "BLOCKED"},
                            {"UNRESERVED", "UNRESERVED", "RESERVED", "UNRESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"RESERVED", "RESERVED", "RESERVED", "RESERVED"},
                            {"BLOCKED", "BLOCKED", "BLOCKED", "BLOCKED"}
                        }
                    )
            );
        });

        doInJPA(entityManager -> {
            Plane plane = entityManager.find(Plane.class, 1L);

            assertEquals("ATR-42", plane.getName());
            assertEquals("BLOCKED", plane.getSeatStatus(1, 'A'));
            assertEquals("BLOCKED", plane.getSeatStatus(1, 'B'));
            assertEquals("BLOCKED", plane.getSeatStatus(1, 'C'));
            assertEquals("BLOCKED", plane.getSeatStatus(1, 'D'));
            assertEquals("UNRESERVED", plane.getSeatStatus(2, 'A'));
            assertEquals("UNRESERVED", plane.getSeatStatus(2, 'B'));
            assertEquals("RESERVED", plane.getSeatStatus(2, 'C'));
            assertEquals("UNRESERVED", plane.getSeatStatus(2, 'D'));
        });

        doInJPA(entityManager -> {
            List<Tuple> tuples = entityManager
                .createNativeQuery(
                    "SELECT * " +
                    " FROM plane ", Tuple.class)
                .unwrap(org.hibernate.query.NativeQuery.class)
                .addScalar(
                    "seat_grid",
                    new StringArrayType(
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
    @TypeDef(name = "string-array", typeClass = StringArrayType.class)
    public static class Plane {

        @Id
        private Long id;

        private String name;

        @Type(type = "string-array")
        @Column(name = "seat_grid", columnDefinition = "text[][]")
        private String[][] seatGrid;

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

        public String[][] getSeatGrid() {
            return seatGrid;
        }

        public Plane setSeatGrid(String[][] seatGrid) {
            this.seatGrid = seatGrid;
            return this;
        }

        public String getSeatStatus(int row, char letter) {
            return seatGrid[row - 1][letter - 65];
        }
    }

}

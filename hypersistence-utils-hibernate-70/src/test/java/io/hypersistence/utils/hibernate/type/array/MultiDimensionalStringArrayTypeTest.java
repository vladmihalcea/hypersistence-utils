package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.common.ReflectionUtils;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

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
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) -> {
                    typeContributions.contributeType(
                        new EnumArrayType(
                            ReflectionUtils.getField(MultiDimensionalArrayTypeTest.Plane.class, "seatGrid").getClass(),
                            "seat_status"
                        )
                    );
                }
            ));
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
                    "SELECT " +
                        "   id, " +
                        "   name, " +
                        "   seat_grid " +
                        "FROM plane " +
                        "WHERE id >= :id", Tuple.class)
                .setParameter("id", 0)
                .unwrap(org.hibernate.query.NativeQuery.class)
                .addScalar("id")
                .addScalar("name")
                .addScalar(
                    "seat_grid",
                    ReflectionUtils.getField(MultiDimensionalArrayTypeTest.Plane.class, "seatGrid").getClass()
                )
                .getResultList();

            Tuple plane = tuples.get(0);
            assertEquals("ATR-42", plane.get("name"));
        });
    }

    @Entity(name = "Plane")
    @Table(name = "plane")
    public static class Plane {

        @Id
        private Long id;

        private String name;
        
        @Type(StringArrayType.class)
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

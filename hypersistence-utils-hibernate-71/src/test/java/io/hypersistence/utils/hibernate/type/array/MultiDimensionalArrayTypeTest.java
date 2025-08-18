package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.common.ReflectionUtils;
import jakarta.persistence.*;
import org.hibernate.annotations.Parameter;
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
public class MultiDimensionalArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[] {
            Plane.class,
        };
    }

    @Override
    protected void beforeInit() {
        executeStatement("DROP TABLE IF EXISTS plane;");
        executeStatement("DROP TYPE IF EXISTS seat_status CASCADE;");
        executeStatement("CREATE TYPE seat_status AS ENUM ('UNRESERVED', 'RESERVED', 'BLOCKED');");
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) -> {
                    typeContributions.contributeType(
                        new EnumArrayType(
                            ReflectionUtils.getField(Plane.class, "seatGrid").getType(),
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
                        new SeatStatus[][] {
                            {SeatStatus.BLOCKED, SeatStatus.BLOCKED, SeatStatus.BLOCKED, SeatStatus.BLOCKED},
                            {SeatStatus.UNRESERVED, SeatStatus.UNRESERVED, SeatStatus.RESERVED, SeatStatus.UNRESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED, SeatStatus.RESERVED},
                            {SeatStatus.BLOCKED, SeatStatus.BLOCKED, SeatStatus.BLOCKED, SeatStatus.BLOCKED}
                        }
                    )
            );
        });

        doInJPA(entityManager -> {
            Plane plane = entityManager.find(Plane.class, 1L);

            assertEquals("ATR-42", plane.getName());
            assertEquals(SeatStatus.BLOCKED, plane.getSeatStatus(1, 'A'));
            assertEquals(SeatStatus.BLOCKED, plane.getSeatStatus(1, 'B'));
            assertEquals(SeatStatus.BLOCKED, plane.getSeatStatus(1, 'C'));
            assertEquals(SeatStatus.BLOCKED, plane.getSeatStatus(1, 'D'));
            assertEquals(SeatStatus.UNRESERVED, plane.getSeatStatus(2, 'A'));
            assertEquals(SeatStatus.UNRESERVED, plane.getSeatStatus(2, 'B'));
            assertEquals(SeatStatus.RESERVED, plane.getSeatStatus(2, 'C'));
            assertEquals(SeatStatus.UNRESERVED, plane.getSeatStatus(2, 'D'));
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
                    ReflectionUtils.getField(Plane.class, "seatGrid").getType()
                )
                .getResultList();

            Tuple plane = tuples.get(0);
            assertEquals("ATR-42", plane.get("name"));
        });
    }

    public enum SeatStatus {
        UNRESERVED,
        RESERVED,
        BLOCKED,
    }

    @Entity(name = "Plane")
    @Table(name = "plane")
    public static class Plane {

        @Id
        private Long id;

        private String name;

        @Type(
            value = EnumArrayType.class,
            parameters = @Parameter(
                name = "sql_array_type",
                value = "seat_status"
            )
        )
        @Column(name = "seat_grid", columnDefinition = "seat_status[][]")
        private SeatStatus[][] seatGrid;

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

        public SeatStatus[][] getSeatGrid() {
            return seatGrid;
        }

        public Plane setSeatGrid(SeatStatus[][] seatGrid) {
            this.seatGrid = seatGrid;
            return this;
        }

        public SeatStatus getSeatStatus(int row, char letter) {
            return seatGrid[row - 1][letter - 65];
        }
    }

}

package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.PostgreSQLDataSourceProvider;
import jakarta.persistence.*;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.junit.Before;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

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

    @Before
    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()){
                statement.executeUpdate("DROP TABLE IF EXISTS plane;");
                statement.executeUpdate("DROP TYPE IF EXISTS seat_status CASCADE;");
                statement.executeUpdate("CREATE TYPE seat_status AS ENUM ('UNRESERVED', 'RESERVED', 'BLOCKED');");
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) -> {
                    typeContributions.contributeType(
                        new EnumArrayType(
                            ReflectionUtils.getField(Plane.class, "seatGrid").getClass(),
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
                    ReflectionUtils.getField(Plane.class, "seatGrid").getClass()
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

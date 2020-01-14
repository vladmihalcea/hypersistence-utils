package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Before;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

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

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL95ArrayDialect.class.getName();
            }
        };
    }

    @Before
    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection()) {
            try (Statement statement = connection.createStatement()){
                statement.executeUpdate("DROP TYPE IF EXISTS seat_status;");
                statement.executeUpdate("CREATE TYPE seat_status AS ENUM ('UNRESERVED', 'RESERVED', 'BLOCKED');");
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Plane plane = new Plane();
            plane.setId(1L);
            plane.setName("Alice");
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
    }

    public enum SeatStatus {
        UNRESERVED,
        RESERVED,
        BLOCKED,
    }

    @Entity(name = "Plane")
    @Table(name = "plane")
    @TypeDef(name = "seat_status_array", typeClass = EnumArrayType.class)
    public static class Plane {

        @Id
        private Long id;

        private String name;

        @Type(
            type = "seat_status_array",
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

        private SeatStatus getSeatStatus(int row, char letter) {
            return seatGrid[row - 1][letter - 65];
        }
    }

}

package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
 */
public class WrapperArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    @Override
    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.executeUpdate(
                "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\""
            );
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorIds(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")});
            event.setSensorValues(new Integer[]{12, 756});
            event.setSensorLongValues(new Long[]{42L, 9223372036854775800L});
            event.setSensorDoubleValues(new Double[]{0.123, 456.789});

            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds());
            assertArrayEquals(new Integer[]{12, 756}, event.getSensorValues());
            assertArrayEquals(new Long[]{42L, 9223372036854775800L}, event.getSensorLongValues());
            assertArrayEquals(new Double[]{0.123, 456.789}, event.getSensorDoubleValues());
        });

        doInJPA(entityManager -> {
            List<Event> events = entityManager.createNativeQuery(
                "select " +
                "   id, " +
                "   sensor_ids, " +
                "   sensor_values, " +
                "   sensor_double_values   " +
                "from event ", Tuple.class)
            .unwrap(org.hibernate.query.NativeQuery.class)
            .addScalar("sensor_ids", UUID[].class)
            .addScalar("sensor_values", int[].class)
            .addScalar("sensor_double_values", double[].class)
            .getResultList();

            assertEquals(2, events.size());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {
        @Type(UUIDArrayType.class)
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private UUID[] sensorIds;

        @Type(IntArrayType.class)
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private Integer[] sensorValues;

        @Type(LongArrayType.class)
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private Long[] sensorLongValues;

        @Type(DoubleArrayType.class)
        @Column(name = "sensor_double_values", columnDefinition = "float8[]")
        private Double[] sensorDoubleValues;

        public UUID[] getSensorIds() {
            return sensorIds;
        }

        public void setSensorIds(UUID[] sensorIds) {
            this.sensorIds = sensorIds;
        }

        public Integer[] getSensorValues() {
            return sensorValues;
        }

        public void setSensorValues(Integer[] sensorValues) {
            this.sensorValues = sensorValues;
        }

        public Long[] getSensorLongValues() {
            return sensorLongValues;
        }

        public void setSensorLongValues(Long[] sensorLongValues) {
            this.sensorLongValues = sensorLongValues;
        }

        public Double[] getSensorDoubleValues() {
            return sensorDoubleValues;
        }

        public void setSensorDoubleValues(Double[] sensorDoubleValues) {
            this.sensorDoubleValues = sensorDoubleValues;
        }
    }
}
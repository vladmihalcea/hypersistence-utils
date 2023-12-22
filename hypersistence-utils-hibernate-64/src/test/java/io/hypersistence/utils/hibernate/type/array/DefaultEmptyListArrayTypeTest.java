package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class DefaultEmptyListArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

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
            try {
                statement.executeUpdate(
                    "DROP TYPE sensor_state CASCADE"
                );
            } catch (SQLException ignore) {
            }
            statement.executeUpdate(
                "CREATE TYPE sensor_state AS ENUM ('ONLINE', 'OFFLINE', 'UNKNOWN')"
            );
            statement.executeUpdate(
                "CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\""
            );
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Test
    public void testEmptyArrays() {

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{}, event.getSensorIds().toArray());
            assertArrayEquals(new String[]{}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{}, event.getSensorValues().toArray());
            assertArrayEquals(new Long[]{}, event.getSensorLongValues().toArray());
            assertArrayEquals(new SensorState[]{}, event.getSensorStates().toArray());
            assertArrayEquals(new Date[]{}, event.getDateValues().toArray());
            assertArrayEquals(new Date[]{}, event.getTimestampValues().toArray());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {
        @Type(ListArrayType.class)
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private List<UUID> sensorIds = new ArrayList<>();

        @Type(ListArrayType.class)
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private List<String> sensorNames = new ArrayList<>();;

        @Type(ListArrayType.class)
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private List<Integer> sensorValues = new ArrayList<>();;

        @Type(ListArrayType.class)
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private List<Long> sensorLongValues = new ArrayList<>();;

        @Type(
            value = ListArrayType.class,
            parameters = @Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "sensor_state")
        )
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private List<SensorState> sensorStates = new ArrayList<>();;

        @Type(ListArrayType.class)
        @Column(name = "date_values", columnDefinition = "date[]")
        private List<Date> dateValues = new ArrayList<>();;

        @Type(ListArrayType.class)
        @Column(name = "timestamp_values", columnDefinition = "timestamp[]")
        private List<Date> timestampValues = new ArrayList<>();;

        public List<UUID> getSensorIds() {
            return sensorIds;
        }

        public void setSensorIds(List<UUID> sensorIds) {
            this.sensorIds = sensorIds;
        }

        public List<String> getSensorNames() {
            return sensorNames;
        }

        public void setSensorNames(List<String> sensorNames) {
            this.sensorNames = sensorNames;
        }

        public List<Integer> getSensorValues() {
            return sensorValues;
        }

        public void setSensorValues(List<Integer> sensorValues) {
            this.sensorValues = sensorValues;
        }

        public List<Long> getSensorLongValues() {
            return sensorLongValues;
        }

        public void setSensorLongValues(List<Long> sensorLongValues) {
            this.sensorLongValues = sensorLongValues;
        }

        public List<SensorState> getSensorStates() {
            return sensorStates;
        }

        public void setSensorStates(List<SensorState> sensorStates) {
            this.sensorStates = sensorStates;
        }

        public List<Date> getDateValues() {
            return dateValues;
        }

        public void setDateValues(List<Date> dateValues) {
            this.dateValues = dateValues;
        }

        public List<Date> getTimestampValues() {
            return timestampValues;
        }

        public void setTimestampValues(List<Date> timestampValues) {
            this.timestampValues = timestampValues;
        }
    }

    public enum SensorState {
        ONLINE, OFFLINE, UNKNOWN;
    }
}

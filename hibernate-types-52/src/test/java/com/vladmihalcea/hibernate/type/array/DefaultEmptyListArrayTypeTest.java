package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.Tuple;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;

import static org.junit.Assert.*;

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
    @TypeDef(name = "list-array", typeClass = ListArrayType.class)
    @TypeDef(name = "sensor-state-array", typeClass = ListArrayType.class, parameters = {
        @Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "sensor_state")}
    )
    @Table(name = "event")
    public static class Event extends BaseEntity {
        @Type(type = "list-array")
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private List<UUID> sensorIds = new ArrayList<>();

        @Type(type = "list-array")
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private List<String> sensorNames = new ArrayList<>();;

        @Type(type = "list-array")
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private List<Integer> sensorValues = new ArrayList<>();;

        @Type(type = "list-array")
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private List<Long> sensorLongValues = new ArrayList<>();;

        @Type(type = "sensor-state-array")
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private List<SensorState> sensorStates = new ArrayList<>();;

        @Type(type = "list-array")
        @Column(name = "date_values", columnDefinition = "date[]")
        private List<Date> dateValues = new ArrayList<>();;

        @Type(type = "list-array")
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

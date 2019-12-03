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
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
 */
public class ListArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

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
    public void test() {

        Date date1 = Date.from(LocalDate.of(1991, 12, 31).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        Date date2 = Date.from(LocalDate.of(1990, 1, 1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorIds(Arrays.asList(UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")));
            event.setSensorNames(Arrays.asList("Temperature", "Pressure"));
            event.setSensorValues(Arrays.asList(12, 756));
            event.setSensorLongValues(Arrays.asList(42L, 9223372036854775800L));
            event.setSensorStates(Arrays.asList(SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN));
            event.setDateValues(Arrays.asList(date1, date2));
            event.setTimestampValues(Arrays.asList(date1, date2));
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds().toArray());
            assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{12, 756}, event.getSensorValues().toArray());
            assertArrayEquals(new Long[]{42L, 9223372036854775800L}, event.getSensorLongValues().toArray());
            assertArrayEquals(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN}, event.getSensorStates().toArray());
            assertArrayEquals(new Date[]{date1, date2}, event.getDateValues().toArray());
            assertArrayEquals(new Date[]{date1, date2}, event.getTimestampValues().toArray());
        });

        doInJPA(entityManager -> {
            List<Tuple> events = entityManager.createNativeQuery(
                "select " +
                "   id, " +
                "   sensor_ids, " +
                "   sensor_names, " +
                "   sensor_values " +
                "from event ", Tuple.class)
            .unwrap(org.hibernate.query.NativeQuery.class)
            .addScalar("sensor_ids", UUIDArrayType.INSTANCE)
            .addScalar("sensor_names", StringArrayType.INSTANCE)
            .addScalar("sensor_values", IntArrayType.INSTANCE)
            .getResultList();

            assertEquals(2, events.size());
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
        private List<UUID> sensorIds;

        @Type(type = "list-array")
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private List<String> sensorNames;

        @Type(type = "list-array")
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private List<Integer> sensorValues;

        @Type(type = "list-array")
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private List<Long> sensorLongValues;

        @Type(type = "sensor-state-array")
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private List<SensorState> sensorStates;

        @Type(type = "list-array")
        @Column(name = "date_values", columnDefinition = "date[]")
        private List<Date> dateValues;

        @Type(type = "list-array")
        @Column(name = "timestamp_values", columnDefinition = "timestamp[]")
        private List<Date> timestampValues;

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

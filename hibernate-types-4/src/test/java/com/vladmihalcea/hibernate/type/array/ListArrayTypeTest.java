package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
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
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            Statement statement = null;
            try {
                statement = connection.createStatement();
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
            }
            finally {
                if (statement != null) {
                    try {
                        statement.close();
                    } catch (SQLException e) {
                        fail(e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    fail(e.getMessage());
                }
            }
        }
        super.init();
    }

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL82ArrayDialect.class.getName();
            }
        };
    }

    @Test
    public void test() {

        final Date date1 = Timestamp.valueOf("1991-12-31 00:00:00");
        final Date date2 = Timestamp.valueOf("1990-01-01 00:00:00");

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
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

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds().toArray());
                assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames().toArray());
                assertArrayEquals(new Integer[]{12, 756}, event.getSensorValues().toArray());
                assertArrayEquals(new Long[]{42L, 9223372036854775800L}, event.getSensorLongValues().toArray());
                assertArrayEquals(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN}, event.getSensorStates().toArray());
                assertEquals(date1.getTime(), event.getDateValues().get(0).getTime());
                assertEquals(date2.getTime(), event.getDateValues().get(1).getTime());
                assertEquals(date1.getTime(), event.getTimestampValues().get(0).getTime());
                assertEquals(date2.getTime(), event.getTimestampValues().get(1).getTime());

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                List<Object[]> events = entityManager
                .createNativeQuery(
                    "select " +
                    "   id, " +
                    "   sensor_ids, " +
                    "   sensor_names, " +
                    "   sensor_values " +
                    "from event ")
                .unwrap(org.hibernate.SQLQuery.class)
                .addScalar("sensor_ids", UUIDArrayType.INSTANCE)
                .addScalar("sensor_names", StringArrayType.INSTANCE)
                .addScalar("sensor_values", IntArrayType.INSTANCE)
                .list();

                assertEquals(2, events.size());

                return null;
            }
        });
    }

    @Entity(name = "Event")
    @TypeDefs({
        @TypeDef(name = "uuid-list-array", typeClass = ListArrayType.class),
        @TypeDef(name = "string-list-array", typeClass = ListArrayType.class),
        @TypeDef(name = "int-list-array", typeClass = ListArrayType.class),
        @TypeDef(name = "long-list-array", typeClass = ListArrayType.class),
        @TypeDef(name = "date-list-array", typeClass = ListArrayType.class),
        @TypeDef(name = "timestamp-list-array", typeClass = ListArrayType.class),
        @TypeDef(name = "sensor-state-array", typeClass = ListArrayType.class, parameters = {
            @Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "sensor_state")}
        )
    })
    @Table(name = "event")
    public static class Event extends BaseEntity {
        @Type(type = "uuid-list-array")
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private List<UUID> sensorIds;

        @Type(type = "string-list-array")
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private List<String> sensorNames;

        @Type(type = "int-list-array")
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private List<Integer> sensorValues;

        @Type(type = "long-list-array")
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private List<Long> sensorLongValues;

        @Type(type = "sensor-state-array")
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private List<SensorState> sensorStates;

        @Type(type = "date-list-array")
        @Column(name = "date_values", columnDefinition = "date[]")
        private List<Date> dateValues;

        @Type(type = "timestamp-list-array")
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

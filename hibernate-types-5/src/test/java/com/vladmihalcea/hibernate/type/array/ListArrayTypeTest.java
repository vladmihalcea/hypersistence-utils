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
import java.util.*;

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
            } finally {
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
                return PostgreSQL94ArrayDialect.class.getName();
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

    @Test
    public void testMixingNullValues() {

        final Date date = Timestamp.valueOf("1990-01-01 00:00:00");

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event nullEvent = new Event();
                nullEvent.setId(0L);
                entityManager.persist(nullEvent);

                Event event = new Event();
                event.setId(1L);
                event.setSensorIds(Arrays.asList(null, UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")));
                event.setSensorNames(Arrays.asList("Temperature", null));
                event.setSensorValues(Arrays.asList(null, 756));
                event.setSensorLongValues(Arrays.asList(null, 9223372036854775800L));
                event.setSensorStates(Arrays.asList(null, SensorState.OFFLINE, SensorState.ONLINE, null));
                event.setDateValues(Arrays.asList(null, date));
                event.setTimestampValues(Arrays.asList(null, date));
                entityManager.persist(event);
                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new UUID[]{null, UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds().toArray());
                assertArrayEquals(new String[]{"Temperature", null}, event.getSensorNames().toArray());
                assertArrayEquals(new Integer[]{null, 756}, event.getSensorValues().toArray());
                assertArrayEquals(new Long[]{null, 9223372036854775800L}, event.getSensorLongValues().toArray());
                assertArrayEquals(new SensorState[]{null, SensorState.OFFLINE, SensorState.ONLINE, null}, event.getSensorStates().toArray());

                assertNull(event.getDateValues().get(0));
                assertEquals(date.getTime(), event.getDateValues().get(1).getTime());
                assertEquals(date.getTime(), event.getDateValues().get(1).getTime());

                assertNull(event.getTimestampValues().get(0));
                assertEquals(date.getTime(), event.getTimestampValues().get(1).getTime());
                assertEquals(date.getTime(), event.getTimestampValues().get(1).getTime());

                return null;
            }
        });
    }

    @Test
    public void testNullValues() {

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event nullEvent = new Event();
                nullEvent.setId(0L);
                entityManager.persist(nullEvent);

                Event event = new Event();
                event.setId(1L);
                event.setSensorIds(Arrays.<UUID>asList(null, null));
                event.setSensorNames(Arrays.<String>asList(null, null));
                event.setSensorValues(Arrays.<Integer>asList(null, null));
                event.setSensorLongValues(Arrays.<Long>asList(null, null));
                event.setSensorStates(Arrays.<SensorState>asList(null, null));
                event.setDateValues(Arrays.<Date>asList(null, null));
                event.setTimestampValues(Arrays.<Date>asList(null, null));
                entityManager.persist(event);
                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new UUID[]{null, null}, event.getSensorIds().toArray());
                assertArrayEquals(new String[]{null, null}, event.getSensorNames().toArray());
                assertArrayEquals(new Integer[]{null, null}, event.getSensorValues().toArray());
                assertArrayEquals(new Long[]{null, null}, event.getSensorLongValues().toArray());
                assertArrayEquals(new SensorState[]{null, null}, event.getSensorStates().toArray());
                assertArrayEquals(new Date[]{null, null}, event.getDateValues().toArray());
                assertArrayEquals(new Date[]{null, null}, event.getTimestampValues().toArray());
                return null;
            }
        });
    }

    @Test
    public void testEmptyArrays() {

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event nullEvent = new Event();
                nullEvent.setId(0L);
                entityManager.persist(nullEvent);

                Event event = new Event();
                event.setId(1L);
                event.setSensorIds(Collections.<UUID>emptyList());
                event.setSensorNames(Collections.<String>emptyList());
                event.setSensorValues(Collections.<Integer>emptyList());
                event.setSensorLongValues(Collections.<Long>emptyList());
                event.setSensorStates(Collections.<SensorState>emptyList());
                event.setDateValues(Collections.<Date>emptyList());
                event.setTimestampValues(Collections.<Date>emptyList());
                entityManager.persist(event);
                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new UUID[]{}, event.getSensorIds().toArray());
                assertArrayEquals(new String[]{}, event.getSensorNames().toArray());
                assertArrayEquals(new Integer[]{}, event.getSensorValues().toArray());
                assertArrayEquals(new Long[]{}, event.getSensorLongValues().toArray());
                assertArrayEquals(new SensorState[]{}, event.getSensorStates().toArray());
                assertArrayEquals(new Date[]{}, event.getDateValues().toArray());
                assertArrayEquals(new Date[]{}, event.getTimestampValues().toArray());
                return null;
            }
        });
    }

    @Test
    public void testNullCollections() {

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event nullEvent = new Event();
                nullEvent.setId(0L);
                entityManager.persist(nullEvent);

                Event event = new Event();
                event.setId(1L);
                entityManager.persist(event);
                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertEquals(null, event.getSensorIds());
                assertEquals(null, event.getSensorNames());
                assertEquals(null, event.getSensorLongValues());
                assertEquals(null, event.getSensorStates());
                assertEquals(null, event.getDateValues());
                assertEquals(null, event.getTimestampValues());
                return null;
            }
        });
    }

    @Entity(name = "Event")
    @TypeDefs({
        @TypeDef(name = "list-array", typeClass = ListArrayType.class),
        @TypeDef(name = "sensor-state-array", typeClass = ListArrayType.class, parameters = {
            @Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "sensor_state")}
        )
    })
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

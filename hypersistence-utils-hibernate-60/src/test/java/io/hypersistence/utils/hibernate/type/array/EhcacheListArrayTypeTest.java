package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.junit.Test;

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
public class EhcacheListArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    protected void additionalProperties(Properties properties) {
        properties.setProperty("hibernate.cache.use_second_level_cache", "true");
        properties.setProperty("hibernate.cache.use_query_cache", "true");
        properties.setProperty("hibernate.cache.region.factory_class", "jcache");
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
                "from event " +
                "where id >= :id", Tuple.class)
            .setParameter("id", 0)
            .unwrap(org.hibernate.query.NativeQuery.class)
            .addScalar("sensor_ids", UUID[].class)
            .addScalar("sensor_names", String[].class)
            .addScalar("sensor_values", int[].class)
            .getResultList();

            assertEquals(2, events.size());
        });
    }

    @Test
    public void testMixingNullValues() {

        Date date = Date.from(LocalDate.of(1990, 1, 1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

        doInJPA(entityManager -> {
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
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{null, UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds().toArray());
            assertArrayEquals(new String[]{"Temperature", null}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{null, 756}, event.getSensorValues().toArray());
            assertArrayEquals(new Long[]{null, 9223372036854775800L}, event.getSensorLongValues().toArray());
            assertArrayEquals(new SensorState[]{null, SensorState.OFFLINE, SensorState.ONLINE, null}, event.getSensorStates().toArray());
            assertArrayEquals(new Date[]{null, date}, event.getDateValues().toArray());
            assertArrayEquals(new Date[]{null, date}, event.getTimestampValues().toArray());
        });
    }
    
    @Test
    public void testNullValues() {
        
        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorIds(Arrays.asList(null, null));
            event.setSensorNames(Arrays.asList(null, null));
            event.setSensorValues(Arrays.asList(null, null));
            event.setSensorLongValues(Arrays.asList(null, null));
            event.setSensorStates(Arrays.asList(null, null));
            event.setDateValues(Arrays.asList(null, null));
            event.setTimestampValues(Arrays.asList(null, null));
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{null, null}, event.getSensorIds().toArray());
            assertArrayEquals(new String[]{null, null}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{null, null}, event.getSensorValues().toArray());
            assertArrayEquals(new Long[]{null, null}, event.getSensorLongValues().toArray());
            assertArrayEquals(new SensorState[]{null, null}, event.getSensorStates().toArray());
            assertArrayEquals(new Date[]{null, null}, event.getDateValues().toArray());
            assertArrayEquals(new Date[]{null, null}, event.getTimestampValues().toArray());
        });
    }

    @Test
    public void testEmptyArrays() {

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorIds(Collections.emptyList());
            event.setSensorNames(Collections.emptyList());
            event.setSensorValues(Collections.emptyList());
            event.setSensorLongValues(Collections.emptyList());
            event.setSensorStates(Collections.emptyList());
            event.setDateValues(Collections.emptyList());
            event.setTimestampValues(Collections.emptyList());
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

    @Test
    public void testNullArrays() {

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

            assertEquals(null, event.getSensorIds());
            assertEquals(null, event.getSensorNames());
            assertEquals(null, event.getSensorLongValues());
            assertEquals(null, event.getSensorStates());
            assertEquals(null, event.getDateValues());
            assertEquals(null, event.getTimestampValues());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @Cacheable
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    public static class Event extends BaseEntity {
        @Type(ListArrayType.class)
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private List<UUID> sensorIds;

        @Type(ListArrayType.class)
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private List<String> sensorNames;

        @Type(ListArrayType.class)
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private List<Integer> sensorValues;

        @Type(ListArrayType.class)
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private List<Long> sensorLongValues;

        @Type(
            value = ListArrayType.class,
            parameters = @Parameter(name = ListArrayType.SQL_ARRAY_TYPE, value = "sensor_state")
        )
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private List<SensorState> sensorStates;

        @Type(ListArrayType.class)
        @Column(name = "date_values", columnDefinition = "date[]")
        private List<Date> dateValues;

        @Type(ListArrayType.class)
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

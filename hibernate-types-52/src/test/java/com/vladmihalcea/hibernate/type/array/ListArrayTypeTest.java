package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import javax.sql.DataSource;
import java.math.BigDecimal;
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

        doInJPA(entityManager -> {
            entityManager.persist(
                new Event()
                    .setId(0L)
            );

            entityManager.persist(
                new Event()
                    .setId(1L)
                    .setSensorIds(
                        Arrays.asList(
                            UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"),
                            UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")
                        )
                    )
                    .setSensorNames(Arrays.asList("Temperature", "Pressure"))
                    .setSensorValues(Arrays.asList(12, 756))
                    .setSensorLongValues(Arrays.asList(42L, 9223372036854775800L))
                    .setSensorBooleanValues(Arrays.asList(true, false))
                    .setSensorDoubleValues(Arrays.asList(0.123D, 456.789D))
                    .setSensorStates(
                        Arrays.asList(
                            SensorState.ONLINE, SensorState.OFFLINE,
                            SensorState.ONLINE, SensorState.UNKNOWN
                        )
                    )
                    .setDateValues(
                        Arrays.asList(
                            java.sql.Date.valueOf(LocalDate.of(1991, 12, 31)),
                            java.sql.Date.valueOf(LocalDate.of(1990, 1, 1))
                        )
                    )
                    .setTimestampValues(
                        Arrays.asList(
                            Date.from(
                                LocalDate.of(1991, 12, 31)
                                    .atStartOfDay()
                                    .atZone(ZoneId.systemDefault())
                                        .toInstant()
                            ),
                                Date.from(
                                        LocalDate.of(1990, 1, 1)
                                                .atStartOfDay()
                                                .atZone(ZoneId.systemDefault())
                                                .toInstant()
                                )
                        )
                    )
                        .setDecimalValues(
                                Arrays.asList(
                                        BigDecimal.ONE,
                                        BigDecimal.ZERO,
                                        BigDecimal.TEN
                                )
                        )
            );
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertEquals(
                Arrays.asList(
                    UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"),
                    UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")
                ),
                event.getSensorIds()
            );
            assertEquals(
                Arrays.asList("Temperature", "Pressure"),
                event.getSensorNames()
            );
            assertEquals(
                Arrays.asList(12, 756),
                event.getSensorValues()
            );
            assertEquals(
                Arrays.asList(42L, 9223372036854775800L),
                event.getSensorLongValues()
            );
            assertEquals(
                Arrays.asList(true, false),
                event.getSensorBooleanValues()
            );
            assertEquals(
                Arrays.asList(0.123D, 456.789D),
                event.getSensorDoubleValues()
            );
            assertEquals(
                Arrays.asList(
                    SensorState.ONLINE, SensorState.OFFLINE,
                    SensorState.ONLINE, SensorState.UNKNOWN
                ),
                event.getSensorStates()
            );
            assertEquals(
                Arrays.asList(
                    java.sql.Date.valueOf(LocalDate.of(1991, 12, 31)),
                    java.sql.Date.valueOf(LocalDate.of(1990, 1, 1))
                ),
                event.getDateValues()
            );
            assertEquals(
                Arrays.asList(
                    Date.from(
                        LocalDate.of(1991, 12, 31)
                            .atStartOfDay()
                            .atZone(ZoneId.systemDefault())
                            .toInstant()
                    ),
                        Date.from(
                                LocalDate.of(1990, 1, 1)
                                        .atStartOfDay()
                                        .atZone(ZoneId.systemDefault())
                                        .toInstant()
                        )
                ),
                    event.getTimestampValues()
            );
            assertEquals(
                    Arrays.asList(
                            BigDecimal.ONE,
                            BigDecimal.ZERO,
                            BigDecimal.TEN
                    ),
                    event.getDecimalValues()
            );
        });

        doInJPA(entityManager -> {
            List<Tuple> events = entityManager.createNativeQuery(
                "select " +
                "   id, " +
                "   sensor_ids, " +
                "   sensor_names, " +
                "   sensor_values, " +
                "   sensor_states " +
                "from event ", Tuple.class)
            .unwrap(org.hibernate.query.NativeQuery.class)
            .addScalar("sensor_ids", UUIDArrayType.INSTANCE)
            .addScalar("sensor_names", StringArrayType.INSTANCE)
            .addScalar("sensor_values", IntArrayType.INSTANCE)
            .addScalar(
                "sensor_states",
                new EnumArrayType(
                    SensorState[].class,
                    "sensor_state"
                )
            )
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
            event.setSensorBooleanValues(Arrays.asList(null, false));
            event.setSensorDoubleValues(Arrays.asList(null, 456.789D));
            event.setSensorStates(Arrays.asList(null, SensorState.OFFLINE, SensorState.ONLINE, null));
            event.setDateValues(Arrays.asList(null, date));
            event.setTimestampValues(Arrays.asList(null, date));
            event.setDecimalValues(Arrays.asList(null, BigDecimal.TEN));
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{null, UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds().toArray());
            assertArrayEquals(new String[]{"Temperature", null}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{null, 756}, event.getSensorValues().toArray());
            assertArrayEquals(new Long[]{null, 9223372036854775800L}, event.getSensorLongValues().toArray());
            assertArrayEquals(new Boolean[]{null, false}, event.getSensorBooleanValues().toArray());
            assertArrayEquals(new Double[]{null, 456.789D}, event.getSensorDoubleValues().toArray());
            assertArrayEquals(new SensorState[]{null, SensorState.OFFLINE, SensorState.ONLINE, null}, event.getSensorStates().toArray());
            assertArrayEquals(new Date[]{null, date}, event.getDateValues().toArray());
            assertArrayEquals(new Date[]{null, date}, event.getTimestampValues().toArray());
            assertArrayEquals(new BigDecimal[]{null, BigDecimal.TEN}, event.getDecimalValues().toArray());
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
            event.setSensorBooleanValues(Arrays.asList(null, null));
            event.setSensorDoubleValues(Arrays.asList(null, null));
            event.setSensorStates(Arrays.asList(null, null));
            event.setDateValues(Arrays.asList(null, null));
            event.setTimestampValues(Arrays.asList(null, null));
            event.setDecimalValues(Arrays.asList(null, null));
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{null, null}, event.getSensorIds().toArray());
            assertArrayEquals(new String[]{null, null}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{null, null}, event.getSensorValues().toArray());
            assertArrayEquals(new Long[]{null, null}, event.getSensorLongValues().toArray());
            assertArrayEquals(new Boolean[]{null, null}, event.getSensorBooleanValues().toArray());
            assertArrayEquals(new Double[]{null, null}, event.getSensorDoubleValues().toArray());
            assertArrayEquals(new SensorState[]{null, null}, event.getSensorStates().toArray());
            assertArrayEquals(new Date[]{null, null}, event.getDateValues().toArray());
            assertArrayEquals(new Date[]{null, null}, event.getTimestampValues().toArray());
            assertArrayEquals(new BigDecimal[]{null, null}, event.getDecimalValues().toArray());
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
            event.setSensorBooleanValues(Collections.emptyList());
            event.setSensorDoubleValues(Collections.emptyList());
            event.setSensorStates(Collections.emptyList());
            event.setDateValues(Collections.emptyList());
            event.setTimestampValues(Collections.emptyList());
            event.setDecimalValues(Collections.emptyList());
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{}, event.getSensorIds().toArray());
            assertArrayEquals(new String[]{}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{}, event.getSensorValues().toArray());
            assertArrayEquals(new Long[]{}, event.getSensorLongValues().toArray());
            assertArrayEquals(new Boolean[]{}, event.getSensorBooleanValues().toArray());
            assertArrayEquals(new Double[]{}, event.getSensorDoubleValues().toArray());
            assertArrayEquals(new SensorState[]{}, event.getSensorStates().toArray());
            assertArrayEquals(new Date[]{}, event.getDateValues().toArray());
            assertArrayEquals(new Date[]{}, event.getTimestampValues().toArray());
            assertArrayEquals(new BigDecimal[]{}, event.getDecimalValues().toArray());
        });
    }


    @Entity(name = "Event")
    @Table(name = "event")
    @TypeDef(name = "list-array", typeClass = ListArrayType.class)
    public static class Event {

        @Id
        private Long id;

        @Type(type = "list-array")
        @Column(
            name = "sensor_ids",
            columnDefinition = "uuid[]"
        )
        private List<UUID> sensorIds;

        @Type(type = "list-array")
        @Column(
            name = "sensor_names",
            columnDefinition = "text[]"
        )
        private List<String> sensorNames;

        @Type(type = "list-array")
        @Column(
            name = "sensor_values",
            columnDefinition = "integer[]"
        )
        private List<Integer> sensorValues;

        @Type(type = "list-array")
        @Column(
            name = "sensor_long_values",
            columnDefinition = "bigint[]"
        )
        private List<Long> sensorLongValues;

        @Type(type = "list-array")
        @Column(
            name = "sensor_boolean_values",
            columnDefinition = "boolean[]"
        )
        private List<Boolean> sensorBooleanValues;

        @Type(type = "list-array")
        @Column(
            name = "sensor_double_values",
            columnDefinition = "float8[]"
        )
        private List<Double> sensorDoubleValues;

        @Type(
            type = "com.vladmihalcea.hibernate.type.array.ListArrayType",
            parameters = {
                @Parameter(
                    name = ListArrayType.SQL_ARRAY_TYPE,
                    value = "sensor_state"
                )
            }
        )
        @Column(
            name = "sensor_states",
            columnDefinition = "sensor_state[]"
        )
        private List<SensorState> sensorStates;

        @Type(type = "list-array")
        @Column(
            name = "date_values",
            columnDefinition = "date[]"
        )
        private List<Date> dateValues;

        @Type(type = "list-array")
        @Column(
                name = "timestamp_values",
                columnDefinition = "timestamp[]"
        )
        private List<Date> timestampValues;

        @Type(type = "list-array")
        @Column(
                name = "decimal_values",
                columnDefinition = "decimal[]"
        )
        private List<BigDecimal> decimalValues;

        public Long getId() {
            return id;
        }

        public Event setId(Long id) {
            this.id = id;
            return this;
        }

        public List<UUID> getSensorIds() {
            return sensorIds;
        }

        public Event setSensorIds(List<UUID> sensorIds) {
            this.sensorIds = sensorIds;
            return this;
        }

        public List<String> getSensorNames() {
            return sensorNames;
        }

        public Event setSensorNames(List<String> sensorNames) {
            this.sensorNames = sensorNames;
            return this;
        }

        public List<Integer> getSensorValues() {
            return sensorValues;
        }

        public Event setSensorValues(List<Integer> sensorValues) {
            this.sensorValues = sensorValues;
            return this;
        }

        public List<Long> getSensorLongValues() {
            return sensorLongValues;
        }

        public Event setSensorLongValues(List<Long> sensorLongValues) {
            this.sensorLongValues = sensorLongValues;
            return this;
        }

        public List<Boolean> getSensorBooleanValues() {
            return sensorBooleanValues;
        }

        public Event setSensorBooleanValues(List<Boolean> sensorBooleanValues) {
            this.sensorBooleanValues = sensorBooleanValues;
            return this;
        }

        public List<Double> getSensorDoubleValues() {
            return sensorDoubleValues;
        }

        public Event setSensorDoubleValues(List<Double> sensorDoubleValues) {
            this.sensorDoubleValues = sensorDoubleValues;
            return this;
        }

        public List<SensorState> getSensorStates() {
            return sensorStates;
        }

        public Event setSensorStates(List<SensorState> sensorStates) {
            this.sensorStates = sensorStates;
            return this;
        }

        public List<Date> getDateValues() {
            return dateValues;
        }

        public Event setDateValues(List<Date> dateValues) {
            this.dateValues = dateValues;
            return this;
        }

        public List<Date> getTimestampValues() {
            return timestampValues;
        }

        public Event setTimestampValues(List<Date> timestampValues) {
            this.timestampValues = timestampValues;
            return this;
        }

        public List<BigDecimal> getDecimalValues() {
            return decimalValues;
        }

        public Event setDecimalValues(List<BigDecimal> decimalValues) {
            this.decimalValues = decimalValues;
            return this;
        }

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

    public enum SensorState {
        ONLINE, OFFLINE, UNKNOWN;
    }
}

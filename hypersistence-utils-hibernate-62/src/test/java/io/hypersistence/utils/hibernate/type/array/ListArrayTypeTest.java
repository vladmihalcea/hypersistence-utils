package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import jakarta.persistence.metamodel.ManagedType;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.junit.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
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
    protected void additionalProperties(Properties properties) {
        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) -> {
                    typeContributions.contributeType(new EnumArrayType(SensorState[].class, "sensor_state"));
                }
            ));
    }

    @Override
    protected void afterInit() {
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
                    .setLocalDateValues(
                        Arrays.asList(
                            LocalDate.of(2022, 3, 22),
                            LocalDate.of(2021, 4, 21)
                        )
                    )
                    .setLocalDateTimeValues(
                        Arrays.asList(
                            LocalDateTime.of(2022, 3, 22, 11, 22, 33),
                            LocalDateTime.of(2021, 4, 21, 22, 33, 44)
                        )
                    )
                    .setLocalDateTimeSetValues(
                        new LinkedHashSet<>(
                            Arrays.asList(
                                LocalDateTime.of(2022, 3, 22, 11, 22, 33),
                                LocalDateTime.of(2022, 3, 22, 11, 22, 33),
                                LocalDateTime.of(2021, 4, 21, 22, 33, 44)
                            )
                        )
                    )
            );
        });
    }

    @Test
    public void test() {
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
            assertEquals(
                Arrays.asList(
                    LocalDate.of(2022, 3, 22),
                    LocalDate.of(2021, 4, 21)
                ),
                event.getLocalDateValues()
            );
            assertEquals(
                Arrays.asList(
                    LocalDateTime.of(2022, 3, 22, 11, 22, 33),
                    LocalDateTime.of(2021, 4, 21, 22, 33, 44)
                ),
                event.getLocalDateTimeValues()
            );
            assertEquals(
                new HashSet<>(
                    Arrays.asList(
                        LocalDateTime.of(2022, 3, 22, 11, 22, 33),
                        LocalDateTime.of(2021, 4, 21, 22, 33, 44)
                    )
                ),
                event.getLocalDateTimeSetValues()
            );
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
    public void testScalarEnumArray() {
        doInJPA(entityManager -> {
            List<Tuple> events = entityManager.createNativeQuery(
                "select " +
                    "   id, " +
                    "   sensor_ids, " +
                    "   sensor_names, " +
                    "   sensor_values, " +
                    "   sensor_states " +
                    "from event " +
                    "where id >= :id", Tuple.class)
            .setParameter("id", 0)
            .unwrap(org.hibernate.query.NativeQuery.class)
            .addScalar("sensor_ids", UUID[].class)
            .addScalar("sensor_names", String[].class)
            .addScalar("sensor_values", int[].class)
            .addScalar("sensor_states", SensorState[].class)
            .getResultList();

            assertEquals(2, events.size());
        });
    }

    @Test
    public void testMixingNullValues() {

        Date date = Date.from(LocalDate.of(1990, 1, 1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(100L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(101L);
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
            event.setLocalDateValues(Arrays.asList(null, LocalDate.of(2021, 4, 21)));
            event.setLocalDateTimeValues(Arrays.asList(null, LocalDateTime.of(2021, 4, 21, 22, 33, 44)));
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 101L);

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
            assertArrayEquals(new LocalDate[]{null, LocalDate.of(2021, 4, 21)}, event.getLocalDateValues().toArray());
            assertArrayEquals(new LocalDateTime[]{null, LocalDateTime.of(2021, 4, 21, 22, 33, 44)}, event.getLocalDateTimeValues().toArray());
        });
    }

    @Test
    public void testNullValues() {

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(100L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(101L);
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
            event.setLocalDateValues(Arrays.asList(null, null));
            event.setLocalDateTimeValues(Arrays.asList(null, null));
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 101L);

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
            assertArrayEquals(new LocalDate[]{null, null}, event.getLocalDateValues().toArray());
            assertArrayEquals(new LocalDateTime[]{null, null}, event.getLocalDateTimeValues().toArray());
        });
    }

    @Test
    public void testEmptyArrays() {

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(100L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(101L);
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
            event.setLocalDateValues(Collections.emptyList());
            event.setLocalDateTimeValues(Collections.emptyList());
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 101L);

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
            assertArrayEquals(new LocalDate[]{}, event.getLocalDateValues().toArray());
            assertArrayEquals(new LocalDateTime[]{}, event.getLocalDateTimeValues().toArray());
        });
    }

    @Test
    public void testAttributeType() {
        doInJPA(entityManager -> {
            ManagedType<Event> eventManagedType = entityManager.getMetamodel().managedType(Event.class);
            assertEquals(
                Collection.class,
                eventManagedType.getAttribute("sensorIds").getJavaType()
            );
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        private Long id;

        @Type(ListArrayType.class)
        @Column(
            name = "sensor_ids",
            columnDefinition = "uuid[]"
        )
        private List<UUID> sensorIds;

        @Type(ListArrayType.class)
        @Column(
            name = "sensor_names",
            columnDefinition = "text[]"
        )
        private List<String> sensorNames;

        @Type(ListArrayType.class)
        @Column(
            name = "sensor_values",
            columnDefinition = "integer[]"
        )
        private List<Integer> sensorValues;

        @Type(ListArrayType.class)
        @Column(
            name = "sensor_long_values",
            columnDefinition = "bigint[]"
        )
        private List<Long> sensorLongValues;

        @Type(ListArrayType.class)
        @Column(
            name = "sensor_boolean_values",
            columnDefinition = "boolean[]"
        )
        private List<Boolean> sensorBooleanValues;

        @Type(ListArrayType.class)
        @Column(
            name = "sensor_double_values",
            columnDefinition = "float8[]"
        )
        private List<Double> sensorDoubleValues;

        @Type(
            value = ListArrayType.class,
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

        @Type(ListArrayType.class)
        @Column(
            name = "date_values",
            columnDefinition = "date[]"
        )
        private List<Date> dateValues;

        @Type(ListArrayType.class)
        @Column(
            name = "timestamp_values",
            columnDefinition = "timestamp[]"
        )
        private List<Date> timestampValues;

        @Type(ListArrayType.class)
        @Column(
            name = "decimal_values",
            columnDefinition = "decimal[]"
        )
        private List<BigDecimal> decimalValues;

        @Type(ListArrayType.class)
        @Column(
            name = "localdate_values",
            columnDefinition = "date[]"
        )
        private List<LocalDate> localDateValues;

        @Type(ListArrayType.class)
        @Column(
            name = "localdatetime_values",
            columnDefinition = "timestamp[]"
        )
        private List<LocalDateTime> localDateTimeValues;

        @Type(ListArrayType.class)
        @Column(
            name = "localdatetime_set_values",
            columnDefinition = "timestamp[]"
        )
        private Set<LocalDateTime> localDateTimeSetValues;

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

        public List<LocalDate> getLocalDateValues() {
            return localDateValues;
        }

        public Event setLocalDateValues(List<LocalDate> localDateValues) {
            this.localDateValues = localDateValues;
            return this;
        }

        public List<LocalDateTime> getLocalDateTimeValues() {
            return localDateTimeValues;
        }

        public Event setLocalDateTimeValues(List<LocalDateTime> localDateTimeValues) {
            this.localDateTimeValues = localDateTimeValues;
            return this;
        }

        public Set<LocalDateTime> getLocalDateTimeSetValues() {
            return localDateTimeSetValues;
        }

        public Event setLocalDateTimeSetValues(Set<LocalDateTime> localDateTimeSetValues) {
            this.localDateTimeSetValues = localDateTimeSetValues;
            return this;
        }
    }

    @Test
    public void testNullArrays() {

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(100L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(101L);
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 101L);

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

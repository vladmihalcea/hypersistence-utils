package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
 * @author Guillaume Briand
 */
public class ArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

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
    public void test() {

        Date date1 = Date.from(LocalDate.of(1991, 12, 31).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        Date date2 = Date.from(LocalDate.of(1990, 1, 1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorIds(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")});
            event.setSensorNames(new String[]{"Temperature", "Pressure"});
            event.setSensorValues(new int[]{12, 756});
            event.setSensorLongValues(new long[]{42L, 9223372036854775800L});
            event.setSensorDoubleValues(new double[]{0.123, 456.789});
            event.setSensorFloatValues(new float[]{1.2f, 4.35f});
            event.setSensorStates(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN});
            event.setDateValues(new Date[]{date1, date2});
            event.setTimestampValues(new Date[]{date1, date2});
            event.setDecimalValues(new BigDecimal[]{BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN});
            event.setLocalDateValues(new LocalDate[]{LocalDate.of(2022, 3, 22), LocalDate.of(2021, 4, 21)});
            event.setLocalDateTimeValues(new LocalDateTime[]{LocalDateTime.of(2022, 3, 22, 11, 22, 33), LocalDateTime.of(2021, 4, 21, 22, 33, 44)});
            event.setSensorBooleanValues(new Boolean[]{false, true, true});

            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds());
            assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames());
            assertArrayEquals(new int[]{12, 756}, event.getSensorValues());
            assertArrayEquals(new long[]{42L, 9223372036854775800L}, event.getSensorLongValues());
            assertArrayEquals(new double[]{0.123, 456.789}, event.getSensorDoubleValues(), 0.01);
            assertArrayEquals(new float[]{1.2f, 4.35f}, event.getSensorFloatValues(), 0.01f);
            assertArrayEquals(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN}, event.getSensorStates());
            assertArrayEquals(new Date[]{date1, date2}, event.getDateValues());
            assertArrayEquals(new Date[]{date1, date2}, event.getTimestampValues());
            assertArrayEquals(new BigDecimal[]{BigDecimal.ONE, BigDecimal.ZERO, BigDecimal.TEN}, event.getDecimalValues());
            assertArrayEquals(new LocalDate[]{LocalDate.of(2022, 3, 22), LocalDate.of(2021, 4, 21)}, event.getLocalDateValues());
            assertArrayEquals(new LocalDateTime[]{LocalDateTime.of(2022, 3, 22, 11, 22, 33), LocalDateTime.of(2021, 4, 21, 22, 33, 44)}, event.getLocalDateTimeValues());
            assertArrayEquals(new Boolean[]{false, true, true}, event.getSensorBooleanValues());
        });

        doInJPA(entityManager -> {
            List<Event> events = entityManager.createNativeQuery(
                            "select " +
                                    "   id, " +
                                    "   sensor_ids, " +
                                    "   sensor_names, " +
                                    "   sensor_values, " +
                                    "   date_values   " +
                                    "from event " +
                                    "where id >= :id", Tuple.class)
                    .setParameter("id", 0)
                    .unwrap(org.hibernate.query.NativeQuery.class)
                    .addScalar("sensor_ids", UUID[].class)
                    .addScalar("sensor_names", String[].class)
                    .addScalar("sensor_values", int[].class)
                    .addScalar("date_values", Date[].class)
                    .getResultList();

            assertEquals(2, events.size());
        });
    }

    @Test
    public void testLargeArray() {
        int[] sensorValues = new int[100];

        Arrays.fill(sensorValues, 0, 10, 123);
        Arrays.fill(sensorValues, 10, 50, 456);
        Arrays.fill(sensorValues, 50, 90, 789);
        Arrays.fill(sensorValues, 90, 100, 0);

        doInJPA(entityManager -> {
            Event event = new Event();
            event.setId(0L);
            event.setSensorValues(sensorValues);

            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 0L);

            assertArrayEquals(sensorValues, event.getSensorValues());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {
        @Type(UUIDArrayType.class)
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private UUID[] sensorIds;

        @Type(StringArrayType.class)
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private String[] sensorNames;

        @Type(IntArrayType.class)
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private int[] sensorValues;

        @Type(LongArrayType.class)
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private long[] sensorLongValues;

        @Type(BooleanArrayType.class)
        @Column(name = "sensor_boolean_values", columnDefinition = "boolean[]")
        private Boolean[] sensorBooleanValues;

        @Type(DoubleArrayType.class)
        @Column(name = "sensor_double_values", columnDefinition = "float8[]")
        private double[] sensorDoubleValues;

        @Type(FloatArrayType.class)
        @Column(name = "sensor_float_values", columnDefinition = "float4[]")
        private float[] sensorFloatValues;

        @Type(DateArrayType.class)
        @Column(name = "date_values", columnDefinition = "date[]")
        private Date[] dateValues;

        @Type(TimestampArrayType.class)
        @Column(name = "timestamp_values", columnDefinition = "timestamp[]")
        private Date[] timestampValues;

        @Type(DecimalArrayType.class)
        @Column(name = "decimal_values", columnDefinition = "decimal[]")
        private BigDecimal[] decimalValues;

        @Type(LocalDateArrayType.class)
        @Column(name = "localdate_values", columnDefinition = "date[]")
        private LocalDate[] localDateValues;

        @Type(LocalDateTimeArrayType.class)
        @Column(name = "localdatetime_values", columnDefinition = "timestamp[]")
        private LocalDateTime[] localDateTimeValues;

        @Type(
                value = EnumArrayType.class,
                parameters = @Parameter(name = AbstractArrayType.SQL_ARRAY_TYPE, value = "sensor_state")
        )
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private SensorState[] sensorStates;

        public UUID[] getSensorIds() {
            return sensorIds;
        }

        public void setSensorIds(UUID[] sensorIds) {
            this.sensorIds = sensorIds;
        }

        public String[] getSensorNames() {
            return sensorNames;
        }

        public void setSensorNames(String[] sensorNames) {
            this.sensorNames = sensorNames;
        }

        public int[] getSensorValues() {
            return sensorValues;
        }

        public void setSensorValues(int[] sensorValues) {
            this.sensorValues = sensorValues;
        }

        public long[] getSensorLongValues() {
            return sensorLongValues;
        }

        public void setSensorLongValues(long[] sensorLongValues) {
            this.sensorLongValues = sensorLongValues;
        }

        public Boolean[] getSensorBooleanValues() {
            return sensorBooleanValues;
        }

        public void setSensorBooleanValues(Boolean[] sensorBooleanValues) {
            this.sensorBooleanValues = sensorBooleanValues;
        }

        public double[] getSensorDoubleValues() {
            return sensorDoubleValues;
        }

        public void setSensorDoubleValues(double[] sensorDoubleValues) {
            this.sensorDoubleValues = sensorDoubleValues;
        }

        public float[] getSensorFloatValues() {
            return sensorFloatValues;
        }

        public void setSensorFloatValues(float[] sensorFloatValues) {
            this.sensorFloatValues = sensorFloatValues;
        }

        public SensorState[] getSensorStates() {
            return sensorStates;
        }

        public void setSensorStates(SensorState[] sensorStates) {
            this.sensorStates = sensorStates;
        }

        public Date[] getDateValues() {
            return dateValues;
        }

        public void setDateValues(Date[] dateValues) {
            this.dateValues = dateValues;
        }

        public Date[] getTimestampValues() {
            return timestampValues;
        }

        public void setTimestampValues(Date[] timestampValues) {
            this.timestampValues = timestampValues;
        }

        public BigDecimal[] getDecimalValues() {
            return decimalValues;
        }

        public void setDecimalValues(BigDecimal[] decimalValues) {
            this.decimalValues = decimalValues;
        }

        public LocalDate[] getLocalDateValues() {
            return localDateValues;
        }

        public void setLocalDateValues(LocalDate[] localDateValues) {
            this.localDateValues = localDateValues;
        }

        public LocalDateTime[] getLocalDateTimeValues() {
            return localDateTimeValues;
        }

        public void setLocalDateTimeValues(LocalDateTime[] localDateTimeValues) {
            this.localDateTimeValues = localDateTimeValues;
        }
    }

    public enum SensorState {
        ONLINE, OFFLINE, UNKNOWN;
    }
}
package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Tuple;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class HSQLDBArrayTypeTest extends AbstractTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    @Test
    public void test() {
        Timestamp date1 = Timestamp.from(LocalDate.of(1991, 12, 31).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());
        Timestamp date2 = Timestamp.from(LocalDate.of(1990, 1, 1).atStartOfDay().atZone(ZoneId.systemDefault()).toInstant());

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorNames(new String[]{"Temperature", "Pressure"});
            event.setSensorValues(new int[]{12, 756});
            event.setSensorLongValues(new long[]{42L, 9223372036854775800L});
            event.setSensorDoubleValues(new double[]{0.123, 456.789});
            event.setSensorFloatValues(new float[]{1.23f, 45.789f});
            event.setDateValues(new Timestamp[]{date1, date2});
            event.setTimestampValues(new Timestamp[]{date1, date2});

            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames());
            assertArrayEquals(new int[]{12, 756}, event.getSensorValues());
            assertArrayEquals(new long[]{42L, 9223372036854775800L}, event.getSensorLongValues());
            assertArrayEquals(new double[]{0.123, 456.789}, event.getSensorDoubleValues(), 0.01);
            assertArrayEquals(new float[]{1.23f, 45.789f}, event.getSensorFloatValues(), 0.01f);
            assertArrayEquals(new Date[]{date1, date2}, event.getDateValues());
            assertArrayEquals(new Date[]{date1, date2}, event.getTimestampValues());
        });

        doInJPA(entityManager -> {
            List<Event> events = entityManager.createNativeQuery("""
                select
                   id,
                   sensor_names,
                   sensor_values,
                   date_values
                from event
                """, Tuple.class)
            .unwrap(org.hibernate.query.NativeQuery.class)
            .getResultList();

            assertEquals(2, events.size());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Column(name = "sensor_names", columnDefinition = "VARCHAR(20) ARRAY[10]")
        private String[] sensorNames;

        @Column(name = "sensor_values", columnDefinition = "INT ARRAY DEFAULT ARRAY[]")
        private int[] sensorValues;

        @Column(name = "sensor_long_values", columnDefinition = "BIGINT ARRAY DEFAULT ARRAY[]")
        private long[] sensorLongValues;

        @Column(name = "sensor_double_values", columnDefinition = "DOUBLE ARRAY DEFAULT ARRAY[]")
        private double[] sensorDoubleValues;

        @Column(name = "sensor_float_values", columnDefinition = "FLOAT ARRAY DEFAULT ARRAY[]")
        private float[] sensorFloatValues;

        @Column(name = "date_values", columnDefinition = "DATE ARRAY DEFAULT ARRAY[]")
        private Timestamp[] dateValues;

        @Column(name = "timestamp_values", columnDefinition = "TIMESTAMP ARRAY DEFAULT ARRAY[]")
        private Timestamp[] timestampValues;

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

        public Timestamp[] getDateValues() {
            return dateValues;
        }

        public void setDateValues(Timestamp[] dateValues) {
            this.dateValues = dateValues;
        }

        public Timestamp[] getTimestampValues() {
            return timestampValues;
        }

        public void setTimestampValues(Timestamp[] timestampValues) {
            this.timestampValues = timestampValues;
        }
    }
}
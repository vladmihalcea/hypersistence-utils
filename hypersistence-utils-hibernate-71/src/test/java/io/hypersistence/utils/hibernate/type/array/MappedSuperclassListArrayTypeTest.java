package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;

/**
 * @author Vlad Mihalcea
 */
public class MappedSuperclassListArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    @Test
    public void test() {

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorNames(Arrays.asList("Temperature", "Pressure"));
            event.setSensorValues(Arrays.asList(12, 756));
            event.setSensorShortValues(Arrays.asList((short) 42, (short) 69));

            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames().toArray());
            assertArrayEquals(new Integer[]{12, 756}, event.getSensorValues().toArray());
            assertArrayEquals(new Short[]{42, 69}, event.getSensorShortValues().toArray());
        });
    }

    @MappedSuperclass
    public static class BaseEntity {

        @Id
        private Long id;

        @Type(ListArrayType.class)
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private List<String> sensorNames;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public List<String> getSensorNames() {
            return sensorNames;
        }

        public void setSensorNames(List<String> sensorNames) {
            this.sensorNames = sensorNames;
        }
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(ListArrayType.class)
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private List<Integer> sensorValues;

        @Type(ListArrayType.class)
        @Column(name = "sensor_short_values", columnDefinition = "smallint[]")
        private List<Short> sensorShortValues;

        public List<Integer> getSensorValues() {
            return sensorValues;
        }

        public void setSensorValues(List<Integer> sensorValues) {
            this.sensorValues = sensorValues;
        }

        public List<Short> getSensorShortValues() {
            return sensorShortValues;
        }

        public void setSensorShortValues(List<Short> sensorShortValues) {
            this.sensorShortValues = sensorShortValues;
        }
    }
}
package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class ArrayTypeNativeQueryTest extends AbstractPostgreSQLIntegrationTest {

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
            event.setSensorNames(new String[]{"Temperature", "Pressure"});
            event.setSensorValues(new int[]{12, 756});
            entityManager.persist(event);
        });

        doInJPA(entityManager -> {
            List<EventSensors> events = entityManager
            .createNamedQuery("EventIdSensorValues", EventSensors.class)
            .setParameter("id", 0)
            .getResultList();

            assertEquals(2, events.size());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @NamedNativeQuery(
        name = "EventIdSensorValues",
        query = "select " +
                "   id, " +
                "   sensor_names, " +
                "   sensor_values " +
                "from event " +
                "where id >= :id",
        resultSetMapping = "EventIdSensorValues"
    )
    @SqlResultSetMapping(
        name = "EventIdSensorValues",
        classes = @ConstructorResult(
            targetClass = EventSensors.class,
            columns = {
                @ColumnResult(
                        name = "id",
                        type = Long.class
                ),
                @ColumnResult(
                        name = "sensor_names",
                        type = String[].class
                ),
                @ColumnResult(
                    name = "sensor_values",
                    type = int[].class
                )
            }
        )
    )
    public static class Event extends BaseEntity {

        @Type(StringArrayType.class)
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private String[] sensorNames;

        @Type(IntArrayType.class)
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private int[] sensorValues;

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
    }

    public static class EventSensors {

        private Long id;

        private String[] sensorNames;

        private int[] sensorValues;

        public EventSensors(Long id, String[] sensorNames, int[] sensorValues) {
            this.id = id;
            this.sensorNames = sensorNames;
            this.sensorValues = sensorValues;
        }

        public Long getId() {
            return id;
        }

        public String[] getSensorNames() {
            return sensorNames;
        }

        public int[] getSensorValues() {
            return sensorValues;
        }
    }
}

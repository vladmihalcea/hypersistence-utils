package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
import java.util.Arrays;
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

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL95ArrayDialect.class.getName();
            }
        };
    }

    @Override
    protected List<org.hibernate.type.Type> additionalTypes() {
        return Arrays.asList(
                StringArrayType.INSTANCE,
                IntArrayType.INSTANCE
        );
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
            .getResultList();

            assertEquals(2, events.size());
        });
    }

    @Entity(name = "Event")
    @TypeDef(name = "sensor-state-array", typeClass = EnumArrayType.class, parameters = {
        @Parameter(name = EnumArrayType.SQL_ARRAY_TYPE, value = "sensor_state")}
    )
    @Table(name = "event")
    @NamedNativeQuery(
        name = "EventIdSensorValues",
        query = "select " +
                "   id, " +
                "   sensor_names, " +
                "   sensor_values " +
                "from event ",
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

        @Type(type = "string-array")
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private String[] sensorNames;

        @Type(type = "int-array")
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

package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.fail;

/**
 * @author Vlad Mihalcea
 */
public class SimpleArrayTypeTest extends AbstractPostgreSQLIntegrationTest {

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
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Test
    public void test() {
        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            entityManager.persist(
                new Event()
                .setId(1L)
                .setSensorNames(new String[]{"Temperature", "Pressure"})
                .setSensorValues(new int[]{12, 756})
                .setSensorStates(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN})
            );
        });

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);

            assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames());
            assertArrayEquals(new int[]{12, 756}, event.getSensorValues());
            assertArrayEquals(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN}, event.getSensorStates());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        private Long id;

        @Type(StringArrayType.class)
        @Column(
            name = "sensor_names",
            columnDefinition = "text[]"
        )
        private String[] sensorNames;

        @Type(IntArrayType.class)
        @Column(
            name = "sensor_values",
            columnDefinition = "integer[]"
        )
        private int[] sensorValues;

        @Type(
            value = EnumArrayType.class,
            parameters = @Parameter(
                name = AbstractArrayType.SQL_ARRAY_TYPE,
                value = "sensor_state"
            )
        )
        @Column(
            name = "sensor_states",
            columnDefinition = "sensor_state[]"
        )
        private SensorState[] sensorStates;

        public Long getId() {
            return id;
        }

        public Event setId(Long id) {
            this.id = id;
            return this;
        }

        public String[] getSensorNames() {
            return sensorNames;
        }

        public Event setSensorNames(String[] sensorNames) {
            this.sensorNames = sensorNames;
            return this;
        }

        public int[] getSensorValues() {
            return sensorValues;
        }

        public Event setSensorValues(int[] sensorValues) {
            this.sensorValues = sensorValues;
            return this;
        }

        public SensorState[] getSensorStates() {
            return sensorStates;
        }

        public Event setSensorStates(SensorState[] sensorStates) {
            this.sensorStates = sensorStates;
            return this;
        }
    }

    public enum SensorState {
        ONLINE, OFFLINE, UNKNOWN;
    }
}

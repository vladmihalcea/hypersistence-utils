package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
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
    protected void beforeInit() {
        executeStatement("DROP TYPE sensor_state CASCADE");
        executeStatement("CREATE TYPE sensor_state AS ENUM ('ONLINE', 'OFFLINE', 'UNKNOWN')");
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
    @TypeDefs({
        @TypeDef(
            typeClass = StringArrayType.class,
            defaultForType = String[].class
        ),
        @TypeDef(
            typeClass = IntArrayType.class,
            defaultForType = int[].class
        ),
        @TypeDef(
            typeClass = EnumArrayType.class,
            defaultForType = SensorState[].class,
            parameters = {
                @Parameter(
                    name = AbstractArrayType.SQL_ARRAY_TYPE,
                    value = "sensor_state"
                )
            }
        )
    })
    public static class Event {

        @Id
        private Long id;

        @Column(
            name = "sensor_names",
            columnDefinition = "text[]"
        )
        private String[] sensorNames;

        @Column(
            name = "sensor_values",
            columnDefinition = "integer[]"
        )
        private int[] sensorValues;

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

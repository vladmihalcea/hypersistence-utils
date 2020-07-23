package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.PostgreSQLDataSourceProvider;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Date;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
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
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            Statement statement = null;
            try {
                statement = connection.createStatement();
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
            }
            finally {
                if (statement != null) {
                    try {
                        statement.close();
                    } catch (SQLException e) {
                        fail(e.getMessage());
                    }
                }
            }
        } catch (SQLException e) {
            fail(e.getMessage());
        } finally {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException e) {
                    fail(e.getMessage());
                }
            }
        }
        super.init();
    }

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL82ArrayDialect.class.getName();
            }
        };
    }

    @Test
    public void test() {

        final Date date1 = Timestamp.valueOf("1991-12-31 00:00:00");
        final Date date2 = Timestamp.valueOf("1990-01-01 00:00:00");

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
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
                event.setSensorStates(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN});
                event.setDateValues(new Date[]{date1, date2});
                event.setTimestampValues(new Date[]{date1, date2});
                event.setSensorBooleanValues(new Boolean[]{false, true, true});

                entityManager.persist(event);

                return null;
            }
        });
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}, event.getSensorIds());
                assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames());
                assertArrayEquals(new int[]{12, 756}, event.getSensorValues());
                assertArrayEquals(new long[]{42L, 9223372036854775800L}, event.getSensorLongValues());
                assertArrayEquals(new double[]{0.123, 456.789}, event.getSensorDoubleValues(), 0.01);
                assertArrayEquals(new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.ONLINE, SensorState.UNKNOWN}, event.getSensorStates());
                assertEquals(date1.getTime(), event.getDateValues()[0].getTime());
                assertEquals(date2.getTime(), event.getDateValues()[1].getTime());
                assertEquals(date1.getTime(), event.getTimestampValues()[0].getTime());
                assertEquals(date2.getTime(), event.getTimestampValues()[1].getTime());
                assertArrayEquals(new Boolean[]{false, true, true}, event.getSensorBooleanValues());

                return null;
            }
        });
    }

    @Test
    public void testLargeArray() {
        final int[] sensorValues = new int[100];

        Arrays.fill(sensorValues, 0, 10, 123);
        Arrays.fill(sensorValues, 10, 50, 456);
        Arrays.fill(sensorValues, 50, 90, 789);
        Arrays.fill(sensorValues, 90, 100, 0);

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = new Event();
                event.setId(0L);
                event.setSensorValues(sensorValues);

                entityManager.persist(event);

                return null;
            }
        });
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 0L);

                assertArrayEquals(sensorValues, event.getSensorValues());

                return null;
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @TypeDef(name = "sensor-state-array", typeClass = EnumArrayType.class, parameters = {
            @Parameter(name = EnumArrayType.SQL_ARRAY_TYPE, value = "sensor_state")}
    )
    public static class Event extends BaseEntity {
        @Type(type = "uuid-array")
        @Column(name = "sensor_ids", columnDefinition = "uuid[]")
        private UUID[] sensorIds;

        @Type(type = "string-array")
        @Column(name = "sensor_names", columnDefinition = "text[]")
        private String[] sensorNames;

        @Type(type = "int-array")
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private int[] sensorValues;

        @Type(type = "long-array")
        @Column(name = "sensor_long_values", columnDefinition = "bigint[]")
        private long[] sensorLongValues;

        @Type(type = "boolean-array")
        @Column(name = "sensor_boolean_values", columnDefinition = "boolean[]")
        private Boolean[] sensorBooleanValues;

        @Type(type = "double-array")
        @Column(name = "sensor_double_values", columnDefinition = "float8[]")
        private double[] sensorDoubleValues;

        @Type(type = "date-array")
        @Column(name = "date_values", columnDefinition = "date[]")
        private Date[] dateValues;

        @Type(type = "timestamp-array")
        @Column(name = "timestamp_values", columnDefinition = "timestamp[]")
        private Date[] timestampValues;

        @Type(type = "sensor-state-array")
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

        public Boolean[] getSensorBooleanValues() { return sensorBooleanValues; }

        public void setSensorBooleanValues(Boolean[] sensorBooleanValues) { this.sensorBooleanValues = sensorBooleanValues; }

        public double[] getSensorDoubleValues() {
            return sensorDoubleValues;
        }

        public void setSensorDoubleValues(double[] sensorDoubleValues) {
            this.sensorDoubleValues = sensorDoubleValues;
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
    }

    public enum SensorState {
        ONLINE, OFFLINE, UNKNOWN;
    }
}
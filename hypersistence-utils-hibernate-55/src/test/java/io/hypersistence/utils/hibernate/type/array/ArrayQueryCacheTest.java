package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.hibernate.util.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.PostgreSQLDataSourceProvider;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.hibernate.jpa.TypedParameterValue;
import org.hibernate.stat.Statistics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Table;
import javax.persistence.TypedQuery;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(Parameterized.class)
public class ArrayQueryCacheTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{Event.class,};
    }

    @Override
    public void init() {
        DataSource dataSource = newDataSource();
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            try {
                statement.executeUpdate("DROP TYPE sensor_state CASCADE");
            } catch (SQLException ignore) {
            }
            statement.executeUpdate("CREATE TYPE sensor_state AS ENUM ('ONLINE', 'OFFLINE', 'UNKNOWN')");
            statement.executeUpdate("CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\"");
        } catch (SQLException e) {
            fail(e.getMessage());
        }
        super.init();
    }

    @Override
    public void destroy() {
        entityManagerFactory().getCache().evictAll();
        super.destroy();
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

    protected void additionalProperties(Properties properties) {
        properties.setProperty("hibernate.cache.use_query_cache", "true");
        properties.setProperty("hibernate.cache.region.factory_class", "ehcache");
        properties.setProperty("hibernate.generate_statistics", "true");
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {"sensor_names", true, true},
                {"sensor_ids", true, true},
                {"sensor_values", true, true},
                {"sensor_values", false, true},
                {"sensor_long_values", true, true},
                {"sensor_long_values", false, true},
                {"sensor_states", false, true},

                {"sensor_names", true, false},
                {"sensor_ids", true, false},
                {"sensor_values", true, false},
                {"sensor_values", false, false},
                {"sensor_long_values", true, false},
                {"sensor_long_values", false, false},
                {"sensor_states", false, false},
        });
    }


    private final String column;
    private final boolean boxed;
    private final boolean positional;

    public ArrayQueryCacheTest(String column, boolean boxed, boolean positional) {
        this.column = column;
        this.boxed = boxed;
        this.positional = positional;
    }

    @Test
    public void test() {
        Statistics statistics = sessionFactory().getStatistics();

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Event event = new Event();
            event.setId(1L);
            event.setSensorIds(UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800"));
            event.setSensorNames("Temperature", "Pressure");
            event.setSensorValues(12, 756);
            event.setSensorLongValues(42L, 9223372036854775800L);
            event.setSensorStates(SensorState.ONLINE, SensorState.OFFLINE, SensorState.UNKNOWN);
            entityManager.persist(event);
        });

        assertEquals(0, statistics.getQueryCacheHitCount());
        assertEquals(0, statistics.getQueryCacheMissCount());
        assertEquals(0, statistics.getQueryCachePutCount());

        String hql = "SELECT id FROM Event WHERE " + column + " = " + (positional ? "?1" : ":p");
        Object parameter;
        switch (column) {
            case "sensor_names": {
                parameter = new TypedParameterValue(
                        StringArrayType.INSTANCE,
                        new String[]{"Temperature", "Pressure"}
                );
                break;
            }
            case "sensor_ids": {
                parameter = new TypedParameterValue(
                        UUIDArrayType.INSTANCE,
                        new UUID[]{UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"), UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")}
                );
                break;
            }
            case "sensor_values": {
                if (boxed) {
                    parameter = new TypedParameterValue(
                            IntArrayType.INSTANCE,
                            new Integer[]{12, 756}
                    );
                } else {
                    parameter = new TypedParameterValue(
                            IntArrayType.INSTANCE,
                            new int[]{12, 756}
                    );
                }
                break;
            }
            case "sensor_long_values": {
                if (boxed) {
                    parameter = new TypedParameterValue(
                            LongArrayType.INSTANCE,
                            new Long[]{42L, 9223372036854775800L}
                    );
                } else {
                    parameter = new TypedParameterValue(
                            LongArrayType.INSTANCE,
                            new long[]{42L, 9223372036854775800L}
                    );
                }
                break;
            }
            case "sensor_states": {
                parameter = new TypedParameterValue(
                        new EnumArrayType(SensorState.class, "sensor_state"),
                        new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.UNKNOWN}
                );
                break;
            }
            default: {
                throw new IllegalArgumentException(column);
            }
        }

        doInJPA(entityManager -> {
            TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
            if (positional) {
                query.setParameter(1, parameter);
            } else {
                query.setParameter("p", parameter);
            }
            List<Long> events = query.unwrap(org.hibernate.query.Query.class)
                    .setCacheable(true)
                    .getResultList();
            assertEquals(Collections.singletonList(1L), events);
        });

        assertEquals(0, statistics.getQueryCacheHitCount());
        assertEquals(1, statistics.getQueryCacheMissCount());
        assertEquals(1, statistics.getQueryCachePutCount());

        doInJPA(entityManager -> {
            TypedQuery<Long> query = entityManager.createQuery(hql, Long.class);
            if (positional) {
                query.setParameter(1, parameter);
            } else {
                query.setParameter("p", parameter);
            }
            List<Long> events = query.unwrap(org.hibernate.query.Query.class)
                    .setCacheable(true)
                    .getResultList();
            assertEquals(Collections.singletonList(1L), events);
        });

        assertEquals(1, statistics.getQueryCacheHitCount());
        assertEquals(1, statistics.getQueryCacheMissCount());
        assertEquals(1, statistics.getQueryCachePutCount());

        doInJPA(entityManager -> {
            TypedQuery<Long> query = entityManager.createQuery(hql + " AND 1 = 1", Long.class);
            if (positional) {
                query.setParameter(1, parameter);
            } else {
                query.setParameter("p", parameter);
            }
            List<Long> events = query.unwrap(org.hibernate.query.Query.class)
                    .setCacheable(true)
                    .getResultList();
            assertEquals(Collections.singletonList(1L), events);
        });

        assertEquals(1, statistics.getQueryCacheHitCount());
        assertEquals(2, statistics.getQueryCacheMissCount());
        assertEquals(2, statistics.getQueryCachePutCount());
    }

    @Entity(name = "Event")
    @Table(name = "event")
    @TypeDef(name = "sensor-state-array", typeClass = EnumArrayType.class, parameters = {@Parameter(name = AbstractArrayType.SQL_ARRAY_TYPE, value = "sensor_state")})
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
        private Long[] sensorLongValues;

        @Type(type = "sensor-state-array")
        @Column(name = "sensor_states", columnDefinition = "sensor_state[]")
        private SensorState[] sensorStates;

        public UUID[] getSensorIds() {
            return sensorIds;
        }

        public void setSensorIds(UUID... sensorIds) {
            this.sensorIds = sensorIds;
        }

        public String[] getSensorNames() {
            return sensorNames;
        }

        public void setSensorNames(String... sensorNames) {
            this.sensorNames = sensorNames;
        }

        public int[] getSensorValues() {
            return sensorValues;
        }

        public void setSensorValues(int... sensorValues) {
            this.sensorValues = sensorValues;
        }

        public Long[] getSensorLongValues() {
            return sensorLongValues;
        }

        public void setSensorLongValues(Long... sensorLongValues) {
            this.sensorLongValues = sensorLongValues;
        }

        public SensorState[] getSensorStates() {
            return sensorStates;
        }

        public void setSensorStates(SensorState... sensorStates) {
            this.sensorStates = sensorStates;
        }
    }

    public enum SensorState {
        ONLINE, OFFLINE, UNKNOWN;
    }
}

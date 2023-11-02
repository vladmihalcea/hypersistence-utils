package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.TypedQuery;
import org.hibernate.annotations.Parameter;
import org.hibernate.annotations.Type;
import org.hibernate.stat.Statistics;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

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

    protected void additionalProperties(Properties properties) {
        properties.setProperty("hibernate.cache.use_query_cache", "true");
        properties.setProperty("hibernate.cache.region.factory_class", "jcache");
        properties.setProperty("hibernate.generate_statistics", "true");
    }

    @Parameterized.Parameters
    public static Collection<Object[]> parameters() {
        return Arrays.asList(new Object[][]{
                {"sensorNames", true},
                {"sensorIds", true},
                {"sensorValues", true},
                {"sensorLongValues", true},
                {"sensorStates", true},

                {"sensorNames", false},
                {"sensorIds", false},
                {"sensorValues", false},
                {"sensorLongValues", false},
                {"sensorStates", false},
        });
    }


    private final String column;
    private final boolean positional;

    public ArrayQueryCacheTest(String column, boolean positional) {
        this.column = column;
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

        String hql = "select id from Event where " + column + " = " + (positional ? "?1" : ":p");
        Object parameter = switch (column) {
            case "sensorNames" -> new String[]{"Temperature", "Pressure"};
            case "sensorIds" -> new UUID[]{
                    UUID.fromString("c65a3bcb-8b36-46d4-bddb-ae96ad016eb1"),
                    UUID.fromString("72e95717-5294-4c15-aa64-a3631cf9a800")
            };
            case "sensorValues" -> new int[]{12, 756};
            case "sensorLongValues" -> new long[]{42L, 9223372036854775800L};
            case "sensorStates" -> new SensorState[]{SensorState.ONLINE, SensorState.OFFLINE, SensorState.UNKNOWN};
            default -> throw new IllegalArgumentException(column);
        };

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
    public static class Event extends BaseEntity {
        @Type(UUIDArrayType.class)
        @Column(columnDefinition = "uuid[]")
        private UUID[] sensorIds;

        @Type(StringArrayType.class)
        @Column(columnDefinition = "text[]")
        private String[] sensorNames;

        @Type(IntArrayType.class)
        @Column(columnDefinition = "integer[]")
        private int[] sensorValues;

        @Type(LongArrayType.class)
        @Column(columnDefinition = "bigint[]")
        private long[] sensorLongValues;

        @Type(
                value = EnumArrayType.class,
                parameters = @Parameter(name = EnumArrayType.SQL_ARRAY_TYPE, value = "sensor_state")
        )
        @Column(columnDefinition = "sensor_state[]")
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

        public long[] getSensorLongValues() {
            return sensorLongValues;
        }

        public void setSensorLongValues(long... sensorLongValues) {
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

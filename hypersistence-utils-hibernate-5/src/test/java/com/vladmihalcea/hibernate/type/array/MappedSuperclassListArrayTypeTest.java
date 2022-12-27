package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.PostgreSQLDataSourceProvider;
import com.vladmihalcea.hibernate.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;
import org.junit.Test;

import javax.persistence.*;
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

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider() {
            @Override
            public String hibernateDialect() {
                return PostgreSQL94ArrayDialect.class.getName();
            }
        };
    }

    @Test
    public void test() {

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event nullEvent = new Event();
                nullEvent.setId(0L);
                entityManager.persist(nullEvent);

                Event event = new Event();
                event.setId(1L);
                event.setSensorNames(Arrays.asList("Temperature", "Pressure"));
                event.setSensorValues(Arrays.asList(12, 756));

                entityManager.persist(event);

                return null;
            }
        });

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);

                assertArrayEquals(new String[]{"Temperature", "Pressure"}, event.getSensorNames().toArray());
                assertArrayEquals(new Integer[]{12, 756}, event.getSensorValues().toArray());

                return null;
            }
        });
    }

    @MappedSuperclass
    public static class BaseEntity {

        @Id
        private Long id;

        @Type(type = "list-array")
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
    @TypeDef(name = "list-array", typeClass = ListArrayType.class)
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(type = "list-array")
        @Column(name = "sensor_values", columnDefinition = "integer[]")
        private List<Integer> sensorValues;

        public List<Integer> getSensorValues() {
            return sensorValues;
        }

        public void setSensorValues(List<Integer> sensorValues) {
            this.sensorValues = sensorValues;
        }
    }
}

package com.vladmihalcea.hibernate.type.json.configuration;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeCustomJsonSerializerConfigurationTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class,
        };
    }

    @Override
    public void init() {
        System.setProperty(
            Configuration.PROPERTIES_FILE_PATH,
                "PostgreSQLJsonBinaryTypeCustomJsonSerializerConfigurationTest.properties"
        );
        super.init();
    }

    @Override
    public void destroy() {
        super.destroy();
        System.getProperties().remove(Configuration.PROPERTIES_FILE_PATH);
    }

    @Test
    public void test() {
        assertFalse(CustomJsonSerializer.isCalled());

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Location location = new Location();
                location.setCountry("Romania");
                location.setCity("Cluj-Napoca");
                location.setReference(BigDecimal.valueOf(2.25262562526626D));

                Event event = new Event();
                event.setId(1L);
                event.setLocation(location);
                entityManager.persist(event);

                return null;
            }
        });

        assertTrue(CustomJsonSerializer.isCalled());
        CustomJsonSerializer.reset();
        assertFalse(CustomJsonSerializer.isCalled());

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, 1L);
                assertEquals("2.25262562526626", event.getLocation().getReference().toString());

                return null;
            }
        });

        assertTrue(CustomJsonSerializer.isCalled());
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Location location;

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }
    }

    public static class Location implements Serializable {

        private String country;

        private String city;

        private BigDecimal reference;

        public String getCountry() {
            return country;
        }

        public void setCountry(String country) {
            this.country = country;
        }

        public String getCity() {
            return city;
        }

        public void setCity(String city) {
            this.city = city;
        }

        public BigDecimal getReference() {
            return reference;
        }

        public void setReference(BigDecimal reference) {
            this.reference = reference;
        }
    }
}

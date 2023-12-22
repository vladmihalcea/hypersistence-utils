package io.hypersistence.utils.hibernate.type.json.configuration;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;

import static org.junit.Assert.*;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeCustomJsonSerializerConfigurationJvmForkTest extends AbstractPostgreSQLIntegrationTest {

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

        doInJPA(entityManager -> {
            Location location = new Location();
            location.setCountry("Romania");
            location.setCity("Cluj-Napoca");
            location.setReference(BigDecimal.valueOf(2.25262562526626D));

            Event event = new Event();
            event.setId(1L);
            event.setLocation(location);
            entityManager.persist(event);
        });

        assertTrue(CustomJsonSerializer.isCalled());
        CustomJsonSerializer.reset();
        assertFalse(CustomJsonSerializer.isCalled());

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);
            assertEquals("2.25262562526626", event.getLocation().getReference().toString());
        });

        assertTrue(CustomJsonSerializer.isCalled());
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(JsonBinaryType.class)
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

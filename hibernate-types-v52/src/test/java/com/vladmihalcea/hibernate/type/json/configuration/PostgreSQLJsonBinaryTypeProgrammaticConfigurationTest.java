package com.vladmihalcea.hibernate.type.json.configuration;

import com.vladmihalcea.hibernate.type.json.JsonBinaryType;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeProgrammaticConfigurationTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class,
        };
    }

    @Override
    protected void additionalProperties(Properties properties) {
        CustomObjectMapperSupplier customObjectMapperSupplier = new CustomObjectMapperSupplier();
        JsonBinaryType jsonBinaryType = new JsonBinaryType(customObjectMapperSupplier.get(), Location.class);

        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) ->
                    typeContributions.contributeType(
                        jsonBinaryType, "location"
                    )
            )
        );
    }

    @Test
    public void test() {
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

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, 1L);
            assertEquals("2.25", event.getLocation().getReference().toString());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event {

        @Id
        private Long id;

        @Type(type = "location")
        @Column(columnDefinition = "jsonb")
        private Location location;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

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

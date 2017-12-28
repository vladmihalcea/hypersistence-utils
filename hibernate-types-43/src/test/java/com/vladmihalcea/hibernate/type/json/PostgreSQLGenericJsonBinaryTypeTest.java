package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.model.Location;
import com.vladmihalcea.hibernate.type.model.Ticket;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLGenericJsonBinaryTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
            Event.class
        };
    }

    @Test
    public void test() {
        final AtomicReference<Event> eventHolder = new AtomicReference<Event>();

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Location cluj = new Location();
                cluj.setCountry("Romania");
                cluj.setCity("Cluj-Napoca");

                Location newYork = new Location();
                newYork.setCountry("US");
                newYork.setCity("New-York");

                Location london = new Location();
                london.setCountry("UK");
                london.setCity("London");

                Event event = new Event();
                event.setId(1L);
                event.setLocation(cluj);
                event.setAlternativeLocations(Arrays.asList(newYork, london));

                entityManager.persist(event);

                eventHolder.set(event);
            return null;
            }
        });
        doInJPA(new JPATransactionFunction<Void>() {
            @Override
            public Void apply(EntityManager entityManager) {
            Event event = entityManager.find(Event.class, eventHolder.get().getId());
            assertEquals(2, event.getAlternativeLocations().size());

            assertEquals("New-York", event.getAlternativeLocations().get(0).getCity());
            assertEquals("London", event.getAlternativeLocations().get(1).getCity());
            return null;
            }
        });
    }


    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Location location;

        @Type(
            type = "jsonb",
            parameters = {
                @org.hibernate.annotations.Parameter(
                    name = TypeReferenceFactory.FACTORY_CLASS,
                    value = "com.vladmihalcea.hibernate.type.json.PostgreSQLGenericJsonBinaryTypeTest$AlternativeLocationsTypeReference"
                )
            }
        )
        @Column(columnDefinition = "jsonb")
        private List<Location> alternativeLocations = new ArrayList<Location>();

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }

        public List<Location> getAlternativeLocations() {
            return alternativeLocations;
        }

        public void setAlternativeLocations(List<Location> alternativeLocations) {
            this.alternativeLocations = alternativeLocations;
        }
    }

    public static class AlternativeLocationsTypeReference implements TypeReferenceFactory {
        @Override
        public TypeReference<?> newTypeReference() {
            return new TypeReference<List<Location>>() {};
        }
    }
}

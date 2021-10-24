package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.model.Location;
import com.vladmihalcea.hibernate.util.AbstractPostgreSQLIntegrationTest;
import com.vladmihalcea.hibernate.util.transaction.JPATransactionFunction;
import net.ttddyy.dsproxy.QueryCountHolder;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EntityManager;
import javax.persistence.Table;
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

        QueryCountHolder.clear();
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
        assertEquals(1, QueryCountHolder.getGrandTotal().getSelect());
        assertEquals(0, QueryCountHolder.getGrandTotal().getUpdate());
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Location location;

        @Type(
            type = "jsonb"
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
}

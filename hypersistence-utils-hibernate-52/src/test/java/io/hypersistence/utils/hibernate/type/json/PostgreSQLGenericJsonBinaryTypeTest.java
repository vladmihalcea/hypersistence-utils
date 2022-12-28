package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.model.Location;
import io.hypersistence.utils.hibernate.type.model.Ticket;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLGenericJsonBinaryTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class,
                Participant.class
        };
    }

    private Event _event;

    @Override
    protected void afterInit() {
        doInJPA(entityManager -> {
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

            _event = event;
        });
    }

    @Test
    public void test() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, _event.getId());

            assertEquals("Cluj-Napoca", event.getLocation().getCity());

            assertEquals(2, event.getAlternativeLocations().size());
            assertEquals("New-York", event.getAlternativeLocations().get(0).getCity());
            assertEquals("London", event.getAlternativeLocations().get(1).getCity());
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertSelectCount(1);
        SQLStatementCountValidator.assertUpdateCount(0);
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
        private List<Location> alternativeLocations;

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

    @Entity(name = "Participant")
    @Table(name = "participant")
    public static class Participant extends BaseEntity {

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private Ticket ticket;

        @ManyToOne
        private Event event;

        public Ticket getTicket() {
            return ticket;
        }

        public void setTicket(Ticket ticket) {
            this.ticket = ticket;
        }

        public Event getEvent() {
            return event;
        }

        public void setEvent(Event event) {
            this.event = event;
        }
    }
}

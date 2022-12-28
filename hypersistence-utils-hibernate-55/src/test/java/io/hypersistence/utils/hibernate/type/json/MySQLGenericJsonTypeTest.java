package io.hypersistence.utils.hibernate.type.json;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.model.Location;
import io.hypersistence.utils.hibernate.type.model.Ticket;
import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
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
public class MySQLGenericJsonTypeTest extends AbstractMySQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class,
                Participant.class
        };
    }

    @Override
    protected String[] packages() {
        return new String[]{
                Location.class.getPackage().getName()
        };
    }

    private Event _event;

    @Override
    protected void afterInit() {
        doInJPA(entityManager -> {
            Location location = new Location();
            location.setCountry("Romania");
            location.setCity("Cluj-Napoca");

            Event event = new Event();
            event.setId(1L);
            event.setAlternativeLocations(Arrays.asList(location));
            entityManager.persist(event);

            _event = event;
        });
    }

    @Test
    public void test() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, _event.getId());
            assertEquals(1, event.getAlternativeLocations().size());
            assertEquals("Cluj-Napoca", event.getAlternativeLocations().get(0).getCity());
            assertEquals("Romania", event.getAlternativeLocations().get(0).getCountry());
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertSelectCount(1);
        SQLStatementCountValidator.assertUpdateCount(0);
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(type = "json")
        @Column(columnDefinition = "json")
        private Location location;

        @Type(
            type = "json"
        )
        @Column(columnDefinition = "json")
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

        @Type(type = "json")
        @Column(columnDefinition = "json")
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

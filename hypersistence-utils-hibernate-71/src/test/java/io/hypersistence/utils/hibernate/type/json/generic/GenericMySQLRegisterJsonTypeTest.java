package io.hypersistence.utils.hibernate.type.json.generic;

import io.hypersistence.utils.hibernate.type.json.JsonType;
import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import io.hypersistence.utils.hibernate.type.model.Location;
import io.hypersistence.utils.hibernate.type.model.Ticket;
import io.hypersistence.utils.hibernate.util.AbstractMySQLIntegrationTest;
import io.hypersistence.utils.jdbc.validator.SQLStatementCountValidator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Type;
import org.hibernate.jpa.boot.spi.TypeContributorList;
import org.junit.Test;

import java.util.Collections;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class GenericMySQLRegisterJsonTypeTest extends AbstractMySQLIntegrationTest {

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

    @Override
    protected void additionalProperties(Properties properties) {
        JsonType locationJsonType = new JsonType(Location.class);

        properties.put("hibernate.type_contributors",
            (TypeContributorList) () -> Collections.singletonList(
                (typeContributions, serviceRegistry) ->
                    typeContributions.contributeType(locationJsonType)
            )
        );
    }


    private Event _event;

    private Participant _participant;

    @Override
    protected void afterInit() {

        doInJPA(entityManager -> {
            Event nullEvent = new Event();
            nullEvent.setId(0L);
            entityManager.persist(nullEvent);

            Location location = new Location();
            location.setCountry("Romania");
            location.setCity("Cluj-Napoca");

            Event event = new Event();
            event.setId(1L);
            event.setLocation(location);
            entityManager.persist(event);

            Ticket ticket = new Ticket();
            ticket.setPrice(12.34d);
            ticket.setRegistrationCode("ABC123");

            Participant participant = new Participant();
            participant.setId(1L);
            participant.setTicket(ticket);
            participant.setEvent(event);

            entityManager.persist(participant);

            _event = event;
            _participant = participant;
        });
    }

    @Test
    public void testLoad() {
        SQLStatementCountValidator.reset();

        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, _event.getId());
            assertEquals("Romania", event.getLocation().getCountry());
            assertEquals("Cluj-Napoca", event.getLocation().getCity());
        });

        SQLStatementCountValidator.assertTotalCount(1);
        SQLStatementCountValidator.assertSelectCount(1);
        SQLStatementCountValidator.assertUpdateCount(0);
    }

    @Test
    public void testBulkUpdateJpqlQuery() {
        doInJPA(entityManager -> {
            Location location = new Location();
            location.setCountry("Romania");
            location.setCity("Sibiu");

            entityManager.createQuery(
                "update Event " +
                "set location = :location " +
                "where id = :id")
            .setParameter("id", _event.getId())
            .setParameter("location", location)
            .executeUpdate();

            Event event = entityManager.find(Event.class, _event.getId());
            assertEquals("Sibiu", event.getLocation().getCity());
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(JsonType.class)
        @Column(columnDefinition = "json")
        private Location location;

        public Location getLocation() {
            return location;
        }

        public void setLocation(Location location) {
            this.location = location;
        }
    }

    @Entity(name = "Participant")
    @Table(name = "participant")
    public static class Participant extends BaseEntity {

        @Type(JsonType.class)
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

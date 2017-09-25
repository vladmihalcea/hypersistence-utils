package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.type.json.model.BaseEntity;
import com.vladmihalcea.hibernate.type.json.model.Location;
import com.vladmihalcea.hibernate.type.json.model.Ticket;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeTest extends AbstractPostgreSQLIntegrationTest {

    @Override
    protected Class<?>[] entities() {
        return new Class<?>[]{
                Event.class,
                Participant.class
        };
    }

    @Test
    public void test() {
        final AtomicReference<Event> eventHolder = new AtomicReference<>();
        final AtomicReference<Participant> participantHolder = new AtomicReference<>();

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

            eventHolder.set(event);
            participantHolder.set(participant);
        });
        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, eventHolder.get().getId());
            assertEquals("Cluj-Napoca", event.getLocation().getCity());

            Participant participant = entityManager.find(Participant.class, participantHolder.get().getId());
            assertEquals("ABC123", participant.getTicket().getRegistrationCode());

            List<String> participants = entityManager.createNativeQuery(
                    "select jsonb_pretty(p.ticket) " +
                            "from participant p " +
                            "where p.ticket ->> 'price' > '10'")
                    .getResultList();

            participants = entityManager.createNativeQuery(
                    "select jsonb_pretty(p.ticket) " +
                            "from participant p " +
                            "where p.ticket ->> 'price' > :price")
                    .setParameter("price", "10")
                    .getResultList();

            event.getLocation().setCity("Constanța");
            assertEquals(Integer.valueOf(0), event.getVersion());
            entityManager.flush();
            assertEquals(Integer.valueOf(1), event.getVersion());

            assertEquals(1, participants.size());
        });
        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, eventHolder.get().getId());
            event.getLocation().setCity(null);
            assertEquals(Integer.valueOf(1), event.getVersion());
            entityManager.flush();
            assertEquals(Integer.valueOf(2), event.getVersion());
        });
        doInJPA(entityManager -> {
            Event event = entityManager.find(Event.class, eventHolder.get().getId());
            event.setLocation(null);
            assertEquals(Integer.valueOf(2), event.getVersion());
            entityManager.flush();
            assertEquals(Integer.valueOf(3), event.getVersion());
        });
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

package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.type.model.BaseEntity;
import com.vladmihalcea.hibernate.type.model.Location;
import com.vladmihalcea.hibernate.type.model.Ticket;
import com.vladmihalcea.hibernate.type.util.AbstractMySQLIntegrationTest;
import com.vladmihalcea.hibernate.type.util.transaction.JPATransactionFunction;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class MySQLJsonTypeTest extends AbstractMySQLIntegrationTest {

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

    @Test
    public void test() {
        final AtomicReference<Event> eventHolder = new AtomicReference<Event>();
        final AtomicReference<Participant> participantHolder = new AtomicReference<Participant>();

        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
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

                return null;
            }
        });
        doInJPA(new JPATransactionFunction<Void>() {

            @Override
            public Void apply(EntityManager entityManager) {
                Event event = entityManager.find(Event.class, eventHolder.get().getId());
                assertEquals("Cluj-Napoca", event.getLocation().getCity());

                Participant participant = entityManager.find(Participant.class, participantHolder.get().getId());
                assertEquals("ABC123", participant.getTicket().getRegistrationCode());

                List<String> participants = entityManager.createNativeQuery(
                        "select p.ticket -> \"$.registrationCode\" " +
                                "from participant p " +
                                "where JSON_EXTRACT(p.ticket, \"$.price\") > 1 ")
                        .getResultList();

                event.getLocation().setCity("Constanța");
                entityManager.flush();

                assertEquals(1, participants.size());

                return null;
            }
        });
    }

    @Entity(name = "Event")
    @Table(name = "event")
    public static class Event extends BaseEntity {

        @Type(type = "json")
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

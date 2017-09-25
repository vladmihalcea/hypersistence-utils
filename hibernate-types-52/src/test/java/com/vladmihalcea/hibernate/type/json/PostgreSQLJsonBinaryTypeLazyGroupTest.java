package com.vladmihalcea.hibernate.type.json;

import com.vladmihalcea.hibernate.type.model.Event;
import com.vladmihalcea.hibernate.type.model.Location;
import com.vladmihalcea.hibernate.type.model.Participant;
import com.vladmihalcea.hibernate.type.model.Ticket;
import com.vladmihalcea.hibernate.type.util.AbstractPostgreSQLIntegrationTest;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLJsonBinaryTypeLazyGroupTest extends AbstractPostgreSQLIntegrationTest {

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
            LOGGER.debug("Fetched event");
            assertEquals("Cluj-Napoca", event.getLocation().getCity());

            Participant participant = entityManager.find(Participant.class, participantHolder.get().getId());
            LOGGER.debug("Fetched participant");
            assertEquals("ABC123", participant.getTicket().getRegistrationCode());
        });
    }
}

package io.hypersistence.utils.hibernate.type.model;

import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.*;
import org.hibernate.annotations.Type;

/**
 * @author Vlad Mihalcea
 */
@Entity(name = "Participant")
@Table(name = "participant")
public class Participant extends BaseEntity {

    @Type(JsonBinaryType.class)
    @Column(columnDefinition = "jsonb")
    @Basic(fetch = FetchType.LAZY)
    private Ticket ticket;

    @ManyToOne(fetch = FetchType.LAZY)
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
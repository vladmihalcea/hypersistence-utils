package com.vladmihalcea.hibernate.type.model;

import org.hibernate.annotations.Type;

import javax.persistence.*;

/**
 * @author Vlad Mihalcea
 */
@Entity(name = "Participant")
@Table(name = "participant")
public class Participant extends BaseEntity {

    @Type(type = "jsonb")
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
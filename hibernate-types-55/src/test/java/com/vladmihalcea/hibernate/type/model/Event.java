package com.vladmihalcea.hibernate.type.model;

import org.hibernate.annotations.Type;

import javax.persistence.*;

/**
 * @author Vlad Mihalcea
 */
@Entity(name = "Event")
@Table(name = "event")
public class Event extends BaseEntity {

    @Type(type = "jsonb")
    @Column(columnDefinition = "jsonb")
    @Basic(fetch = FetchType.LAZY)
    private Location location;

    public Location getLocation() {
        return location;
    }

    public void setLocation(Location location) {
        this.location = location;
    }
}
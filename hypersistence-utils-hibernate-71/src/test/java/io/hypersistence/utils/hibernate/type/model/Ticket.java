package io.hypersistence.utils.hibernate.type.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * @author Vlad Mihalcea
 */
public class Ticket implements Serializable {

    private String registrationCode;

    private double price;

    public String getRegistrationCode() {
        return registrationCode;
    }

    public void setRegistrationCode(String registrationCode) {
        this.registrationCode = registrationCode;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        this.price = price;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ticket ticket = (Ticket) o;
        return Double.compare(ticket.price, price) == 0 &&
            Objects.equals(registrationCode, ticket.registrationCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(registrationCode, price);
    }
}

package io.hypersistence.utils.hibernate.type.model;

import java.io.Serializable;

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

        if (Double.compare(ticket.price, price) != 0) return false;
        return registrationCode != null ? registrationCode.equals(ticket.registrationCode) : ticket.registrationCode == null;
    }

    @Override
    public int hashCode() {
        int result;
        long temp;
        result = registrationCode != null ? registrationCode.hashCode() : 0;
        temp = Double.doubleToLongBits(price);
        result = 31 * result + (int) (temp ^ (temp >>> 32));
        return result;
    }
}

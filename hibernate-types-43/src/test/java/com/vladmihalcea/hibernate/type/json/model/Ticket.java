package com.vladmihalcea.hibernate.type.json.model;

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
}

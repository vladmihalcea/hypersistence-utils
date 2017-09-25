package com.vladmihalcea.hibernate.type.json.model;

import java.io.Serializable;

/**
 * @author Vlad Mihalcea
 */
public class Location implements Serializable {

    private String country;

    private String city;

    public String getCountry() {
        return country;
    }

    public void setCountry(String country) {
        this.country = country;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }
}

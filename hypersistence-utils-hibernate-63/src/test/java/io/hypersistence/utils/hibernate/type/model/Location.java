package io.hypersistence.utils.hibernate.type.model;

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil;

import java.io.Serializable;
import java.util.Objects;

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

    @Override
    public String toString() {
        return JacksonUtil.toString(this);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Location location = (Location) o;
        return Objects.equals(country, location.country) &&
            Objects.equals(city, location.city);
    }

    @Override
    public int hashCode() {
        return Objects.hash(country, city);
    }
}

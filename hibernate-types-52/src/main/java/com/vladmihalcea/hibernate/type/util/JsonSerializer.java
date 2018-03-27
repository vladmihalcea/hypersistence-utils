package com.vladmihalcea.hibernate.type.util;

/**
 * Contract for serializing JSON objects.
 *
 * @author Vlad Mihalcea
 */
public interface JsonSerializer {

    /**
     * Clone JSON object.
     *
     * @param jsonObject JSON object
     * @param <T> JSON object parameterized type
     * @return cloned JSON object
     */
    <T> T clone(T jsonObject);
}

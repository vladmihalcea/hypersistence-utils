package io.hypersistence.utils.hibernate.type.util;

import java.io.Serializable;

/**
 * Contract for serializing JSON objects.
 *
 * @author Vlad Mihalcea
 */
public interface JsonSerializer extends Serializable {

    /**
     * Clone JSON object.
     *
     * @param jsonObject JSON object
     * @param <T> JSON object parameterized type
     * @return cloned JSON object
     */
    <T> T clone(T jsonObject);
}

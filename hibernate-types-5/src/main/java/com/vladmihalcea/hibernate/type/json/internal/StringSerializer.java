package com.vladmihalcea.hibernate.type.json.internal;

/**
 * @author Fabio Grucci
 */
interface StringSerializer<T> {

    T fromString(String string);

    String toString(T value);
}

package com.vladmihalcea.hibernate.type.json.internal;

/**
 * @author Vlad Mihalcea
 */
public abstract class JsonStringSerializer<T> implements StringSerializer<T> {

    @Override
    public String toString(T value) {
        return JacksonUtil.toString(value);
    }
}

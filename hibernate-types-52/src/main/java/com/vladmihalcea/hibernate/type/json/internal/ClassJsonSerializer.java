package com.vladmihalcea.hibernate.type.json.internal;


public class ClassJsonSerializer<T> implements JsonSerializer<T> {

    private final Class<T> clazz;

    public ClassJsonSerializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public T fromString(String string) {
        return JacksonUtil.fromString(string, clazz);
    }
}

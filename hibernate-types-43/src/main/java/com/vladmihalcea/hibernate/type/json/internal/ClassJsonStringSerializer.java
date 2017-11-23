package com.vladmihalcea.hibernate.type.json.internal;

/**
 * @author Fabio Grucci
 */
public class ClassJsonStringSerializer<T> extends JsonStringSerializer<T> {

    private final Class<T> clazz;

    public ClassJsonStringSerializer(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public T fromString(String string) {
        return JacksonUtil.fromString(string, clazz);
    }
}

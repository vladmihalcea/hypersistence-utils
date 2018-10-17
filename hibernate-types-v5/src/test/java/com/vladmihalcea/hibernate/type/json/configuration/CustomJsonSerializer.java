package com.vladmihalcea.hibernate.type.json.configuration;

import com.vladmihalcea.hibernate.type.json.internal.JacksonUtil;
import com.vladmihalcea.hibernate.type.util.JsonSerializer;

/**
 * @author Vlad Mihalcea
 */
public class CustomJsonSerializer implements JsonSerializer {

    private static boolean called;

    public static boolean isCalled() {
        return called;
    }

    public static void reset() {
        called = false;
    }

    @Override
    public <T> T clone(T value) {
        called = true;
        return JacksonUtil.clone(value);
    }
}

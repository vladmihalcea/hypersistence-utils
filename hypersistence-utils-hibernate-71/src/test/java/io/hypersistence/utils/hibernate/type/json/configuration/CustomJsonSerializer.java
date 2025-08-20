package io.hypersistence.utils.hibernate.type.json.configuration;

import io.hypersistence.utils.hibernate.type.json.internal.JacksonUtil;
import io.hypersistence.utils.hibernate.type.util.JsonSerializer;

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

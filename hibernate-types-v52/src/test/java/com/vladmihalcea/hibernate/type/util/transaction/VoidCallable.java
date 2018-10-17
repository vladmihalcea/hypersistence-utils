package com.vladmihalcea.hibernate.type.util.transaction;

import java.util.concurrent.Callable;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface VoidCallable extends Callable<Void> {

    void execute();

    default Void call() throws Exception {
        execute();
        return null;
    }
}

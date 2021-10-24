package com.vladmihalcea.hibernate.util.transaction;

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

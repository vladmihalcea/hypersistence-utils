package io.hypersistence.utils.test.transaction;

import java.util.concurrent.Callable;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface VoidCallable extends Callable<Void> {

    void execute();

    default Void call() {
        execute();
        return null;
    }
}

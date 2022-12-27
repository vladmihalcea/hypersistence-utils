package io.hypersistence.utils.hibernate.util.transaction;

import java.util.concurrent.Callable;

/**
 * @author Vlad Mihalcea
 */
public abstract class VoidCallable implements Callable<Void> {

    public abstract void execute();

    public Void call() throws Exception {
        execute();
        return null;
    }
}

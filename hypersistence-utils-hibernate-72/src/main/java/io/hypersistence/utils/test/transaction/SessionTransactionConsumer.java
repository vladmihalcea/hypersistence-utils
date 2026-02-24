package io.hypersistence.utils.test.transaction;

import org.hibernate.Session;

import java.util.function.Consumer;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface SessionTransactionConsumer extends Consumer<Session> {
    default void beforeTransactionCompletion() {

    }

    default void afterTransactionCompletion() {

    }
}

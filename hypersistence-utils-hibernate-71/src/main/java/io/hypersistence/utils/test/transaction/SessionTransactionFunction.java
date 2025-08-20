package io.hypersistence.utils.test.transaction;

import org.hibernate.Session;

import java.util.function.Function;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface SessionTransactionFunction<T> extends Function<Session, T> {
    default void beforeTransactionCompletion() {

    }

    default void afterTransactionCompletion() {

    }
}

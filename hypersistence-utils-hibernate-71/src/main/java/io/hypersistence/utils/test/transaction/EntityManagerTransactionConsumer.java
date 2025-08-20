package io.hypersistence.utils.test.transaction;

import jakarta.persistence.EntityManager;

import java.util.function.Consumer;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface EntityManagerTransactionConsumer extends Consumer<EntityManager> {
    default void beforeTransactionCompletion() {

    }

    default void afterTransactionCompletion() {

    }
}

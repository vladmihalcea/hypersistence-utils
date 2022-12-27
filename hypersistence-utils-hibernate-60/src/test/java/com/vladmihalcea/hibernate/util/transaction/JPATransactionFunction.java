package com.vladmihalcea.hibernate.util.transaction;

import jakarta.persistence.EntityManager;

import java.util.function.Function;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface JPATransactionFunction<T> extends Function<EntityManager, T> {
    default void beforeTransactionCompletion() {

    }

    default void afterTransactionCompletion() {

    }
}

package io.hypersistence.utils.test.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface ConnectionTransactionFunction<T> {

    T execute(Connection connection) throws SQLException;

    default void beforeTransactionCompletion() {

    }

    default void afterTransactionCompletion() {

    }
}

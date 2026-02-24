package io.hypersistence.utils.test.transaction;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.function.Function;

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

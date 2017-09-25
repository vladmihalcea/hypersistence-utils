package com.vladmihalcea.hibernate.type.util.transaction;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * @author Vlad Mihalcea
 */
@FunctionalInterface
public interface ConnectionVoidCallable {
    void execute(Connection connection) throws SQLException;
}

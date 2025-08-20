package io.hypersistence.utils.test.providers;

import org.hibernate.dialect.Database;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public interface DataSourceProvider {

    Database database();

    String hibernateDialect();

    DataSource dataSource();

    String url();

    String username();

    String password();

    default JdbcDatabaseContainer newJdbcDatabaseContainer() {
        throw new UnsupportedOperationException(
            String.format(
                "The [%s] database was not configured to use Testcontainers!",
                database()
            )
        );
    }

    default boolean supportsDatabaseName() {
        return true;
    }

    default boolean supportsCredentials() {
        return true;
    }
}
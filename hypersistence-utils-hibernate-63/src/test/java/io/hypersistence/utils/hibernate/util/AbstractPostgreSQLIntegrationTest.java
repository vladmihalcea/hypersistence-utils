package io.hypersistence.utils.hibernate.util;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.PostgreSQLDataSourceProvider;

/**
 * AbstractPostgreSQLIntegrationTest - Abstract PostgreSQL IntegrationTest
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractPostgreSQLIntegrationTest extends AbstractTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider();
    }
}

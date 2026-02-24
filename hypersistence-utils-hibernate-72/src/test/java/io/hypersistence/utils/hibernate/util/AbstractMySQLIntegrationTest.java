package io.hypersistence.utils.hibernate.util;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.MySQLDataSourceProvider;

/**
 * AbstractMySQLIntegrationTest - Abstract MySQL IntegrationTest
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractMySQLIntegrationTest extends AbstractTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new MySQLDataSourceProvider();
    }
}

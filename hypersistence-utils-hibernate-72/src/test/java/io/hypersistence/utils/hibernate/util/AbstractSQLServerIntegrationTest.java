package io.hypersistence.utils.hibernate.util;

import io.hypersistence.utils.hibernate.util.AbstractTest;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.SQLServerDataSourceProvider;

/**
 * AbstractSQLServerIntegrationTest - Abstract SQL Server IntegrationTest
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractSQLServerIntegrationTest extends AbstractTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new SQLServerDataSourceProvider();
    }
}

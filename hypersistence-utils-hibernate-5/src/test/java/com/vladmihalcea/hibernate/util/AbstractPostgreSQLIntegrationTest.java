package com.vladmihalcea.hibernate.util;

import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.PostgreSQLDataSourceProvider;

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

package com.vladmihalcea.hibernate.type.util;

import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.MySQLDataSourceProvider;

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

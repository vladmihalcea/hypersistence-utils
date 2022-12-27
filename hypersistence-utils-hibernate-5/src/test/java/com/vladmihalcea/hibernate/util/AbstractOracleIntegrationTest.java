package com.vladmihalcea.hibernate.util;

import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.OracleDataSourceProvider;

/**
 * AbstractOracleIntegrationTest - Abstract Oracle IntegrationTest
 *
 * @author Vlad Mihalcea
 */
public abstract class AbstractOracleIntegrationTest extends AbstractTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new OracleDataSourceProvider();
    }
}

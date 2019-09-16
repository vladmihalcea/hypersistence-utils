package com.vladmihalcea.hibernate.type.util;

import com.vladmihalcea.hibernate.type.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.type.util.providers.OracleDataSourceProvider;
import org.junit.After;

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

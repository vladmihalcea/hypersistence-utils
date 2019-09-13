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

    public void init() {
        if(isOracle()) {
            super.init();
        }
    }

    @After
    public void destroy() {
        if(isOracle()) {
            super.destroy();
        }
    }

    protected boolean isOracle() {
        return ReflectionUtils.getClassOrNull("oracle.jdbc.pool.OracleDataSource") != null;
    }
}

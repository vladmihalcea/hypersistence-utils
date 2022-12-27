package com.vladmihalcea.hibernate.id;

import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.OracleDataSourceProvider;

/**
 * @author Philippe Marschall
 */
public class OracleBatchSequenceGeneratorTest extends AbstractBatchSequenceGeneratorTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new OracleDataSourceProvider();
    }

}

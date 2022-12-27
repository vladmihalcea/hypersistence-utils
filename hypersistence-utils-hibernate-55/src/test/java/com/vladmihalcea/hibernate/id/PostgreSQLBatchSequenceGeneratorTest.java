package com.vladmihalcea.hibernate.id;

import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.PostgreSQLDataSourceProvider;

/**
 * @author Philippe Marschall
 */
public class PostgreSQLBatchSequenceGeneratorTest extends AbstractBatchSequenceGeneratorTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider();
    }

}

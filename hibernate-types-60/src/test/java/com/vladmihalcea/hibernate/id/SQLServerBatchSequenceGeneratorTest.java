package com.vladmihalcea.hibernate.id;

import com.vladmihalcea.hibernate.util.providers.DataSourceProvider;
import com.vladmihalcea.hibernate.util.providers.SQLServerDataSourceProvider;

/**
 * @author Philippe Marschall
 */
public class SQLServerBatchSequenceGeneratorTest extends AbstractBatchSequenceGeneratorTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new SQLServerDataSourceProvider();
    }

}

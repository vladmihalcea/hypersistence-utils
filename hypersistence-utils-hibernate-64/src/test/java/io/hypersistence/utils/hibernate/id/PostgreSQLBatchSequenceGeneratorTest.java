package io.hypersistence.utils.hibernate.id;

import io.hypersistence.utils.test.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.PostgreSQLDataSourceProvider;

/**
 * @author Philippe Marschall
 */
public class PostgreSQLBatchSequenceGeneratorTest extends AbstractBatchSequenceGeneratorTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new PostgreSQLDataSourceProvider();
    }

}

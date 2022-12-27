package io.hypersistence.utils.hibernate.id;

import io.hypersistence.utils.hibernate.util.providers.DataSourceProvider;
import io.hypersistence.utils.hibernate.util.providers.SQLServerDataSourceProvider;

/**
 * @author Philippe Marschall
 */
public class SQLServerBatchSequenceGeneratorTest extends AbstractBatchSequenceGeneratorTest {

    @Override
    protected DataSourceProvider dataSourceProvider() {
        return new SQLServerDataSourceProvider();
    }

}

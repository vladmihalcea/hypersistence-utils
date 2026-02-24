package io.hypersistence.utils.hibernate.util.providers;

import io.hypersistence.utils.test.providers.DataSourceProvider;
import io.hypersistence.utils.test.providers.DataSourceProviderSupplier;
import org.hibernate.dialect.Database;

import java.util.Map;

/**
 * @author Vlad Mihalcea
 */
public class DataSourceProviderSupplierImpl implements DataSourceProviderSupplier {

    @Override
    public Map<Database, DataSourceProvider> get() {
        return Map.of(
            Database.H2, H2DataSourceProvider.INSTANCE,
            Database.HSQL, HSQLDBDataSourceProvider.INSTANCE,
            Database.MYSQL, MySQLDataSourceProvider.INSTANCE,
            Database.ORACLE, OracleDataSourceProvider.INSTANCE,
            Database.POSTGRESQL, PostgreSQLDataSourceProvider.INSTANCE,
            Database.SQLSERVER, SQLServerDataSourceProvider.INSTANCE
        );
    }
}

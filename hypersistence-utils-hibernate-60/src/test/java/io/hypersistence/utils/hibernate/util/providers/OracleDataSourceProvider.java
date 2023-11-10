package io.hypersistence.utils.hibernate.util.providers;

import io.hypersistence.utils.test.providers.AbstractContainerDataSourceProvider;
import io.hypersistence.utils.test.providers.DataSourceProvider;
import oracle.jdbc.pool.OracleDataSource;
import org.hibernate.dialect.Database;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorNoOpImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.OracleContainer;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public class OracleDataSourceProvider extends AbstractContainerDataSourceProvider {

    public static final DataSourceProvider INSTANCE = new OracleDataSourceProvider();

    @Override
    public Database database() {
        return Database.ORACLE;
    }

    @Override
    public String hibernateDialect() {
        return FastOracleDialect.class.getName();
    }

    @Override
    public String defaultJdbcUrl() {
        return "jdbc:oracle:thin:@localhost:1521/xe";
    }

    @Override
    public DataSource newDataSource() {
        try {
            OracleDataSource dataSource = new OracleDataSource();
            JdbcDatabaseContainer container = getContainer();
            if(container == null) {
                dataSource.setDatabaseName("high_performance_java_persistence");
            } else {
                dataSource.setDatabaseName(container.getDatabaseName());
            }
            dataSource.setURL(url());
            dataSource.setUser(username());
            dataSource.setPassword(password());
            return dataSource;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String username() {
        return "oracle";
    }

    @Override
    public String password() {
        return "admin";
    }

    @Override
    public JdbcDatabaseContainer newJdbcDatabaseContainer() {
        return new OracleContainer("gvenzl/oracle-xe:21.3.0-slim");
    }

    @Override
    public boolean supportsDatabaseName() {
        return false;
    }

    public static class FastOracleDialect extends OracleDialect {
        @Override
        public SequenceInformationExtractor getSequenceInformationExtractor() {
            return SequenceInformationExtractorNoOpImpl.INSTANCE;
        }
    }
}

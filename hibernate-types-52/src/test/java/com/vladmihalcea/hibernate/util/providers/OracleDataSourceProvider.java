package com.vladmihalcea.hibernate.util.providers;

import com.vladmihalcea.hibernate.util.ReflectionUtils;
import org.hibernate.dialect.Oracle12cDialect;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.SQL2008StandardLimitHandler;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorNoOpImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;
import org.testcontainers.containers.OracleContainer;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class OracleDataSourceProvider implements DataSourceProvider {
    private static final OracleContainer ORACLE_CONTAINER;

    static {
        ORACLE_CONTAINER = new OracleContainer("gvenzl/oracle-xe:18.4.0-slim")
                .withUsername("oracle")
                .withPassword("admin")
                .withDatabaseName("high_performance_java_persistence");

        ORACLE_CONTAINER.start();
    }

    @Override
    public String hibernateDialect() {
        return EnhancedOracle12cDialect.class.getName();
    }

    @Override
    public DataSource dataSource() {
        try {
            DataSource dataSource = ReflectionUtils.newInstance("oracle.jdbc.pool.OracleDataSource");
            ReflectionUtils.invokeSetter(dataSource, "URL", ORACLE_CONTAINER.getJdbcUrl());
            ReflectionUtils.invokeSetter(dataSource, "user", ORACLE_CONTAINER.getUsername());
            ReflectionUtils.invokeSetter(dataSource, "password", ORACLE_CONTAINER.getPassword());
            return dataSource;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Database database() {
        return Database.ORACLE;
    }

    public static class EnhancedOracle12cDialect extends Oracle12cDialect {
        @Override
        public LimitHandler getLimitHandler() {
            return SQL2008StandardLimitHandler.INSTANCE;
        }

        @Override
        public SequenceInformationExtractor getSequenceInformationExtractor() {
            return SequenceInformationExtractorNoOpImpl.INSTANCE;
        }
    }
}

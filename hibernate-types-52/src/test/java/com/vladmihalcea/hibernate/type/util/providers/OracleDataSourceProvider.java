package com.vladmihalcea.hibernate.type.util.providers;

import com.vladmihalcea.hibernate.type.util.ReflectionUtils;
import org.hibernate.dialect.Oracle12cDialect;
import org.hibernate.dialect.pagination.LimitHandler;
import org.hibernate.dialect.pagination.SQL2008StandardLimitHandler;
import org.hibernate.tool.schema.extract.internal.SequenceInformationExtractorNoOpImpl;
import org.hibernate.tool.schema.extract.spi.SequenceInformationExtractor;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class OracleDataSourceProvider implements DataSourceProvider {
    @Override
    public String hibernateDialect() {
        return EnhancedOracle12cDialect.class.getName();
    }

    @Override
    public DataSource dataSource() {
        try {
            DataSource dataSource = ReflectionUtils.newInstance("oracle.jdbc.pool.OracleDataSource");
            ReflectionUtils.invokeSetter(dataSource, "databaseName", "high_performance_java_persistence");
            ReflectionUtils.invokeSetter(dataSource, "URL", url());
            ReflectionUtils.invokeSetter(dataSource, "user", "oracle");
            ReflectionUtils.invokeSetter(dataSource, "password", "admin");
            return dataSource;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Class<? extends DataSource> dataSourceClassName() {
        try {
            return (Class<? extends DataSource>) Class.forName("oracle.jdbc.pool.OracleDataSource");
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    @Override
    public Properties dataSourceProperties() {
        Properties properties = new Properties();
        properties.setProperty("databaseName", "high_performance_java_persistence");
        properties.setProperty("URL", url());
        properties.setProperty("user", username());
        properties.setProperty("password", password());
        return properties;
    }

    @Override
    public String url() {
        return "jdbc:oracle:thin:@localhost:1521/xe";
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

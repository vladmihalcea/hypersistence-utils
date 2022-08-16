package com.vladmihalcea.hibernate.util.providers;

import oracle.jdbc.pool.OracleDataSource;
import org.hibernate.dialect.Oracle10gDialect;
import org.testcontainers.containers.JdbcDatabaseContainer;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class OracleDataSourceProvider extends AbstractContainerDataSourceProvider {

    @Override
    public String hibernateDialect() {
        return Oracle10gDialect.class.getName();
    }

    @Override
    public String defaultJdbcUrl() {
        return "jdbc:oracle:thin:@localhost:1521/xe";
    }

    @Override
    public DataSource newDataSource() {
        try {
            OracleDataSource dataSource = new OracleDataSource();
            JdbcDatabaseContainer container = database().getContainer();
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
    public Database database() {
        return Database.ORACLE;
    }
}

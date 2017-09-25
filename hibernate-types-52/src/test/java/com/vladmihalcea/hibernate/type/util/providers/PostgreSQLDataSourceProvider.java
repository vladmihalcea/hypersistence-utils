package com.vladmihalcea.hibernate.type.util.providers;

import org.hibernate.dialect.PostgreSQL95Dialect;
import org.postgresql.ds.PGSimpleDataSource;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLDataSourceProvider implements DataSourceProvider {

    @Override
    public String hibernateDialect() {
        return PostgreSQL95Dialect.class.getName();
    }

    @Override
    public DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setDatabaseName("high_performance_java_persistence");
        dataSource.setServerName("localhost");
        dataSource.setUser("postgres");
        dataSource.setPassword("admin");
        return dataSource;
    }

    @Override
    public Class<? extends DataSource> dataSourceClassName() {
        return PGSimpleDataSource.class;
    }

    @Override
    public Properties dataSourceProperties() {
        Properties properties = new Properties();
        properties.setProperty("databaseName", "high_performance_java_persistence");
        properties.setProperty("serverName", "localhost");
        properties.setProperty("user", username());
        properties.setProperty("password", password());
        return properties;
    }

    @Override
    public String url() {
        return null;
    }

    @Override
    public String username() {
        return "postgres";
    }

    @Override
    public String password() {
        return "admin";
    }

    @Override
    public Database database() {
        return Database.POSTGRESQL;
    }
}

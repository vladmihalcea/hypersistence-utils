package com.vladmihalcea.hibernate.util.providers;

import org.hibernate.dialect.PostgreSQL95Dialect;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQLDataSourceProvider implements DataSourceProvider {
    private static final PostgreSQLContainer<?> POSTGRE_SQL_CONTAINER;

    static {
        POSTGRE_SQL_CONTAINER = new PostgreSQLContainer<>("postgres:11.1")
                .withUsername("postgres")
                .withPassword("admin")
                .withDatabaseName("high_performance_java_persistence");

        POSTGRE_SQL_CONTAINER.start();
    }

    @Override
    public String hibernateDialect() {
        return PostgreSQL95Dialect.class.getName();
    }

    @Override
    public DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(POSTGRE_SQL_CONTAINER.getJdbcUrl());
        dataSource.setUser(POSTGRE_SQL_CONTAINER.getUsername());
        dataSource.setPassword(POSTGRE_SQL_CONTAINER.getPassword());
        return dataSource;
    }

    @Override
    public Database database() {
        return Database.POSTGRESQL;
    }
}

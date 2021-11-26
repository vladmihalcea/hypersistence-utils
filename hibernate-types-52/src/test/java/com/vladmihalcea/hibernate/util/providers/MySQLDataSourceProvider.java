package com.vladmihalcea.hibernate.util.providers;

import com.mysql.cj.jdbc.MysqlDataSource;
import org.testcontainers.containers.MySQLContainer;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public class MySQLDataSourceProvider implements DataSourceProvider {
    private static final MySQLContainer<?> MYSQL_CONTAINER;

    static {
        MYSQL_CONTAINER = new MySQLContainer<>("mysql:5.7")
                .withUsername("mysql")
                .withPassword("admin")
                .withDatabaseName("high_performance_java_persistence");

        MYSQL_CONTAINER.start();
    }

    @Override
    public String hibernateDialect() {
        return "org.hibernate.dialect.MySQL57Dialect";
    }

    @Override
    public DataSource dataSource() {
        MysqlDataSource dataSource = new MysqlDataSource();
        dataSource.setUrl(MYSQL_CONTAINER.getJdbcUrl());
        dataSource.setUser(MYSQL_CONTAINER.getUsername());
        dataSource.setPassword(MYSQL_CONTAINER.getPassword());
        return dataSource;
    }

    @Override
    public Database database() {
        return Database.MYSQL;
    }
}

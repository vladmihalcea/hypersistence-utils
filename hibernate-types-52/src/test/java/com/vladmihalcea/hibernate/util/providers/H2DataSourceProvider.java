package com.vladmihalcea.hibernate.util.providers;

import org.h2.jdbcx.JdbcConnectionPool;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public class H2DataSourceProvider implements DataSourceProvider {

    @Override
    public String hibernateDialect() {
        return "org.hibernate.dialect.H2Dialect";
    }

    @Override
    public DataSource dataSource() {
        return JdbcConnectionPool.create(
                "jdbc:h2:mem:test",
                "sa",
                ""
        );
    }

    @Override
    public Database database() {
        return Database.H2;
    }
}

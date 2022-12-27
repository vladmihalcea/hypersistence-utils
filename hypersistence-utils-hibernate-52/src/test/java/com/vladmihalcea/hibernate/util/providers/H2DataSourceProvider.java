package com.vladmihalcea.hibernate.util.providers;

import org.h2.jdbcx.JdbcConnectionPool;
import org.h2.jdbcx.JdbcDataSource;

import javax.sql.DataSource;
import java.util.Properties;

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
            url(),
            username(),
            password()
        );
    }

    @Override
    public String url() {
        return "jdbc:h2:mem:test";
    }

    @Override
    public String username() {
        return "sa";
    }

    @Override
    public String password() {
        return "";
    }

    @Override
    public Database database() {
        return Database.H2;
    }
}

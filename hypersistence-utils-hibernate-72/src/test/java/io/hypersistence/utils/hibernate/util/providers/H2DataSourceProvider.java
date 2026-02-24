package io.hypersistence.utils.hibernate.util.providers;

import io.hypersistence.utils.test.providers.DataSourceProvider;
import org.h2.jdbcx.JdbcConnectionPool;
import org.hibernate.dialect.Database;
import org.hibernate.dialect.H2Dialect;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public class H2DataSourceProvider implements DataSourceProvider {

    public static final DataSourceProvider INSTANCE = new H2DataSourceProvider();

    @Override
    public Database database() {
        return Database.H2;
    }

    @Override
    public String hibernateDialect() {
        return H2Dialect.class.getName();
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
}

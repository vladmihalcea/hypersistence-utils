package io.hypersistence.utils.hibernate.util.providers;

import io.hypersistence.utils.test.providers.DataSourceProvider;
import org.hibernate.dialect.Database;
import org.hibernate.dialect.HSQLDialect;
import org.hsqldb.jdbc.JDBCDataSource;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public class HSQLDBDataSourceProvider implements DataSourceProvider {

    public static final DataSourceProvider INSTANCE = new HSQLDBDataSourceProvider();

    @Override
    public Database database() {
        return Database.HSQL;
    }
    
    @Override
    public String hibernateDialect() {
        return HSQLDialect.class.getName();
    }

    @Override
    public DataSource dataSource() {
        JDBCDataSource dataSource = new JDBCDataSource();
        dataSource.setUrl(url());
        dataSource.setUser(username());
        dataSource.setPassword(password());
        return dataSource;
    }

    @Override
    public String url() {
        return "jdbc:hsqldb:mem:test";
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

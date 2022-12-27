package io.hypersistence.utils.hibernate.util.providers;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public interface DataSourceProvider {

    Database database();

    String hibernateDialect();

    DataSource dataSource();

    String url();

    String username();

    String password();
}
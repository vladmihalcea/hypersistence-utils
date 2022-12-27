package com.vladmihalcea.hibernate.util.providers;

import javax.sql.DataSource;
import java.util.Properties;

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
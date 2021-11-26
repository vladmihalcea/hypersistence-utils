package com.vladmihalcea.hibernate.util.providers;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public interface DataSourceProvider {

    String hibernateDialect();

    DataSource dataSource();

    Database database();
}

package com.vladmihalcea.hibernate.type.util.providers;

import javax.sql.DataSource;
import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public interface DataSourceProvider {

    String hibernateDialect();

    DataSource dataSource();

    Class<? extends DataSource> dataSourceClassName();

    Properties dataSourceProperties();

    String url();

    String username();

    String password();

    Database database();

    enum IdentifierStrategy {
        IDENTITY,
        SEQUENCE
    }
}

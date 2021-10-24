package com.vladmihalcea.hibernate.util;

import javax.sql.DataSource;

/**
 * @author Vlad Mihalcea
 */
public enum DataSourceProxyType {
    DATA_SOURCE_PROXY {
        @Override
        DataSource dataSource(DataSource dataSource) {
            return dataSource;
        }
    };

    abstract DataSource dataSource(DataSource dataSource);
}

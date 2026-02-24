package io.hypersistence.utils.hibernate.util;

import io.hypersistence.utils.test.AbstractHibernateTest;

import java.util.Properties;

public abstract class AbstractTest extends AbstractHibernateTest {

    protected Properties properties() {
        Properties properties = super.properties();
        properties.put("hibernate.cache.ehcache.missing_cache_strategy", "create");
        return properties;
    }
}

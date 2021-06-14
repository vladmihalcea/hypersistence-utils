package com.vladmihalcea.hibernate.type.util;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class ConfigurationTest {

    @Test
    public void testHibernateProperties() {
        assertNull(Configuration.INSTANCE.getProperties().getProperty("hibernate.types.nothing"));
        assertEquals("def", Configuration.INSTANCE.getProperties().getProperty("hibernate.types.abc"));
    }

    @Test
    public void testHibernateTypesOverrideProperties() {
        assertEquals("ghi", Configuration.INSTANCE.getProperties().getProperty("hibernate.types.def"));
    }
}

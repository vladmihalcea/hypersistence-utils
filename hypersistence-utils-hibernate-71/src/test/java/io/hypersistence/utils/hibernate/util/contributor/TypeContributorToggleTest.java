package io.hypersistence.utils.hibernate.util.contributor;

import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;
import org.hibernate.HibernateException;
import org.junit.Test;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class TypeContributorToggleTest extends TypeContributorEnableTest {

    @Override
    protected void additionalProperties(Properties properties) {
        properties.setProperty(
            HibernateTypesContributor.ENABLE_TYPES_CONTRIBUTOR,
            "arbitrary"
        );
    }

    @Override
    public void init() {
    }

    @Override
    public void destroy() {
    }

    @Test
    public void test() {
        assertEquals("The value [arbitrary] of the [hypersistence.utils.enable_types_contributor] setting is not supported!",
            assertThrows(HibernateException.class, this::newEntityManagerFactory).getMessage());
    }
}

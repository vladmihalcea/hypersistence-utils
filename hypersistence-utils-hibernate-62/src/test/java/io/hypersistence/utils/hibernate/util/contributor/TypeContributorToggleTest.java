package io.hypersistence.utils.hibernate.util.contributor;

import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;
import io.hypersistence.utils.hibernate.util.AbstractPostgreSQLIntegrationTest;
import org.hibernate.HibernateException;
import org.junit.Test;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Properties;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

@RunWith(Enclosed.class)
public class TypeContributorToggleTest {
    public static class TypeContributorEnabledSettingValueTrueTest extends TypeContributorEnableTest {
        @Override
        protected void additionalProperties(Properties properties) {
            properties.setProperty(
                    HibernateTypesContributor.ENABLE_TYPES_CONTRIBUTOR,
                    Boolean.TRUE.toString()
            );
        }
    }

    public static class TypeContributorDisabledSettingValueArbitraryStringTest extends AbstractPostgreSQLIntegrationTest {
        @Override
        public void init() {
        }

        @Override
        public void destroy() {
        }

        @Override
        protected void additionalProperties(Properties properties) {
            properties.setProperty(
                    HibernateTypesContributor.ENABLE_TYPES_CONTRIBUTOR,
                    "arbitrary"
            );
        }

        @Test
        public void test() {
            assertEquals("The value [arbitrary] of the [hypersistence.utils.enable_types_contributor] setting is not supported!",
                    assertThrows(HibernateException.class, this::newEntityManagerFactory).getMessage());
        }
    }
}

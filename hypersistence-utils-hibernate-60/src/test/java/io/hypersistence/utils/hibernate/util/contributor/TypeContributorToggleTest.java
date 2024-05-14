package io.hypersistence.utils.hibernate.util.contributor;

import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;
import org.junit.experimental.runners.Enclosed;
import org.junit.runner.RunWith;

import java.util.Properties;

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

    public static class TypeContributorDisabledSettingValueArbitraryStringTest extends TypeContributorDisableTest {
        @Override
        protected void additionalProperties(Properties properties) {
            properties.setProperty(
                    HibernateTypesContributor.ENABLE_TYPES_CONTRIBUTOR,
                    "arbitrary"
            );
        }
    }
}

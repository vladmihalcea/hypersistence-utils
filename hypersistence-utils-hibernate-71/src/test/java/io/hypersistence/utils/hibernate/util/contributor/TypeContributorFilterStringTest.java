package io.hypersistence.utils.hibernate.util.contributor;

import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;

import java.util.Properties;

/**
 * @author Vlad Mihalcea
 */
public class TypeContributorFilterStringTest extends TypeContributorFilterClassTest {

    @Override
    protected void additionalProperties(Properties properties) {
        properties.setProperty(
            HibernateTypesContributor.TYPES_CONTRIBUTOR_FILTER,
            PostgreSQLInetTypeFilterOutPredicate.class.getName()
        );
    }
}

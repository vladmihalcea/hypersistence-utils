package io.hypersistence.utils.hibernate.util.contributor;

import io.hypersistence.utils.hibernate.type.HibernateTypesContributor;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLInetType;
import org.hibernate.usertype.UserType;

import java.util.Properties;
import java.util.function.Predicate;

/**
 * @author Vlad Mihalcea
 */
public class TypeContributorFilterClassTest extends TypeContributorDisableTest {

    @Override
    protected void additionalProperties(Properties properties) {
        properties.put(
            HibernateTypesContributor.TYPES_CONTRIBUTOR_FILTER,
            PostgreSQLInetTypeFilterOutPredicate.class
        );
    }

    public static class PostgreSQLInetTypeFilterOutPredicate implements Predicate<UserType> {
        @Override
        public boolean test(UserType userType) {
            return !(userType instanceof PostgreSQLInetType);
        }
    }
}

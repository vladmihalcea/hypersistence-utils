package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.common.ReflectionUtils;
import io.hypersistence.utils.hibernate.type.basic.Iso8601MonthType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLHStoreType;
import io.hypersistence.utils.hibernate.type.basic.PostgreSQLInetType;
import io.hypersistence.utils.hibernate.type.interval.OracleIntervalDayToSecondType;
import io.hypersistence.utils.hibernate.type.interval.PostgreSQLIntervalType;
import io.hypersistence.utils.hibernate.type.interval.PostgreSQLPeriodType;
import io.hypersistence.utils.hibernate.type.json.JsonNodeStringType;
import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import org.hibernate.HibernateException;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.usertype.UserType;

import java.util.function.Predicate;

/**
 * The {@link HibernateTypesContributor} registers various types automatically.
 *
 * @author Vlad Mihalcea
 * @since 2.15.0
 */
public class HibernateTypesContributor implements TypeContributor {

    public static final String ENABLE_TYPES_CONTRIBUTOR = "hypersistence.utils.enable_types_contributor";

    public static final String TYPES_CONTRIBUTOR_FILTER = "hypersistence.utils.types_contributor_filter";

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        ConfigurationService configurationService = serviceRegistry.getService(ConfigurationService.class);
        Boolean enableTypesContributor = (Boolean) configurationService.getSetting(ENABLE_TYPES_CONTRIBUTOR, value -> {
            if(value instanceof Boolean) {
                return value;
            }
            if(value instanceof String) {
                return Boolean.valueOf((String) value);
            }
            throw new HibernateException(
                String.format("The value [%s] of the [%s] setting is not supported!", value, ENABLE_TYPES_CONTRIBUTOR)
            );
        });
        if(Boolean.FALSE.equals(enableTypesContributor)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Predicate<UserType> typeFilter = (Predicate<UserType>) configurationService.getSetting(TYPES_CONTRIBUTOR_FILTER, value -> {
            if(value instanceof Predicate) {
                return value;
            }
            if(value instanceof Class) {
                return ReflectionUtils.newInstance((Class) value);
            }
            if(value instanceof String) {
                return ReflectionUtils.newInstance(
                    ReflectionUtils.getClass((String) value)
                );
            }
            throw new HibernateException(
                String.format("The value of the [%s] setting is not supported!", TYPES_CONTRIBUTOR_FILTER)
            );
        });
        if(typeFilter == null) {
            typeFilter = o -> true;
        }

        JdbcServices jdbcServices = serviceRegistry.getService(JdbcServices.class);
        Dialect dialect = jdbcServices.getDialect();

        boolean enableJson = ReflectionUtils.getClassOrNull("com.fasterxml.jackson.databind.ObjectMapper") != null;

        /*
         * The JSON Types that map java.lang.Object as they can cause
         * https://github.com/vladmihalcea/hypersistence-utils/issues/520
         */

        if(dialect instanceof PostgreSQLDialect) {
            /* Date/Time */
            contributeType(typeContributions, PostgreSQLIntervalType.INSTANCE, typeFilter);
            contributeType(typeContributions, PostgreSQLPeriodType.INSTANCE, typeFilter);

            /* Specific-types */
            contributeType(typeContributions, PostgreSQLHStoreType.INSTANCE, typeFilter);
            contributeType(typeContributions, PostgreSQLInetType.INSTANCE, typeFilter);
            contributeType(typeContributions, PostgreSQLRangeType.INSTANCE, typeFilter);

            if(ReflectionUtils.getClassOrNull("com.google.common.collect.Range") != null) {
                contributeType(typeContributions, PostgreSQLGuavaRangeType.INSTANCE, typeFilter);
            }
        } else if(dialect instanceof MySQLDialect) {
            /* JSON */
            if (enableJson) {
                contributeType(typeContributions, JsonNodeStringType.INSTANCE, typeFilter);
            }
        } else if(dialect instanceof OracleDialect) {
            /* Date/Time */
            contributeType(typeContributions, OracleIntervalDayToSecondType.INSTANCE, typeFilter);
        }
        /* Date/Time */
        contributeType(typeContributions, Iso8601MonthType.INSTANCE, typeFilter);
    }

    private HibernateTypesContributor contributeType(TypeContributions typeContributions, UserType type, Predicate<UserType> typeFilter) {
        if (typeFilter.test(type)) {
            typeContributions.contributeType(type);
        }

        return this;
    }
}

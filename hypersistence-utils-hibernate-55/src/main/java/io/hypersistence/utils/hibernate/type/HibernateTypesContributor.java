package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.array.*;
import io.hypersistence.utils.hibernate.type.basic.*;
import io.hypersistence.utils.hibernate.type.interval.OracleIntervalDayToSecondType;
import io.hypersistence.utils.hibernate.type.interval.PostgreSQLIntervalType;
import io.hypersistence.utils.hibernate.type.interval.PostgreSQLPeriodType;
import io.hypersistence.utils.hibernate.type.json.*;
import io.hypersistence.utils.hibernate.type.money.CurrencyUnitType;
import io.hypersistence.utils.hibernate.type.money.MonetaryAmountType;
import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import io.hypersistence.utils.hibernate.type.search.PostgreSQLTSVectorType;
import io.hypersistence.utils.hibernate.util.ReflectionUtils;
import org.hibernate.HibernateException;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.*;
import org.hibernate.engine.config.spi.ConfigurationService;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.BasicType;
import org.hibernate.usertype.CompositeUserType;
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
                return Boolean.getBoolean((String) value);
            }
            throw new HibernateException(
                String.format("The value [%s] of the [%s] setting is not supported!", value, ENABLE_TYPES_CONTRIBUTOR)
            );
        });
        if(Boolean.FALSE.equals(enableTypesContributor)) {
            return;
        }
        @SuppressWarnings("unchecked")
        Predicate<Object> typeFilter = (Predicate<Object>) configurationService.getSetting(TYPES_CONTRIBUTOR_FILTER, value -> {
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

        if (dialect instanceof PostgreSQL82Dialect) {
            /* Arrays */
            this
                .contributeType(typeContributions, BooleanArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, DateArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, DecimalArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, DoubleArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, EnumArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, IntArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, ListArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, LocalDateArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, LocalDateTimeArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, LongArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, StringArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, TimestampArrayType.INSTANCE, typeFilter)
                .contributeType(typeContributions, UUIDArrayType.INSTANCE, typeFilter)
                /* Date/Time */
                .contributeType(typeContributions, PostgreSQLIntervalType.INSTANCE, typeFilter)
                .contributeType(typeContributions, PostgreSQLPeriodType.INSTANCE, typeFilter)
                /* Specific-types */
                .contributeType(typeContributions, PostgreSQLTSVectorType.INSTANCE, typeFilter)
                .contributeType(typeContributions, PostgreSQLEnumType.INSTANCE, typeFilter)
                .contributeType(typeContributions, PostgreSQLHStoreType.INSTANCE, typeFilter)
                .contributeType(typeContributions, PostgreSQLInetType.INSTANCE, typeFilter)
                .contributeType(typeContributions, PostgreSQLRangeType.INSTANCE, typeFilter)
                .contributeType(typeContributions, PostgreSQLCITextType.INSTANCE, typeFilter);

            if (ReflectionUtils.getClassOrNull("com.google.common.collect.Range") != null) {
                this.contributeType(typeContributions, PostgreSQLGuavaRangeType.INSTANCE, typeFilter);
            }
            if (enableJson) {
                /* JSON */
                this.contributeType(typeContributions, JsonBinaryType.INSTANCE, typeFilter);
            }
        } else if (dialect instanceof MySQLDialect) {
            /* JSON */
            if (enableJson) {
                this.contributeType(typeContributions, JsonStringType.INSTANCE, typeFilter)
                    .contributeType(typeContributions, JsonNodeStringType.INSTANCE, typeFilter);
            }
        } else if (dialect instanceof SQLServer2005Dialect) {
            /* JSON */
            if (enableJson) {
                this.contributeType(typeContributions, JsonStringType.INSTANCE, typeFilter);
            }
        } else if (dialect instanceof Oracle8iDialect) {
            /* Date/Time */
            this
                .contributeType(typeContributions, OracleIntervalDayToSecondType.INSTANCE, typeFilter);
            /* JSON */
            if (enableJson) {
                this.contributeType(typeContributions, JsonStringType.INSTANCE, typeFilter)
                    .contributeType(typeContributions, JsonBlobType.INSTANCE, typeFilter);
            }
        }

        /* Basic */
        this.contributeType(typeContributions, NullableCharacterType.INSTANCE, typeFilter)
            /* Date/Time */
            .contributeType(typeContributions, Iso8601MonthType.INSTANCE, typeFilter)
            .contributeType(typeContributions, MonthDayDateType.INSTANCE, typeFilter)
            .contributeType(typeContributions, MonthDayIntegerType.INSTANCE, typeFilter)
            .contributeType(typeContributions, YearMonthDateType.INSTANCE, typeFilter)
            .contributeType(typeContributions, YearMonthEpochType.INSTANCE, typeFilter)
            .contributeType(typeContributions, YearMonthIntegerType.INSTANCE, typeFilter)
            .contributeType(typeContributions, YearMonthTimestampType.INSTANCE, typeFilter);
        /* JSON */
        if (enableJson) {
            this.contributeType(typeContributions, JsonType.INSTANCE, typeFilter);
        }
        /* Money and Currency API */
        if (ReflectionUtils.getClassOrNull("org.javamoney.moneta.Money") != null) {
            this.contributeType(typeContributions, CurrencyUnitType.INSTANCE, typeFilter)
                .contributeType(typeContributions, MonetaryAmountType.INSTANCE, typeFilter);
        }
    }

    private HibernateTypesContributor contributeType(TypeContributions typeContributions, Object type, Predicate<Object> typeFilter) {
        if (typeFilter.test(type)) {
            if (type instanceof BasicType) {
                typeContributions.contributeType((BasicType) type);
            } else if (type instanceof UserType) {
                typeContributions.contributeType((UserType) type, type.getClass().getSimpleName());
            } else if (type instanceof CompositeUserType) {
                typeContributions.contributeType((CompositeUserType) type, type.getClass().getSimpleName());
            } else {
                throw new UnsupportedOperationException(
                    String.format("The [%s] is not supported!", type.getClass())
                );
            }
        }
        return this;
    }
}

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
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.*;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.BasicType;
import org.hibernate.usertype.CompositeUserType;
import org.hibernate.usertype.UserType;

/**
 * The {@link HibernateTypesContributor} registers various types automatically.
 *
 * @author Vlad Mihalcea
 * @since 2.15.0
 */
public class HibernateTypesContributor implements TypeContributor {

    @Override
    public void contribute(TypeContributions typeContributions, ServiceRegistry serviceRegistry) {
        JdbcServices jdbcServices = serviceRegistry.getService(JdbcServices.class);
        Dialect dialect = jdbcServices.getDialect();

        boolean enableJson = ReflectionUtils.getClassOrNull("com.fasterxml.jackson.databind.ObjectMapper") != null;

        if (dialect instanceof PostgreSQL82Dialect) {
            /* Arrays */
            this
                .contributeType(typeContributions, BooleanArrayType.INSTANCE)
                .contributeType(typeContributions, DateArrayType.INSTANCE)
                .contributeType(typeContributions, DecimalArrayType.INSTANCE)
                .contributeType(typeContributions, DoubleArrayType.INSTANCE)
                .contributeType(typeContributions, EnumArrayType.INSTANCE)
                .contributeType(typeContributions, IntArrayType.INSTANCE)
                .contributeType(typeContributions, ListArrayType.INSTANCE)
                .contributeType(typeContributions, LocalDateArrayType.INSTANCE)
                .contributeType(typeContributions, LocalDateTimeArrayType.INSTANCE)
                .contributeType(typeContributions, LongArrayType.INSTANCE)
                .contributeType(typeContributions, StringArrayType.INSTANCE)
                .contributeType(typeContributions, TimestampArrayType.INSTANCE)
                .contributeType(typeContributions, UUIDArrayType.INSTANCE)
                /* Date/Time */
                .contributeType(typeContributions, PostgreSQLIntervalType.INSTANCE)
                .contributeType(typeContributions, PostgreSQLPeriodType.INSTANCE)
                /* Specific-types */
                .contributeType(typeContributions, PostgreSQLTSVectorType.INSTANCE)
                .contributeType(typeContributions, PostgreSQLEnumType.INSTANCE)
                .contributeType(typeContributions, PostgreSQLHStoreType.INSTANCE)
                .contributeType(typeContributions, PostgreSQLInetType.INSTANCE)
                .contributeType(typeContributions, PostgreSQLRangeType.INSTANCE)
                .contributeType(typeContributions, PostgreSQLCITextType.INSTANCE);

            if (ReflectionUtils.getClassOrNull("com.google.common.collect.Range") != null) {
                this.contributeType(typeContributions, PostgreSQLGuavaRangeType.INSTANCE);
            }
            if (enableJson) {
                /* JSON */
                this.contributeType(typeContributions, JsonBinaryType.INSTANCE);
            }
        } else if (dialect instanceof MySQLDialect) {
            /* JSON */
            if (enableJson) {
                this.contributeType(typeContributions, JsonStringType.INSTANCE)
                    .contributeType(typeContributions, JsonNodeStringType.INSTANCE);
            }
        } else if (dialect instanceof SQLServer2005Dialect) {
            /* JSON */
            if (enableJson) {
                this.contributeType(typeContributions, JsonStringType.INSTANCE);
            }
        } else if (dialect instanceof Oracle8iDialect) {
            /* Date/Time */
            this
                .contributeType(typeContributions, OracleIntervalDayToSecondType.INSTANCE);
            /* JSON */
            if (enableJson) {
                this.contributeType(typeContributions, JsonStringType.INSTANCE)
                    .contributeType(typeContributions, JsonBlobType.INSTANCE);
            }
        }

        /* Basic */
        this.contributeType(typeContributions, NullableCharacterType.INSTANCE)
            /* Date/Time */
            .contributeType(typeContributions, Iso8601MonthType.INSTANCE)
            .contributeType(typeContributions, MonthDayDateType.INSTANCE)
            .contributeType(typeContributions, MonthDayIntegerType.INSTANCE)
            .contributeType(typeContributions, YearMonthDateType.INSTANCE)
            .contributeType(typeContributions, YearMonthEpochType.INSTANCE)
            .contributeType(typeContributions, YearMonthIntegerType.INSTANCE)
            .contributeType(typeContributions, YearMonthTimestampType.INSTANCE);
        /* JSON */
        if (enableJson) {
            this.contributeType(typeContributions, JsonType.INSTANCE);
        }
        /* Money and Currency API */
        if (ReflectionUtils.getClassOrNull("org.javamoney.moneta.Money") != null) {
            this.contributeType(typeContributions, CurrencyUnitType.INSTANCE)
                .contributeType(typeContributions, MonetaryAmountType.INSTANCE);
        }
    }

    private HibernateTypesContributor contributeType(TypeContributions typeContributions, Object type) {
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
        return this;
    }
}

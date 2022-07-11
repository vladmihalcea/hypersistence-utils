package com.vladmihalcea.hibernate.type;

import com.vladmihalcea.hibernate.type.array.*;
import com.vladmihalcea.hibernate.type.basic.*;
import com.vladmihalcea.hibernate.type.interval.OracleIntervalDayToSecondType;
import com.vladmihalcea.hibernate.type.interval.PostgreSQLIntervalType;
import com.vladmihalcea.hibernate.type.interval.PostgreSQLPeriodType;
import com.vladmihalcea.hibernate.type.json.*;
import com.vladmihalcea.hibernate.type.range.PostgreSQLRangeType;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import com.vladmihalcea.hibernate.type.search.PostgreSQLTSVectorType;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.*;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;

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

        if(dialect instanceof PostgreSQLDialect) {
            /* Arrays */
            typeContributions.contributeType(BooleanArrayType.INSTANCE);
            typeContributions.contributeType(DateArrayType.INSTANCE);
            typeContributions.contributeType(DecimalArrayType.INSTANCE);
            typeContributions.contributeType(DoubleArrayType.INSTANCE);
            typeContributions.contributeType(EnumArrayType.INSTANCE);
            typeContributions.contributeType(IntArrayType.INSTANCE);
            typeContributions.contributeType(ListArrayType.INSTANCE);
            typeContributions.contributeType(LocalDateArrayType.INSTANCE);
            typeContributions.contributeType(LocalDateTimeArrayType.INSTANCE);
            typeContributions.contributeType(LongArrayType.INSTANCE);
            typeContributions.contributeType(StringArrayType.INSTANCE);
            typeContributions.contributeType(TimestampArrayType.INSTANCE);
            typeContributions.contributeType(UUIDArrayType.INSTANCE);

            /* Date/Time */
            typeContributions.contributeType(PostgreSQLIntervalType.INSTANCE);
            typeContributions.contributeType(PostgreSQLPeriodType.INSTANCE);

            /* JSON */
            typeContributions.contributeType(JsonBinaryType.INSTANCE);

            /* Specific-types */
            typeContributions.contributeType(PostgreSQLTSVectorType.INSTANCE);
            typeContributions.contributeType(PostgreSQLEnumType.INSTANCE);
            typeContributions.contributeType(PostgreSQLHStoreType.INSTANCE);
            typeContributions.contributeType(PostgreSQLInetType.INSTANCE);
            typeContributions.contributeType(PostgreSQLRangeType.INSTANCE);

            if(ReflectionUtils.getClassOrNull("com.google.common.collect.Range") != null) {
                typeContributions.contributeType(PostgreSQLGuavaRangeType.INSTANCE);
            }
        } else if(dialect instanceof MySQLDialect) {
            /* JSON */
            typeContributions.contributeType(JsonStringType.INSTANCE);
            typeContributions.contributeType(JsonNodeStringType.INSTANCE);
        } else if(dialect instanceof SQLServerDialect) {
            /* JSON */
            typeContributions.contributeType(JsonStringType.INSTANCE);
        } else if(dialect instanceof OracleDialect) {
            /* Date/Time */
            typeContributions.contributeType(OracleIntervalDayToSecondType.INSTANCE);
            /* JSON */
            typeContributions.contributeType(JsonStringType.INSTANCE);
            typeContributions.contributeType(JsonBlobType.INSTANCE);
        }

        /* Basic */
        typeContributions.contributeType(NullableCharacterType.INSTANCE);
        /* Date/Time */
        typeContributions.contributeType(Iso8601MonthType.INSTANCE);
        typeContributions.contributeType(MonthDayDateType.INSTANCE);
        typeContributions.contributeType(MonthDayIntegerType.INSTANCE);
        typeContributions.contributeType(YearMonthDateType.INSTANCE);
        typeContributions.contributeType(YearMonthEpochType.INSTANCE);
        typeContributions.contributeType(YearMonthIntegerType.INSTANCE);
        typeContributions.contributeType(YearMonthTimestampType.INSTANCE);
        /* JSON */
        typeContributions.contributeType(JsonType.INSTANCE);
    }
}

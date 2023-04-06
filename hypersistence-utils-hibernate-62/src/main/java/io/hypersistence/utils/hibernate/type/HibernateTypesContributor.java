package io.hypersistence.utils.hibernate.type;

import io.hypersistence.utils.hibernate.type.array.*;
import io.hypersistence.utils.hibernate.type.basic.*;
import io.hypersistence.utils.hibernate.type.interval.OracleIntervalDayToSecondType;
import io.hypersistence.utils.hibernate.type.interval.PostgreSQLIntervalType;
import io.hypersistence.utils.hibernate.type.interval.PostgreSQLPeriodType;
import io.hypersistence.utils.hibernate.type.json.JsonNodeStringType;
import io.hypersistence.utils.hibernate.type.range.PostgreSQLRangeType;
import io.hypersistence.utils.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import io.hypersistence.utils.hibernate.util.ReflectionUtils;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.Dialect;
import org.hibernate.dialect.MySQLDialect;
import org.hibernate.dialect.OracleDialect;
import org.hibernate.dialect.PostgreSQLDialect;
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

        boolean enableJson = ReflectionUtils.getClassOrNull("com.fasterxml.jackson.databind.ObjectMapper") != null;

        /*
         * The JSON Types that map java.lang.Object as they can cause
         * https://github.com/vladmihalcea/hypersistence-utils/issues/520
         */

        if(dialect instanceof PostgreSQLDialect) {
            /* Arrays */
            typeContributions.contributeType(BooleanArrayType.INSTANCE);
            typeContributions.contributeType(DateArrayType.INSTANCE);
            typeContributions.contributeType(DecimalArrayType.INSTANCE);
            typeContributions.contributeType(DoubleArrayType.INSTANCE);
            typeContributions.contributeType(FloatArrayType.INSTANCE);
            typeContributions.contributeType(EnumArrayType.INSTANCE);
            typeContributions.contributeType(IntArrayType.INSTANCE);
            typeContributions.contributeType(LocalDateArrayType.INSTANCE);
            typeContributions.contributeType(LocalDateTimeArrayType.INSTANCE);
            typeContributions.contributeType(LongArrayType.INSTANCE);
            typeContributions.contributeType(StringArrayType.INSTANCE);
            typeContributions.contributeType(TimestampArrayType.INSTANCE);
            typeContributions.contributeType(UUIDArrayType.INSTANCE);

            /* Date/Time */
            typeContributions.contributeType(PostgreSQLIntervalType.INSTANCE);
            typeContributions.contributeType(PostgreSQLPeriodType.INSTANCE);

            /* Specific-types */
            typeContributions.contributeType(PostgreSQLEnumType.INSTANCE);
            typeContributions.contributeType(PostgreSQLHStoreType.INSTANCE);
            typeContributions.contributeType(PostgreSQLInetType.INSTANCE);
            typeContributions.contributeType(PostgreSQLRangeType.INSTANCE);

            if(ReflectionUtils.getClassOrNull("com.google.common.collect.Range") != null) {
                typeContributions.contributeType(PostgreSQLGuavaRangeType.INSTANCE);
            }
        } else if(dialect instanceof MySQLDialect) {
            /* JSON */
            if (enableJson) {
                typeContributions.contributeType(JsonNodeStringType.INSTANCE);
            }
        } else if(dialect instanceof OracleDialect) {
            /* Date/Time */
            typeContributions.contributeType(OracleIntervalDayToSecondType.INSTANCE);
        }

        /* Basic */
        typeContributions.contributeType(NullableCharacterType.INSTANCE);
        /* Date/Time */
        typeContributions.contributeType(Iso8601MonthType.INSTANCE);
    }
}

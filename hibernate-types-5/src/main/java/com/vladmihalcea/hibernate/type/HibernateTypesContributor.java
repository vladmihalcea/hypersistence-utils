package com.vladmihalcea.hibernate.type;

import com.vladmihalcea.hibernate.type.array.*;
import com.vladmihalcea.hibernate.type.basic.NullableCharacterType;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLHStoreType;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLInetType;
import com.vladmihalcea.hibernate.type.json.*;
import com.vladmihalcea.hibernate.type.range.PostgreSQLRangeType;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
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
            typeContributions.contributeType(DoubleArrayType.INSTANCE);
            typeContributions.contributeType(ListArrayType.INSTANCE);
            typeContributions.contributeType(LongArrayType.INSTANCE);
            typeContributions.contributeType(StringArrayType.INSTANCE);
            typeContributions.contributeType(TimestampArrayType.INSTANCE);
            typeContributions.contributeType(UUIDArrayType.INSTANCE);

            /* JSON */
            typeContributions.contributeType(JsonBinaryType.INSTANCE);

            /* Specific-types */
            typeContributions.contributeType(PostgreSQLEnumType.INSTANCE);
            typeContributions.contributeType(PostgreSQLHStoreType.INSTANCE);
            typeContributions.contributeType(PostgreSQLInetType.INSTANCE);
            typeContributions.contributeType(PostgreSQLRangeType.INSTANCE);
            typeContributions.contributeType(PostgreSQLGuavaRangeType.INSTANCE);
        } else if(dialect instanceof MySQLDialect) {
            /* JSON */
            typeContributions.contributeType(JsonStringType.INSTANCE);
            typeContributions.contributeType(JsonNodeStringType.INSTANCE);
        } else if(dialect instanceof SQLServerDialect) {
            /* JSON */
            typeContributions.contributeType(JsonStringType.INSTANCE);
        } else if(dialect instanceof OracleDialect) {
            /* JSON */
            typeContributions.contributeType(JsonStringType.INSTANCE);
            typeContributions.contributeType(JsonBlobType.INSTANCE);
        }

        /* Basic */
        typeContributions.contributeType(NullableCharacterType.INSTANCE);
        /* JSON */
        typeContributions.contributeType(JsonType.INSTANCE);
    }
}

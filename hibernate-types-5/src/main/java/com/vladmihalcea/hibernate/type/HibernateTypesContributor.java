package com.vladmihalcea.hibernate.type;

import com.vladmihalcea.hibernate.type.array.*;
import com.vladmihalcea.hibernate.type.basic.NullableCharacterType;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLEnumType;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLHStoreType;
import com.vladmihalcea.hibernate.type.basic.PostgreSQLInetType;
import com.vladmihalcea.hibernate.type.json.*;
import com.vladmihalcea.hibernate.type.range.PostgreSQLRangeType;
import com.vladmihalcea.hibernate.type.range.guava.PostgreSQLGuavaRangeType;
import com.vladmihalcea.hibernate.util.ReflectionUtils;
import org.hibernate.boot.model.TypeContributions;
import org.hibernate.boot.model.TypeContributor;
import org.hibernate.dialect.*;
import org.hibernate.engine.jdbc.spi.JdbcServices;
import org.hibernate.service.ServiceRegistry;
import org.hibernate.type.BasicType;
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

        if(dialect instanceof PostgreSQLDialect) {
            /* Arrays */
            this
            .contributeType(typeContributions, BooleanArrayType.INSTANCE)
            .contributeType(typeContributions, DateArrayType.INSTANCE)
            .contributeType(typeContributions, DecimalArrayType.INSTANCE)
            .contributeType(typeContributions, DoubleArrayType.INSTANCE)
            .contributeType(typeContributions, EnumArrayType.INSTANCE)
            .contributeType(typeContributions, IntArrayType.INSTANCE)
            .contributeType(typeContributions, DoubleArrayType.INSTANCE)
            .contributeType(typeContributions, ListArrayType.INSTANCE)
            .contributeType(typeContributions, LongArrayType.INSTANCE)
            .contributeType(typeContributions, StringArrayType.INSTANCE)
            .contributeType(typeContributions, TimestampArrayType.INSTANCE)
            .contributeType(typeContributions, UUIDArrayType.INSTANCE)
            /* JSON */
            .contributeType(typeContributions, JsonBinaryType.INSTANCE)

            /* Specific-types */
            .contributeType(typeContributions, PostgreSQLEnumType.INSTANCE)
            .contributeType(typeContributions, PostgreSQLHStoreType.INSTANCE)
            .contributeType(typeContributions, PostgreSQLInetType.INSTANCE)
            .contributeType(typeContributions, PostgreSQLRangeType.INSTANCE);

            if(ReflectionUtils.getClassOrNull("com.google.common.collect.Range") != null) {
                this.contributeType(typeContributions, PostgreSQLGuavaRangeType.INSTANCE);
            }
        } else if(dialect instanceof MySQLDialect) {
            /* JSON */
            this
            .contributeType(typeContributions, JsonStringType.INSTANCE)
            .contributeType(typeContributions, JsonNodeStringType.INSTANCE);
        } else if(dialect instanceof SQLServerDialect) {
            /* JSON */
            this
            .contributeType(typeContributions, JsonStringType.INSTANCE);
        } else if(dialect instanceof OracleDialect) {
            /* Date/Time */
            this
            /* JSON */
            .contributeType(typeContributions, JsonStringType.INSTANCE)
            .contributeType(typeContributions, JsonBlobType.INSTANCE);
        }

        /* Basic */
        this.contributeType(typeContributions, NullableCharacterType.INSTANCE)
        /* JSON */
        .contributeType(typeContributions, JsonType.INSTANCE);
    }

    private HibernateTypesContributor contributeType(TypeContributions typeContributions, BasicType type) {
        typeContributions.contributeType(type);
        return this;
    }

    private HibernateTypesContributor contributeType(TypeContributions typeContributions, UserType type) {
        if(type instanceof ImmutableType) {
            ImmutableType immutableType = (ImmutableType) type;
            typeContributions.contributeType(immutableType, immutableType.getName());
        } else {
            typeContributions.contributeType(type, type.getClass().getSimpleName());
        }
        return this;
    }
}

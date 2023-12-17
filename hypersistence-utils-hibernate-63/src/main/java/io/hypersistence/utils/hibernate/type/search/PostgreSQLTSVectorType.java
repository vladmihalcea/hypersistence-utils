package io.hypersistence.utils.hibernate.type.search;

import io.hypersistence.utils.hibernate.type.MutableDynamicParameterizedType;
import io.hypersistence.utils.hibernate.type.search.internal.PostgreSQLTSVectorSqlTypeDescriptor;
import io.hypersistence.utils.hibernate.type.search.internal.PostgreSQLTSVectorTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;

/**
 * Maps a {@link String} object type to a PostgreSQL TSVector column type.
 *
 * @author Vlad Mihalcea
 * @author Philip Riecks
 */
public class PostgreSQLTSVectorType extends MutableDynamicParameterizedType<Object, PostgreSQLTSVectorSqlTypeDescriptor, PostgreSQLTSVectorTypeDescriptor> {

    public static final PostgreSQLTSVectorType INSTANCE = new PostgreSQLTSVectorType();

    public PostgreSQLTSVectorType() {
        super(
            Object.class,
            PostgreSQLTSVectorSqlTypeDescriptor.INSTANCE,
            new PostgreSQLTSVectorTypeDescriptor()
        );
    }

    public PostgreSQLTSVectorType(Configuration configuration) {
        super(
            Object.class,
            PostgreSQLTSVectorSqlTypeDescriptor.INSTANCE,
            new PostgreSQLTSVectorTypeDescriptor(),
            configuration
        );
    }

    public PostgreSQLTSVectorType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "tsvector";
    }
}

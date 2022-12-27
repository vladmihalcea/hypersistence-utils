package io.hypersistence.utils.hibernate.type.search;

import io.hypersistence.utils.hibernate.type.search.internal.PostgreSQLTSVectorSqlTypeDescriptor;
import io.hypersistence.utils.hibernate.type.search.internal.PostgreSQLTSVectorTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps a {@link String} object type to a PostgreSQL TSVector column type.
 *
 * @author Vlad Mihalcea
 * @author Philip Riecks
 */
public class PostgreSQLTSVectorType
        extends AbstractSingleColumnStandardBasicType<Object> implements DynamicParameterizedType {

    public static final PostgreSQLTSVectorType INSTANCE = new PostgreSQLTSVectorType();

    private Configuration configuration;

    public PostgreSQLTSVectorType() {
        super(PostgreSQLTSVectorSqlTypeDescriptor.INSTANCE, new PostgreSQLTSVectorTypeDescriptor());
    }

    public PostgreSQLTSVectorType(Configuration configuration) {
        this();
        this.configuration = configuration;
    }

    public PostgreSQLTSVectorType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    @Override
    public String getName() {
        return "tsvector";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((PostgreSQLTSVectorTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}

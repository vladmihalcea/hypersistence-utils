package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.basic.internal.PostgreSQLTSVectorSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.basic.internal.PostgreSQLTSVectorTypeDescriptor;
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
        extends AbstractSingleColumnStandardBasicType<String> implements DynamicParameterizedType {

    public static final PostgreSQLTSVectorType INSTANCE = new PostgreSQLTSVectorType();


    public PostgreSQLTSVectorType() {
        super(PostgreSQLTSVectorSqlTypeDescriptor.INSTANCE,  new PostgreSQLTSVectorTypeDescriptor());
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

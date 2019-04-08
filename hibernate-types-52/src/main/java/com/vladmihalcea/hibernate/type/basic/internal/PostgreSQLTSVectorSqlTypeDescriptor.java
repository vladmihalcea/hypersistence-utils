package com.vladmihalcea.hibernate.type.basic.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

import java.sql.Types;

public class PostgreSQLTSVectorSqlTypeDescriptor implements SqlTypeDescriptor {

    public static final PostgreSQLTSVectorSqlTypeDescriptor INSTANCE = new PostgreSQLTSVectorSqlTypeDescriptor();

    @Override
    public int getSqlType() {
        return Types.VARCHAR;
    }

    @Override
    public boolean canBeRemapped() {
        return false;
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return null;
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return null;
    }
}

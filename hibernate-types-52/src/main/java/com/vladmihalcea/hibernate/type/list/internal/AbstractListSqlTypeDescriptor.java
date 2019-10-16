package com.vladmihalcea.hibernate.type.list.internal;

import java.sql.CallableStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicExtractor;
import org.hibernate.type.descriptor.sql.SqlTypeDescriptor;

/**
 * @author Daniel Hoffmann
 */
public abstract class AbstractListSqlTypeDescriptor implements SqlTypeDescriptor {

    @Override
    public int getSqlType() {
        return Types.ARRAY;
    }

    @Override
    public boolean canBeRemapped() {
        return true;
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicExtractor<X>(javaTypeDescriptor, this) {
            @Override
            protected X doExtract(ResultSet rs, String name, WrapperOptions options) throws SQLException {
                return javaTypeDescriptor.wrap(extractArray(rs, name), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, int index, WrapperOptions options) throws SQLException {
                return javaTypeDescriptor.wrap(extractArray(statement, index), options);
            }

            @Override
            protected X doExtract(CallableStatement statement, String name, WrapperOptions options) throws SQLException {
                return javaTypeDescriptor.wrap(extractArray(statement, name), options);
            }
        };
    }

    private Object extractArray(ResultSet resultSet, String name) throws SQLException {
        return resultSet.getArray(name);
    }

    private Object extractArray(CallableStatement statement, int index) throws SQLException {
        return statement.getArray(index);
    }

    private Object extractArray(CallableStatement statement, String name) throws SQLException {
        return statement.getArray(name);
    }
}

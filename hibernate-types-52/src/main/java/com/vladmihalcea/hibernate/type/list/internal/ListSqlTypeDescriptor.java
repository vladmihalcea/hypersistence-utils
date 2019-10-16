package com.vladmihalcea.hibernate.type.list.internal;

import java.sql.Array;
import java.sql.CallableStatement;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BasicBinder;

/**
 * @author Daniel Hoffmann
 */
public class ListSqlTypeDescriptor extends AbstractListSqlTypeDescriptor {

    public static final ListSqlTypeDescriptor INSTANCE = new ListSqlTypeDescriptor();

    @Override
    public <X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return new BasicBinder<X>(javaTypeDescriptor, this) {
            @Override
            protected void doBind(PreparedStatement preparedStatement,
                                  X value,
                                  int index,
                                  WrapperOptions options) throws SQLException {
                preparedStatement.setArray(index, javaTypeDescriptor.unwrap(value, Array.class, options));
            }

            @Override
            protected void doBind(CallableStatement callableStatement,
                                  X value,
                                  String name,
                                  WrapperOptions options) throws SQLException {
                throw new SQLFeatureNotSupportedException("Array cannot be set with name parameter!");
            }
        };
    }
}

package com.vladmihalcea.hibernate.type.list;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.list.internal.ListSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.list.internal.ListTypeDescriptor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.hibernate.usertype.DynamicParameterizedType;

/**
 * Maps a {@link List<Integer>} object type to a PostgreSQL Array type
 *
 * @author Daniel Hoffmann
 */
public class PostgreSQLIntegerListType extends AbstractHibernateType<Object> implements DynamicParameterizedType {

    private static final String TYPE = "int4";

    public static final PostgreSQLIntegerListType INSTANCE = new PostgreSQLIntegerListType();

    public PostgreSQLIntegerListType() {
        super(
            ListSqlTypeDescriptor.INSTANCE,
            new ListTypeDescriptor<>(TYPE, array -> {
                List<Integer> list = new ArrayList<>();
                Collections.addAll(list, ((Integer[]) array.getArray()));
                return list;
            })
        );
    }

    @Override
    public String getName() {
        return TYPE;
    }

    @Override
    public void setParameterValues(Properties properties) {
        ((ListTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(properties);
    }
}

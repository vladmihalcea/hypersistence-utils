package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.array.internal.ArraySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.DateArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Date;
import java.util.Properties;

/**
 * Maps an {@code Date[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Guillaume Briand
 */
public class DateArrayType
        extends AbstractHibernateType<Date[]>
        implements DynamicParameterizedType {

    public static final DateArrayType INSTANCE = new DateArrayType();

    public DateArrayType() {
        super(
                ArraySqlTypeDescriptor.INSTANCE,
                new DateArrayTypeDescriptor()
        );
    }

    public DateArrayType(Configuration configuration) {
        super(
                ArraySqlTypeDescriptor.INSTANCE,
                new DateArrayTypeDescriptor(), configuration
        );
    }

    public String getName() {
        return "date-array";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((DateArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}

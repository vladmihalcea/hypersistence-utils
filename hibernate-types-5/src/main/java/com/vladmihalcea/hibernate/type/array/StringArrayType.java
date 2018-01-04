package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.ArraySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.StringArrayTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps an {@code String[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class StringArrayType
        extends AbstractSingleColumnStandardBasicType<String[]>
        implements DynamicParameterizedType {

    public static final StringArrayType INSTANCE = new StringArrayType();

    public StringArrayType() {
        super(ArraySqlTypeDescriptor.INSTANCE, StringArrayTypeDescriptor.INSTANCE);
    }

    public String getName() {
        return "string-array";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((StringArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}
package com.vladmihalcea.hibernate.type.array;

import java.util.Properties;
import java.util.UUID;

import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import com.vladmihalcea.hibernate.type.array.internal.ArraySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.StringArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.UUIDArrayTypeDescriptor;

/**
 * Maps an {@code UUID[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Rafael Acevedo
 */
public class UUIDArrayType
        extends AbstractSingleColumnStandardBasicType<UUID[]>
        implements DynamicParameterizedType {

    public static final UUIDArrayType INSTANCE = new UUIDArrayType();

    public UUIDArrayType() {
        super(ArraySqlTypeDescriptor.INSTANCE, new UUIDArrayTypeDescriptor());
    }

    public String getName() {
        return "uuid-array";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((UUIDArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}
package com.vladmihalcea.hibernate.type.array;

import java.util.Properties;

import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import com.vladmihalcea.hibernate.type.array.internal.ArraySqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.array.internal.LongArrayTypeDescriptor;

/**
 * Maps an {@code long[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this
 * article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class LongArrayType
		extends AbstractSingleColumnStandardBasicType<long[]>
        implements DynamicParameterizedType {

    public static final LongArrayType INSTANCE = new LongArrayType();

    public LongArrayType() {
		super(ArraySqlTypeDescriptor.INSTANCE, new LongArrayTypeDescriptor());
    }

    @Override
	public String getName() {
		return "long-array";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

    @Override
    public void setParameterValues(Properties parameters) {
		((LongArrayTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}
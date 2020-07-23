package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.BooleanArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import com.vladmihalcea.hibernate.type.util.ParameterizedParameterType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps an {@code boolean[]} array on a PostgreSQL ARRAY type.
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a>.
 *
 * @author jeet.choudhary7@gmail.com
 * @version 1.0
 * @since July 18, 2020
 */
public class BooleanArrayType extends AbstractArrayType<boolean[]> {

    public static final BooleanArrayType INSTANCE = new BooleanArrayType();

    public BooleanArrayType(Configuration configuration) {
        super(
                new BooleanArrayTypeDescriptor(),
                configuration
        );
    }

    public BooleanArrayType(Class arrayClass) {
        this();
        Properties parameters = new Properties();
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterizedParameterType(arrayClass));
        setParameterValues(parameters);
    }

    public BooleanArrayType() {
        super(
                new BooleanArrayTypeDescriptor()
        );
    }

    @Override
    public String getName() {

        return "boolean-array";
    }
}
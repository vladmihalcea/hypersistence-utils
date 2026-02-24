package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.array.internal.GenericArrayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.type.util.ParameterizedParameterType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps a {@code boolean[]} array on a PostgreSQL ARRAY column type.
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a>.
 *
 * @author jeet.choudhary7@gmail.com
 * @version 3.14.1
 */
public class MultiDimensionalArrayType extends AbstractArrayType<Object> {

    public static final MultiDimensionalArrayType INSTANCE = new MultiDimensionalArrayType();

    public MultiDimensionalArrayType() {
        super(
            new GenericArrayTypeDescriptor()
        );
    }

    public MultiDimensionalArrayType(Configuration configuration) {
        super(
            new GenericArrayTypeDescriptor(),
            configuration
        );
    }

    public MultiDimensionalArrayType(Class arrayClass) {
        this();
        Properties parameters = new Properties();
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterizedParameterType(arrayClass));
        setParameterValues(parameters);
    }

    public MultiDimensionalArrayType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "multi-dimensional-array";
    }
}
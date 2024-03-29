package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.array.internal.TimestampArrayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.type.util.ParameterizedParameterType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Date;
import java.util.Properties;

/**
 * Maps an {@code Date[]} array on a PostgreSQL timestamp[] ARRAY type. Multidimensional arrays are supported as well, as explained in <a href="https://vladmihalcea.com/multidimensional-array-jpa-hibernate/">this article</a>.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class TimestampArrayType extends AbstractArrayType<Date[]> {

    public static final TimestampArrayType INSTANCE = new TimestampArrayType();

    public TimestampArrayType() {
        super(
            new TimestampArrayTypeDescriptor()
        );
    }

    public TimestampArrayType(Configuration configuration) {
        super(
            new TimestampArrayTypeDescriptor(), configuration
        );
    }

    public TimestampArrayType(Class arrayClass) {
        this();
        Properties parameters = new Properties();
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterizedParameterType(arrayClass));
        setParameterValues(parameters);
    }

    public String getName() {
        return "timestamp-array";
    }
}

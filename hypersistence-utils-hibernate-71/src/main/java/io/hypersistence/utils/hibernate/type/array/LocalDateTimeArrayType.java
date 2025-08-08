package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.array.internal.LocalDateTimeArrayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.type.util.ParameterizedParameterType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

/**
 * Maps a {@code java.Time.LocalDateTime[]} array on a PostgreSQL timestamp[] ARRAY type. Multidimensional arrays are
 * supported as well, as
 * explained in <a href="https://vladmihalcea.com/multidimensional-array-jpa-hibernate/">this article</a>.
 * <p>
 * For more details about how to use it, check out
 * <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this
 * article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */

public class LocalDateTimeArrayType extends AbstractArrayType<java.time.LocalDateTime[]> {

    public static final LocalDateTimeArrayType INSTANCE =
        new LocalDateTimeArrayType();

    public LocalDateTimeArrayType() {
        super(
            new LocalDateTimeArrayTypeDescriptor()
        );
    }

    public LocalDateTimeArrayType(Configuration configuration) {
        super(
            new LocalDateTimeArrayTypeDescriptor(), configuration
        );
    }

    public LocalDateTimeArrayType(Class arrayClass) {
        this();
        Properties parameters = new Properties();
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterizedParameterType(arrayClass));
        setParameterValues(parameters);
    }

    public LocalDateTimeArrayType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "localdatetime-array";
    }
}

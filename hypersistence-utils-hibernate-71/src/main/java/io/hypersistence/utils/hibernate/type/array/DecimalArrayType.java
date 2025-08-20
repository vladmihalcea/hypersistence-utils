package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.AbstractArrayType;
import io.hypersistence.utils.hibernate.type.array.internal.DecimalArrayTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import io.hypersistence.utils.hibernate.type.util.ParameterizedParameterType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.math.BigDecimal;
import java.util.Properties;

/**
 * Maps a {@code decimal[]} array on a PostgreSQL ARRAY column type.
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a>.
 *
 * @author Moritz Kobel
 */
public class DecimalArrayType extends AbstractArrayType<BigDecimal[]> {

    public static final DecimalArrayType INSTANCE = new DecimalArrayType();

    public DecimalArrayType() {
        super(
            new DecimalArrayTypeDescriptor()
        );
    }

    public DecimalArrayType(Configuration configuration) {
        super(
            new DecimalArrayTypeDescriptor(),
            configuration
        );
    }

    public DecimalArrayType(Class arrayClass) {
        this();
        Properties parameters = new Properties();
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterizedParameterType(arrayClass));
        setParameterValues(parameters);
    }

    public DecimalArrayType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "decimal-array";
    }
}
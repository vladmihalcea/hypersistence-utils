package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.DoubleArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

/**
 * Maps an {@code double[]} array on a PostgreSQL ARRAY type. Multidimensional arrays are supported as well, as explained in <a href="https://vladmihalcea.com/multidimensional-array-jpa-hibernate/">this article</a>.
 * <p>
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this
 * article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class DoubleArrayType extends AbstractArrayType<double[]> {

    public static final DoubleArrayType INSTANCE = new DoubleArrayType();

    public DoubleArrayType() {
        super(
            new DoubleArrayTypeDescriptor()
        );
    }

    public DoubleArrayType(Configuration configuration) {
        super(
            new DoubleArrayTypeDescriptor(),
            configuration
        );
    }

    @Override
    public String getName() {
        return "double-array";
    }
}

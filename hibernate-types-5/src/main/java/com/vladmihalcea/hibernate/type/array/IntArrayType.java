package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.IntArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

/**
 * Maps an {@code int[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class IntArrayType extends AbstractArrayType<int[]> {

    public static final IntArrayType INSTANCE = new IntArrayType();

    public IntArrayType() {
        super(
            new IntArrayTypeDescriptor()
        );
    }

    public IntArrayType(Configuration configuration) {
        super(
            new IntArrayTypeDescriptor(), configuration
        );
    }

    public String getName() {
        return "int-array";
    }
}
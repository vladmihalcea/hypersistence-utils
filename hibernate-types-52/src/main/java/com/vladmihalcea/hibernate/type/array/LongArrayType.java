package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.LongArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

/**
 * Maps an {@code long[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href=
 * "https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this
 * article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class LongArrayType extends AbstractArrayType<long[]> {

    public static final LongArrayType INSTANCE = new LongArrayType();

    public LongArrayType() {
        super(
            new LongArrayTypeDescriptor()
        );
    }

    public LongArrayType(Configuration configuration) {
        super(
            new LongArrayTypeDescriptor(),
            configuration
        );
    }

    @Override
    public String getName() {
        return "long-array";
    }
}
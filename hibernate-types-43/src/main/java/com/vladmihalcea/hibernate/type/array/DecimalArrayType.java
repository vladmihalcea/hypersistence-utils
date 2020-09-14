package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.DecimalArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

import java.math.BigDecimal;

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

    @Override
    public String getName() {
        return "decimal-array";
    }
}


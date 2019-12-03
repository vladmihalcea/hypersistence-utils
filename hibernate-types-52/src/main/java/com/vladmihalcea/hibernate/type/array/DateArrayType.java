package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.DateArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

import java.util.Date;

/**
 * Maps an {@code Date[]} array on a PostgreSQL date[] ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Guillaume Briand
 */
public class DateArrayType extends AbstractArrayType<Date[]> {

    public static final DateArrayType INSTANCE = new DateArrayType();

    public DateArrayType() {
        super(
            new DateArrayTypeDescriptor()
        );
    }

    public DateArrayType(Configuration configuration) {
        super(
            new DateArrayTypeDescriptor(), configuration
        );
    }

    public String getName() {
        return "date-array";
    }
}

package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.ListArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

/**
 * Maps an {@link java.util.List} entity attribute on a PostgreSQL ARRAY column type.
 *
 * @author Vlad Mihalcea
 */
public class ListArrayType extends AbstractArrayType<Object> {

    public static final ListArrayType INSTANCE = new ListArrayType();

    public ListArrayType() {
        super(
            new ListArrayTypeDescriptor()
        );
    }

    public ListArrayType(Configuration configuration) {
        super(
            new ListArrayTypeDescriptor(), configuration
        );
    }

    public String getName() {
        return "list-array";
    }
}
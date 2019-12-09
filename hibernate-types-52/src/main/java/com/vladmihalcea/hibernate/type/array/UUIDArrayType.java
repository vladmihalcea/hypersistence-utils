package com.vladmihalcea.hibernate.type.array;

import com.vladmihalcea.hibernate.type.array.internal.AbstractArrayType;
import com.vladmihalcea.hibernate.type.array.internal.UUIDArrayTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;

import java.util.UUID;

/**
 * Maps an {@code UUID[]} array on a PostgreSQL ARRAY type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/how-to-map-java-and-sql-arrays-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Rafael Acevedo
 */
public class UUIDArrayType extends AbstractArrayType<UUID[]> {

    public static final UUIDArrayType INSTANCE = new UUIDArrayType();

    public UUIDArrayType() {
        super(
            new UUIDArrayTypeDescriptor()
        );
    }

    public UUIDArrayType(Configuration configuration) {
        super(
            new UUIDArrayTypeDescriptor(),
            configuration
        );
    }

    public String getName() {
        return "uuid-array";
    }
}
package io.hypersistence.utils.hibernate.type.array.internal;

import java.util.UUID;

/**
 * @author Rafael Acevedo
 */
public class UUIDArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<UUID[]> {

    public UUIDArrayTypeDescriptor() {
        super(UUID[].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "uuid";
    }
}

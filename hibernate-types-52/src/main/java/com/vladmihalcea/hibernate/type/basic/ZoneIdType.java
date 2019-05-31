package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.basic.internal.ZoneIdTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

import java.time.ZoneId;

/**
 * Maps a Java {@link ZoneId} object to an {@code VARCHAR} column type.
 *
 */
public class ZoneIdType extends AbstractSingleColumnStandardBasicType<ZoneId> {

    public static final ZoneIdType INSTANCE = new ZoneIdType();

    public ZoneIdType() {
        super(VarcharTypeDescriptor.INSTANCE, ZoneIdTypeDescriptor.INSTANCE);
    }

    @Override
    public String getName() {
        return "zone-id";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }
}

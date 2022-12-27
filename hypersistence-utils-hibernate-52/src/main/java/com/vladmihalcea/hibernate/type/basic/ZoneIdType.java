package com.vladmihalcea.hibernate.type.basic;

import com.vladmihalcea.hibernate.type.AbstractHibernateType;
import com.vladmihalcea.hibernate.type.basic.internal.ZoneIdTypeDescriptor;
import com.vladmihalcea.hibernate.type.util.Configuration;
import org.hibernate.type.descriptor.sql.VarcharTypeDescriptor;

import java.time.ZoneId;

/**
 * Maps a Java {@link ZoneId} object to an {@code VARCHAR} column type.
 *
 * @author stonio
 */
public class ZoneIdType extends AbstractHibernateType<ZoneId> {

    public static final ZoneIdType INSTANCE = new ZoneIdType();

    public ZoneIdType() {
        super(
            VarcharTypeDescriptor.INSTANCE,
            ZoneIdTypeDescriptor.INSTANCE
        );
    }

    public ZoneIdType(Configuration configuration) {
        super(
            VarcharTypeDescriptor.INSTANCE,
            ZoneIdTypeDescriptor.INSTANCE,
            configuration
        );
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

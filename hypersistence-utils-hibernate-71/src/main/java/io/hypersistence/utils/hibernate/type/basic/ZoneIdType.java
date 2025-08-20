package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.hibernate.type.DescriptorImmutableType;
import io.hypersistence.utils.hibernate.type.basic.internal.ZoneIdTypeDescriptor;
import io.hypersistence.utils.hibernate.type.util.Configuration;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.jdbc.VarcharJdbcType;

import java.time.Year;
import java.time.ZoneId;

/**
 * Maps a Java {@link ZoneId} object to an {@code VARCHAR} column type.
 *
 * @author stonio
 */
public class ZoneIdType extends DescriptorImmutableType<ZoneId, VarcharJdbcType, ZoneIdTypeDescriptor> {

    public static final ZoneIdType INSTANCE = new ZoneIdType();

    public ZoneIdType() {
        super(
            ZoneId.class,
            VarcharJdbcType.INSTANCE,
            ZoneIdTypeDescriptor.INSTANCE
        );
    }

    public ZoneIdType(Configuration configuration) {
        super(
            ZoneId.class,
            VarcharJdbcType.INSTANCE,
            ZoneIdTypeDescriptor.INSTANCE,
            configuration
        );
    }

    public ZoneIdType(org.hibernate.type.spi.TypeBootstrapContext typeBootstrapContext) {
        this(new Configuration(typeBootstrapContext.getConfigurationSettings()));
    }

    public String getName() {
        return "zone-id";
    }

    @Override
    public ZoneId fromStringValue(CharSequence charSequence) throws HibernateException {
        return charSequence != null ? ZoneId.of((String) charSequence) : null;
    }
}

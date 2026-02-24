package io.hypersistence.utils.hibernate.type.basic.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;

import java.time.ZoneId;
import java.util.Comparator;

/**
 * Descriptor for {@link ZoneId} handling.
 *
 * @author stonio
 */
public class ZoneIdTypeDescriptor extends AbstractClassJavaType<ZoneId> {

    public static final ZoneIdTypeDescriptor INSTANCE = new ZoneIdTypeDescriptor();

    public static class ZoneIdComparator implements Comparator<ZoneId> {
        public static final ZoneIdComparator INSTANCE = new ZoneIdComparator();

        public int compare(ZoneId o1, ZoneId o2) {
            return o1.getId().compareTo(o2.getId());
        }
    }

    public ZoneIdTypeDescriptor() {
        super(ZoneId.class);
    }

    public String toString(ZoneId value) {
        return value.getId();
    }

    public ZoneId fromString(String string) {
        return ZoneId.of(string);
    }

    @Override
    public Comparator<ZoneId> getComparator() {
        return ZoneIdComparator.INSTANCE;
    }

    @SuppressWarnings({ "unchecked" })
    public <X> X unwrap(ZoneId value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (String.class.isAssignableFrom(type)) {
            return (X) toString(value);
        }
        throw unknownUnwrap(type);
    }

    public <X> ZoneId wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (String.class.isInstance(value)) {
            return fromString((String) value);
        }
        throw unknownWrap(value.getClass());
    }
}

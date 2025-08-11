package io.hypersistence.utils.hibernate.type.basic.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractClassJavaType;

import java.time.Month;
import java.util.Objects;

/**
 * @author Martin Panzer
 */
public class Iso8601MonthTypeDescriptor extends AbstractClassJavaType<Month> {

    public static final Iso8601MonthTypeDescriptor INSTANCE = new Iso8601MonthTypeDescriptor();

    public Iso8601MonthTypeDescriptor() {
        super(Month.class);
    }

    @Override
    public boolean areEqual(Month one, Month another) {
        return Objects.equals(one, another);
    }

    @Override
    public String toString(Month value) {
        return value.toString();
    }

    @SuppressWarnings({"unchecked"})
    @Override
    public <X> X unwrap(Month value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (Number.class.isAssignableFrom(type)) {
            Number intValue = value.getValue();
            if(Byte.class.isAssignableFrom(type)) {
                return (X) (Byte) intValue.byteValue();
            } else if(Short.class.isAssignableFrom(type)) {
                return (X) (Short) intValue.shortValue();
            }
            return (X) (Integer) intValue.intValue();
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> Month wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number) {
            int numericValue = ((Number) (value)).intValue();
            return Month.of(numericValue);
        }
        throw unknownWrap(value.getClass());
    }
}

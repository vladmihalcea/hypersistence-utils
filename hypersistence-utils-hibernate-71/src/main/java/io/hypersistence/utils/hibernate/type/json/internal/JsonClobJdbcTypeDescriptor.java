package io.hypersistence.utils.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.ClobJdbcType;

/**
 * @author Vlad Mihalcea
 * @author Andreas Gebhardt
 */
public class JsonClobJdbcTypeDescriptor extends AbstractJsonJdbcTypeDescriptor {

    public static final JsonClobJdbcTypeDescriptor INSTANCE = new JsonClobJdbcTypeDescriptor();

    private final ClobJdbcType clobTypeDescriptor = ClobJdbcType.DEFAULT;

    @Override
    public int getJdbcTypeCode() {
        return clobTypeDescriptor.getJdbcTypeCode();
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return clobTypeDescriptor.getBinder(javaType);
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return clobTypeDescriptor.getExtractor(javaType);
    }
}

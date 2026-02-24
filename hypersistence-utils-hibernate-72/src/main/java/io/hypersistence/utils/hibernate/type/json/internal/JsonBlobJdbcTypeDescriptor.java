package io.hypersistence.utils.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaType;
import org.hibernate.type.descriptor.jdbc.BlobJdbcType;

/**
 * @author Vlad Mihalcea
 */
public class JsonBlobJdbcTypeDescriptor extends AbstractJsonJdbcTypeDescriptor {

    public static final JsonBlobJdbcTypeDescriptor INSTANCE = new JsonBlobJdbcTypeDescriptor();

    private BlobJdbcType blobTypeDescriptor = BlobJdbcType.DEFAULT;

    @Override
    public int getJdbcTypeCode() {
        return blobTypeDescriptor.getJdbcTypeCode();
    }

    @Override
    public <X> ValueBinder<X> getBinder(JavaType<X> javaType) {
        return blobTypeDescriptor.getBinder(javaType);
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaType<X> javaType) {
        return blobTypeDescriptor.getExtractor(javaType);
    }
}

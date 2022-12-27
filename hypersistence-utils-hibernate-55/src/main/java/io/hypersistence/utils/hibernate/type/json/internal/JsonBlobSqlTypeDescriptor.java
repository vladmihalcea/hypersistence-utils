package io.hypersistence.utils.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.BlobTypeDescriptor;

/**
 * @author Vlad Mihalcea
 */
public class JsonBlobSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor {

    public static final JsonBlobSqlTypeDescriptor INSTANCE = new JsonBlobSqlTypeDescriptor();

    private BlobTypeDescriptor blobTypeDescriptor = BlobTypeDescriptor.DEFAULT;

    @Override
    public <X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return blobTypeDescriptor.getBinder(javaTypeDescriptor);
    }

    @Override
    public int getSqlType() {
        return blobTypeDescriptor.getSqlType();
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return blobTypeDescriptor.getExtractor(javaTypeDescriptor);
    }
}

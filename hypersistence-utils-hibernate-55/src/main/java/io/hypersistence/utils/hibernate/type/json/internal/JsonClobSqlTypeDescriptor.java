package io.hypersistence.utils.hibernate.type.json.internal;

import org.hibernate.type.descriptor.ValueBinder;
import org.hibernate.type.descriptor.ValueExtractor;
import org.hibernate.type.descriptor.java.JavaTypeDescriptor;
import org.hibernate.type.descriptor.sql.ClobTypeDescriptor;

/**
 * @author Vlad Mihalcea
 */
public class JsonClobSqlTypeDescriptor extends AbstractJsonSqlTypeDescriptor {

    public static final JsonClobSqlTypeDescriptor INSTANCE = new JsonClobSqlTypeDescriptor();

    private ClobTypeDescriptor clobTypeDescriptor = ClobTypeDescriptor.DEFAULT;

    @Override
    public <X> ValueBinder<X> getBinder(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return clobTypeDescriptor.getBinder(javaTypeDescriptor);
    }

    @Override
    public int getSqlType() {
        return clobTypeDescriptor.getSqlType();
    }

    @Override
    public <X> ValueExtractor<X> getExtractor(JavaTypeDescriptor<X> javaTypeDescriptor) {
        return clobTypeDescriptor.getExtractor(javaTypeDescriptor);
    }
}

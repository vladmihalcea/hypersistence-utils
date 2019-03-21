package com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters.internal;

import com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters.PGTSVector;
import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;

/**
 * @author Lukman Adekunle
 */
public class TSVectorTypeDescriptor extends AbstractTypeDescriptor<PGTSVector> {

    public static final TSVectorTypeDescriptor INSTANCE = new TSVectorTypeDescriptor();

    public TSVectorTypeDescriptor() {
        super(PGTSVector.class);
    }

    @Override
    public boolean areEqual(PGTSVector one, PGTSVector another) {
        if (one == another) {
            return true;
        }
        if (one == null || another == null) {
            return false;
        }
        return one.equals(another);
    }

    @Override
    public String toString(PGTSVector value) {
        return value.getValue();
    }

    @Override
    public PGTSVector fromString(String string) {
        PGTSVector pgtsVector = new PGTSVector();
        pgtsVector.setValue(string);
        return pgtsVector;
    }

    @Override
    @SuppressWarnings({"unchecked"})
    public <X> X unwrap(PGTSVector value, Class<X> type, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        if (PGTSVector.class.isAssignableFrom(type)) {
            return (X) value;
        }
        throw unknownUnwrap(type);
    }

    @Override
    public <X> PGTSVector wrap(X value, WrapperOptions options) {
        if (value == null) {
            return null;
        }
        return fromString(value.toString());
    }

}

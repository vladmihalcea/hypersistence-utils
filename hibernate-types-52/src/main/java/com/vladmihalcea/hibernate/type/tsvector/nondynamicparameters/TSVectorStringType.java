package com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters;

import com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters.internal.TSVectorSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.tsvector.nondynamicparameters.internal.TSVectorTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;

/**
 * @author Lukman Adekunle
 */
public class TSVectorStringType extends AbstractSingleColumnStandardBasicType<PGTSVector> {

    public static final TSVectorStringType INSTANCE = new TSVectorStringType();

    public TSVectorStringType() {
        super(TSVectorSqlTypeDescriptor.INSTANCE, TSVectorTypeDescriptor.INSTANCE);
    }

    @Override
    public String getName() {
        return "tsvector";
    }

    @Override
    protected boolean registerUnderJavaType() {
        return true;
    }

}
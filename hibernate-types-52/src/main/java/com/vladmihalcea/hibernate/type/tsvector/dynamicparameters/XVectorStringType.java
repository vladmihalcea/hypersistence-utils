package com.vladmihalcea.hibernate.type.tsvector.dynamicparameters;

import com.vladmihalcea.hibernate.type.tsvector.dynamicparameters.internal.XVectorSqlTypeDescriptor;
import com.vladmihalcea.hibernate.type.tsvector.dynamicparameters.internal.XVectorTypeDescriptor;
import org.hibernate.type.AbstractSingleColumnStandardBasicType;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;
/**
 * @author Lukman Adekunle
 */
public class XVectorStringType extends AbstractSingleColumnStandardBasicType<Object> implements DynamicParameterizedType {

    public static final XVectorStringType INSTANCE = new XVectorStringType();

    public XVectorStringType() {
        super(XVectorSqlTypeDescriptor.INSTANCE, XVectorTypeDescriptor.INSTANCE);
    }

    @Override
    public String getName() {
        return "tsvector";
    }

    @Override
    public void setParameterValues(Properties parameters) {
        ((XVectorTypeDescriptor) getJavaTypeDescriptor()).setParameterValues(parameters);
    }
}

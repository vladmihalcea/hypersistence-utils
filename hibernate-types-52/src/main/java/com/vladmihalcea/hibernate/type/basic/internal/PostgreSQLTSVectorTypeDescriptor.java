package com.vladmihalcea.hibernate.type.basic.internal;

import org.hibernate.type.descriptor.WrapperOptions;
import org.hibernate.type.descriptor.java.AbstractTypeDescriptor;
import org.hibernate.usertype.DynamicParameterizedType;

import java.util.Properties;

public class PostgreSQLTSVectorTypeDescriptor extends AbstractTypeDescriptor<String> implements DynamicParameterizedType {

    public PostgreSQLTSVectorTypeDescriptor() {
        super(String.class);
    }

    @Override
    public String fromString(String string) {
        return null;
    }

    @Override
    public <X> X unwrap(String value, Class<X> type, WrapperOptions options) {
        return null;
    }

    @Override
    public <X> String wrap(X value, WrapperOptions options) {
        return null;
    }

    @Override
    public void setParameterValues(Properties parameters) {

    }
}

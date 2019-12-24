package com.vladmihalcea.hibernate.type.array.internal;

import org.hibernate.type.descriptor.WrapperOptions;

/**
 * @author Vlad Mihalcea
 */
public class String2DArrayTypeDescriptor
        extends AbstractArrayTypeDescriptor<String[][]> {

    public String2DArrayTypeDescriptor() {
        super(String[][].class);
    }

    @Override
    protected String getSqlArrayType() {
        return "text";
    }

    @Override
    public <X> String[][] wrap(X value, WrapperOptions options) {
        Object[] array = super.wrap(value, options);
        if(array.length == 0)
            return new String[][] { };
        return (String[][]) array;
    }
}

package io.hypersistence.utils.hibernate.type.util;

import org.hibernate.usertype.DynamicParameterizedType;

import java.lang.annotation.Annotation;

/**
 * A stub {@code ParameterType} that returns sane values for {@link #getReturnedClass()} and
 * {@link #getAnnotationsMethod()}.
 *
 * @author Jan-Willem Gmelig Meyling
 */
public class ParameterizedParameterType implements DynamicParameterizedType.ParameterType {

    private final Class<?> clasz;

    public ParameterizedParameterType(Class<?> clasz) {
        this.clasz = clasz;
    }

    @Override
    public Class getReturnedClass() {
        return clasz;
    }

    @Override
    public Annotation[] getAnnotationsMethod() {
        return new Annotation[0];
    }

    @Override
    public String getCatalog() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getSchema() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String getTable() {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isPrimaryKey() {
        throw new UnsupportedOperationException();
    }

    @Override
    public String[] getColumns() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Long[] getColumnLengths() {
        throw new UnsupportedOperationException();
    }
}

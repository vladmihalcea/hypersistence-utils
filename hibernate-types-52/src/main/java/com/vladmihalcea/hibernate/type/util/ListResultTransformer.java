package com.vladmihalcea.hibernate.type.util;

import org.hibernate.transform.ResultTransformer;

import java.util.List;

/**
 * The {@link com.vladmihalcea.hibernate.type.util.ListResultTransformer} simplifies the way
 * we can use a ResultTransformer by defining a default implementation for the
 * {@link ResultTransformer#transformList(List)} method.
 *
 * This way, the {@link com.vladmihalcea.hibernate.type.util.ListResultTransformer} can be used
 * as a {@link @FunctionalInterface}.
 *
 * @author Vlad Mihalcea
 * @since 2.9.0
 */
@FunctionalInterface
public interface ListResultTransformer extends ResultTransformer {

    @Override
    default List transformList(List collection) {
        return collection;
    }
}

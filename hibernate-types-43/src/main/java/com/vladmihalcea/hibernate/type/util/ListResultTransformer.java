package com.vladmihalcea.hibernate.type.util;

import org.hibernate.transform.ResultTransformer;

import java.util.List;

/**
 * The {@link com.vladmihalcea.hibernate.type.util.ListResultTransformer} simplifies the way
 * we can use a ResultTransformer by defining a default implementation for the
 * {@link ResultTransformer#transformList(List)} method.
 * <p>
 * This way, the {@link com.vladmihalcea.hibernate.type.util.ListResultTransformer} can be used
 * as a functional interface.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/hibernate-resulttransformer/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @deprecated use {@link com.vladmihalcea.hibernate.query.ListResultTransformer} instead
 *
 * @author Vlad Mihalcea
 * @since 2.9.0
 */
@Deprecated
public abstract class ListResultTransformer extends com.vladmihalcea.hibernate.query.ListResultTransformer {

}

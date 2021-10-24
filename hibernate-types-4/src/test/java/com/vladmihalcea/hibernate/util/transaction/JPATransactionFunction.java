package com.vladmihalcea.hibernate.util.transaction;

import javax.persistence.EntityManager;

/**
 * @author Vlad Mihalcea
 */
public abstract class JPATransactionFunction<T> {

    public abstract T apply(EntityManager entityManager);

    public void beforeTransactionCompletion() {

    }

    public void afterTransactionCompletion() {

    }
}

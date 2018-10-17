package com.vladmihalcea.hibernate.type.util.transaction;

import javax.persistence.EntityManager;

/**
 * @author Vlad Mihalcea
 */
public abstract class JPATransactionVoidFunction {

    public abstract void accept(EntityManager t);

    public void beforeTransactionCompletion() {

    }

    public void afterTransactionCompletion() {

    }
}

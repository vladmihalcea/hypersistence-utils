package com.vladmihalcea.hibernate.type.util;

/**
 * @author Vlad Mihalcea
 */
public interface EntityProvider {

    /**
     * Entity types shared among multiple test configurations
     *
     * @return entity types
     */
    Class<?>[] entities();
}

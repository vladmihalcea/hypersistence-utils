package com.vladmihalcea.hibernate.type.util;

import javax.json.bind.Jsonb;

/**
 * Supplies a custom reference of a Jackson {@link Jsonb}
 *
 * @author Vlad Mihalcea
 * @author Jan-Willem Gmelig Meyling
 */
public interface JsonbSupplier {

    /**
     * Get custom {@link Jsonb} reference
     *
     * @return custom {@link Jsonb} reference
     */
    Jsonb get();
}

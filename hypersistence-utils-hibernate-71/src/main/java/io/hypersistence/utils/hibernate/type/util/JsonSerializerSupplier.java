package io.hypersistence.utils.hibernate.type.util;

import java.io.Serializable;

/**
 * Supplies a custom reference of a Jackson {@link JsonSerializer}
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public interface JsonSerializerSupplier extends Serializable {

    /**
     * Get custom {@link JsonSerializer} reference
     *
     * @return custom {@link JsonSerializer} reference
     */
    JsonSerializer get();
}

package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Supplies a custom reference of a Jackson {@link JsonSerializer}
 *
 * @author Vlad Mihalcea
 * @since 2.1.0
 */
public interface JsonSerializerSupplier {

    /**
     * Get custom {@link JsonSerializer} reference
     *
     * @return custom {@link JsonSerializer} reference
     */
    JsonSerializer get();
}

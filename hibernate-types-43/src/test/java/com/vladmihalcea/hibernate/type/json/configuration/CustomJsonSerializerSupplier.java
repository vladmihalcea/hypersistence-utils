package com.vladmihalcea.hibernate.type.json.configuration;

import com.vladmihalcea.hibernate.type.util.JsonSerializer;
import com.vladmihalcea.hibernate.type.util.JsonSerializerSupplier;

/**
 * @author Vlad Mihalcea
 */
public class CustomJsonSerializerSupplier implements JsonSerializerSupplier {

    @Override
    public JsonSerializer get() {
        return new CustomJsonSerializer();
    }
}

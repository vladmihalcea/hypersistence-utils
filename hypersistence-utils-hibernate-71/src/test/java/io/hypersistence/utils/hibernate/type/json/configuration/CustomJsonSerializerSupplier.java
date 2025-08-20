package io.hypersistence.utils.hibernate.type.json.configuration;

import io.hypersistence.utils.hibernate.type.util.JsonSerializer;
import io.hypersistence.utils.hibernate.type.util.JsonSerializerSupplier;

/**
 * @author Vlad Mihalcea
 */
public class CustomJsonSerializerSupplier implements JsonSerializerSupplier {

    @Override
    public JsonSerializer get() {
        return new CustomJsonSerializer();
    }
}

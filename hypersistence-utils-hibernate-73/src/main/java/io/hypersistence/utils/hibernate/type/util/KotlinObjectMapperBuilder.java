package io.hypersistence.utils.hibernate.type.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.module.kotlin.KotlinFeature;
import tools.jackson.module.kotlin.KotlinModule;

/**
 * Builds the default {@link ObjectMapper} for Kotlin.
 *
 * @author Vlad Mihalcea
 * @since 3.13.0
 */
public class KotlinObjectMapperBuilder {

    public static JsonMapper.Builder builder() {
        KotlinModule kotlinModule = new KotlinModule.Builder()
            .enable(KotlinFeature.StrictNullChecks)
            .build();
        return JsonMapper.builder()
            .addModule(kotlinModule);
    }
}

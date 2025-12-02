package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.kotlin.KotlinFeature;
import com.fasterxml.jackson.module.kotlin.KotlinModule;

/**
 * Builds the default {@link ObjectMapper} for Kotlin.
 *
 * @author Vlad Mihalcea
 * @since 3.13.0
 */
public class KotlinObjectMapperBuilder {

    public static ObjectMapper build() {
        KotlinModule kotlinModule = new KotlinModule.Builder()
            .enable(KotlinFeature.StrictNullChecks)
            .build();
        return JsonMapper.builder()
            .addModule(kotlinModule)
            .build();
    }
}

package io.hypersistence.utils.hibernate.type.util;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import tools.jackson.module.kotlin.KotlinFeature;
import tools.jackson.module.kotlin.KotlinModule;

import java.time.OffsetDateTime;

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
            .findAndAddModules()
            .addModule(kotlinModule)
            .addModule(
                new SimpleModule()
                    .addSerializer(OffsetDateTime.class, ObjectMapperWrapper.OffsetDateTimeSerializer.INSTANCE)
                    .addDeserializer(OffsetDateTime.class, ObjectMapperWrapper.OffsetDateTimeDeserializer.INSTANCE)
            )
            .build();
    }
}

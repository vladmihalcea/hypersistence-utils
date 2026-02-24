package io.hypersistence.utils.hibernate.type.json.configuration;

import tools.jackson.core.Version;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;

import java.util.TimeZone;

/**
 * @author Vlad Mihalcea
 */
public class CustomObjectMapperSupplier implements ObjectMapperSupplier {

    @Override
    public ObjectMapper get() {
        return JsonMapper.builder()
            .defaultTimeZone(TimeZone.getTimeZone("GMT"))
            .addModule(new SimpleModule("SimpleModule", new Version(1, 0, 0, null, null, null))
                .addSerializer(new MoneySerializer())
            )
           .build();


    }
}

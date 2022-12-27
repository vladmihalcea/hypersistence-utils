package io.hypersistence.utils.hibernate.type.json.configuration;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;

import java.util.TimeZone;

/**
 * @author Vlad Mihalcea
 */
public class CustomObjectMapperSupplier implements ObjectMapperSupplier {

    @Override
    public ObjectMapper get() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.setTimeZone(TimeZone.getTimeZone("GMT"));
        SimpleModule simpleModule = new SimpleModule("SimpleModule", new Version(1, 0, 0, null, null, null));
        simpleModule.addSerializer(new MoneySerializer());
        objectMapper.registerModule(simpleModule);
        return objectMapper;
    }
}

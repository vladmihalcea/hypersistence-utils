package com.vladmihalcea.hibernate.type.json.loader;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.vladmihalcea.hibernate.type.util.ObjectMapperSupplier;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.TimeZone;

/**
 * @author Vlad Mihalcea
 */
public class CustomObjectMapperSupplier implements ObjectMapperSupplier {

    @Override
    public ObjectMapper get() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        objectMapper.setTimeZone(TimeZone.getTimeZone("GMT"));
        SimpleModule simpleModule = new SimpleModule("SimpleModule", new Version(1,0,0,null));
        simpleModule.addSerializer(new MoneySerializer());
        objectMapper.registerModule(simpleModule);
        return objectMapper;
    }

    public static class MoneySerializer extends JsonSerializer<BigDecimal> {

        @Override
        public void serialize(BigDecimal value, JsonGenerator jsonGenerator, SerializerProvider provider)
                throws IOException {
            jsonGenerator.writeString(value.setScale(2, BigDecimal.ROUND_HALF_UP).toString());
        }

        @Override
        public Class<BigDecimal> handledType() {
            return BigDecimal.class;
        }
    }
}

package io.hypersistence.utils.hibernate.type.json.configuration;


import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * @author Vlad Mihalcea
 */
public class MoneySerializer extends ValueSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal value, JsonGenerator jsonGenerator, SerializationContext context)
            throws JacksonException {
        jsonGenerator.writeString(value.setScale(2, BigDecimal.ROUND_HALF_UP).toString());
    }

    @Override
    public Class<BigDecimal> handledType() {
        return BigDecimal.class;
    }
}

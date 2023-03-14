package io.hypersistence.utils.hibernate.type.json;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperSupplier;

public class JacksonPropertyInclusionNonEmptyObjectMapperSupplier implements ObjectMapperSupplier {
    @Override
    public ObjectMapper get() {
        return new ObjectMapper().setDefaultPropertyInclusion(JsonInclude.Include.NON_EMPTY);
    }
}

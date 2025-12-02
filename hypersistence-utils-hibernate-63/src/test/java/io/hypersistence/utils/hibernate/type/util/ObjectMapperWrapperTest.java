package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ObjectMapperWrapperTest {

    @Test
    public void testObjectMapperDefaultInstance() {
        ObjectMapper objectMapper = ObjectMapperWrapper.INSTANCE.getObjectMapper();

        assertTrue(
            objectMapper.getRegisteredModuleIds().contains("com.fasterxml.jackson.module.kotlin.KotlinModule")
        );
    }
}

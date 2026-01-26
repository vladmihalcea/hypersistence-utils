package io.hypersistence.utils.hibernate.type.util;

import tools.jackson.databind.ObjectMapper;
import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class ObjectMapperWrapperTest {

    @Test
    public void testObjectMapperDefaultInstance() {
        ObjectMapper objectMapper = ObjectMapperWrapper.INSTANCE.getObjectMapper();

        assertTrue(
            objectMapper.registeredModules().stream()
                        .anyMatch(m -> "tools.jackson.module.kotlin.KotlinModule".equals(m.getModuleName()))
        );
    }
}

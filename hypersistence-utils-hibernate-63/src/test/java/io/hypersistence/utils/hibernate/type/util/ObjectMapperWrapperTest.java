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

    @Test
    public void testFromStringFailureDoesNotLeakPayload() {
        try {
            ObjectMapperWrapper.INSTANCE.fromString("{invalid-json-payload-with-secret}", Object.class);
            org.junit.Assert.fail("Expected HibernateException");
        } catch (org.hibernate.HibernateException e) {
            org.junit.Assert.assertFalse(e.getMessage().contains("invalid-json-payload-with-secret"));
            org.junit.Assert.assertTrue(e.getMessage().contains("cannot be transformed to Json object"));
        }
    }
}

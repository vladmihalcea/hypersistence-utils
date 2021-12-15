package com.vladmihalcea.hibernate.type.util.objectmapper;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.vladmihalcea.hibernate.type.util.ObjectMapperSupplier;
import java.time.OffsetDateTime;

/**
 * Jackson Object Mapper for jsonb hibernate type to persist a java.time.OffsetDateTime without losing timezone.
 *
 * to register this mapper create <code>hibernate-types.properties</code> file and add the following property:
 * <code>hibernate.types.jackson.object.mapper=com.vladmihalcea.hibernate.type.util.objectmapper.OffsetDateTimeObjectMapperSupplier</code>
 */
public class OffsetDateTimeObjectMapperSupplier implements ObjectMapperSupplier {

    private static final String OFFSET_DATE_TIME_MODULE_NAME = "OffsetDateTimeModule";

    private static final Version OFFSET_DATE_TIME_MODULE_VERSION = new Version(1,0,0, null, null, null);

    @Override
    public ObjectMapper get() {
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

        SimpleModule offsetDateTimeModule = new SimpleModule(OFFSET_DATE_TIME_MODULE_NAME, OFFSET_DATE_TIME_MODULE_VERSION);
        offsetDateTimeModule.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer());
        offsetDateTimeModule.addDeserializer(OffsetDateTime.class, new OffsetDateTimeDeserializer());
        objectMapper.registerModule(offsetDateTimeModule);

        return objectMapper;
    }
}

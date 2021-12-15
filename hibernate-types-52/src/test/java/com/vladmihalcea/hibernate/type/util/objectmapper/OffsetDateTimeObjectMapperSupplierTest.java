package com.vladmihalcea.hibernate.type.util.objectmapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class OffsetDateTimeObjectMapperSupplierTest {

    private ObjectMapper offsetDateTimeMapper = new OffsetDateTimeObjectMapperSupplier().get();

    @Test
    public void should_convert_OffsetDateTime_to_String() throws JsonProcessingException {
        OffsetDateTime currentTime = OffsetDateTime.of(2021, 12, 15, 10, 25, 17, 123456, ZoneOffset.of("+01:00"));
        String expectedResult = "\"2021-12-15T10:25:17.000123456+01:00\"";

        String actualResult = offsetDateTimeMapper.writeValueAsString(currentTime);

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void should_write_null_Object_from_OffsetDateTime_as_null_String() throws JsonProcessingException {
        OffsetDateTime currentTime = null;
        String expectedResult = "null";

        String actualResult = offsetDateTimeMapper.writeValueAsString(currentTime);

        assertEquals(expectedResult, actualResult);
    }

    @Test
    public void should_convert_a_String_to_OffsetDateTime() throws JsonProcessingException {
        String currentTime = "\"2021-12-15T10:25:17.000123456+01:00\"";
        OffsetDateTime expectedResult = OffsetDateTime.of(2021, 12, 15, 10, 25, 17, 123456, ZoneOffset.of("+01:00"));

        OffsetDateTime actualResult = offsetDateTimeMapper.readValue(currentTime, OffsetDateTime.class);

        assertEquals(expectedResult, actualResult);
    }


    @Test
    public void should_convert_a_null_String_to_a_null_OffsetDateTime() throws JsonProcessingException {
        String currentTime = "null";

        OffsetDateTime actualResult = offsetDateTimeMapper.readValue(currentTime, OffsetDateTime.class);

        assertNull(actualResult);
    }

}
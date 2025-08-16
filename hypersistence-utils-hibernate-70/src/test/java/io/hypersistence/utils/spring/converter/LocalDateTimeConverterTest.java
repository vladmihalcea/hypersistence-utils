package io.hypersistence.utils.spring.converter;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

class LocalDateTimeConverterTest {

    private final LocalDateTimeConverter converter = new LocalDateTimeConverter();

    @Test
    void testConvertToDatabaseColumn() {
        List<LocalDateTime> times = List.of(
                LocalDateTime.of(2025, 8, 16, 23, 45, 12),
                LocalDateTime.of(2025, 8, 17, 10, 0, 0)
        );

        String json = converter.convertToDatabaseColumn(times);
        Assertions.assertNotNull(json);
        Assertions.assertTrue(json.contains("2025-08-16T23:45:12"));
        Assertions.assertTrue(json.contains("2025-08-17T10:00:00"));
    }

    @Test
    void testConvertToEntityAttribute() {
        String json = "[\"2025-08-16T23:45:12\",\"2025-08-17T10:00:00\"]";

        List<LocalDateTime> result = converter.convertToEntityAttribute(json);

        Assertions.assertNotNull(result);
        Assertions.assertEquals(2, result.size());
        Assertions.assertEquals(LocalDateTime.of(2025, 8, 16, 23, 45, 12), result.get(0));
        Assertions.assertEquals(LocalDateTime.of(2025, 8, 17, 10, 0, 0), result.get(1));
    }

    @Test
    void testNullHandling() {
        Assertions.assertNull(converter.convertToDatabaseColumn(null));
        Assertions.assertNull(converter.convertToEntityAttribute(null));
    }
}

package io.hypersistence.utils.spring.converter;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ListConverterTest {

    private final LongListConverter longConverter = new LongListConverter();
    private final DoubleListConverter doubleConverter = new DoubleListConverter();
    private final StringListConverter stringConverter = new StringListConverter();

    @Test
    void testLongListConversion() {
        List<Long> longs = List.of(100L, 200L, 300L);
        String json = longConverter.convertToDatabaseColumn(longs);

        assertNotNull(json);
        assertTrue(json.contains("100"));

        List<Long> result = longConverter.convertToEntityAttribute(json);
        assertEquals(longs, result);
    }

    @Test
    void testDoubleListConversion() {
        List<Double> doubles = List.of(1.1, 2.2, 3.3);
        String json = doubleConverter.convertToDatabaseColumn(doubles);

        assertNotNull(json);
        assertTrue(json.contains("1.1"));

        List<Double> result = doubleConverter.convertToEntityAttribute(json);
        assertEquals(doubles, result);
    }

    @Test
    void testStringListConversion() {
        List<String> strings = List.of("apple", "banana", "cherry");
        String json = stringConverter.convertToDatabaseColumn(strings);

        assertNotNull(json);
        assertTrue(json.contains("apple"));

        List<String> result = stringConverter.convertToEntityAttribute(json);
        assertEquals(strings, result);
    }

    @Test
    void testNullHandling() {
        assertNull(longConverter.convertToDatabaseColumn(null));
        assertNull(longConverter.convertToEntityAttribute(null));

        assertNull(doubleConverter.convertToDatabaseColumn(null));
        assertNull(doubleConverter.convertToEntityAttribute(null));

        assertNull(stringConverter.convertToDatabaseColumn(null));
        assertNull(stringConverter.convertToEntityAttribute(null));
    }
}


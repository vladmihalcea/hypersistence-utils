package io.hypersistence.utils.spring.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalDateTime;
import java.util.List;

/**
 * JPA {@link AttributeConverter} that serializes and deserializes
 * a {@link List} of {@link LocalDateTime} values to and from a JSON string.
 *
 * <p>Uses Jackson with the {@link JavaTimeModule} registered, ensuring
 * {@code LocalDateTime} values are stored as ISO-8601 strings
 * (e.g., {@code "2025-08-16T23:45:12.123"}) rather than numeric timestamps.</p>
 *
 * <p>Example DB column content:</p>
 * <pre>
 * ["2025-08-16T23:12:45.123","2025-08-17T10:00:00"]
 * </pre>
 *
 * <p>Example entity usage:</p>
 * <pre>
 * {@code
 * @Entity
 * public class EventBatch {
 *     @Id
 *     private Long id;
 *
 *     @Convert(converter = LocalDateTimeConverter.class)
 *     private List<LocalDateTime> eventTimes;
 * }
 * }
 * </pre>
 */
@Converter
public class LocalDateTimeConverter implements AttributeConverter<List<LocalDateTime>, String> {

    /**
     * Shared Jackson ObjectMapper configured for Java 8 date/time types.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static {
        // Support Java 8 date/time types
        MAPPER.registerModule(new JavaTimeModule());
        // Store dates as ISO-8601 strings instead of timestamps
        MAPPER.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    /**
     * Converts a {@code List<LocalDateTime>} to its JSON string representation
     * for storage in the database.
     *
     * @param localDateTimes the list of LocalDateTime values (nullable)
     * @return a JSON string representation of the list, or {@code null} if input is null
     * @throws RuntimeException if serialization fails
     */
    @Override
    public String convertToDatabaseColumn(List<LocalDateTime> localDateTimes) {
        if (localDateTimes == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(localDateTimes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize List<LocalDateTime>", e);
        }
    }

    /**
     * Converts a JSON string from the database back into a {@code List<LocalDateTime>}.
     *
     * @param dbData the JSON string stored in the database (nullable)
     * @return the deserialized list of LocalDateTime values, or {@code null} if input is null
     * @throws RuntimeException if deserialization fails
     */
    @Override
    public List<LocalDateTime> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, new TypeReference<>() {
            });
        } catch (Exception e) {
            throw new RuntimeException("Failed to deserialize List<LocalDateTime>", e);
        }
    }
}

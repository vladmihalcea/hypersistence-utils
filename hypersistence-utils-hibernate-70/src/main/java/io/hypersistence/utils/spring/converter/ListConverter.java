package io.hypersistence.utils.spring.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.AttributeConverter;

import java.util.List;

/**
 * A generic JPA {@link AttributeConverter} implementation for persisting and retrieving
 * {@link java.util.List} values as JSON strings in the database.
 *
 * <p>This converter uses Jackson {@link ObjectMapper} for serialization and deserialization.
 * Subclasses should provide a concrete {@link TypeReference} for the list element type
 * (e.g. {@code new TypeReference<List<Integer>>() {}}) in order to preserve type information
 * at runtime.</p>
 *
 * <p>Example usage in an entity:</p>
 * <pre>
 * {@code
 * @Entity
 * public class MyEntity {
 *
 *     @Id
 *     private Long id;
 *
 *     @Convert(converter = ListOfIntegerConverter.class)
 *     private List<Integer> numbers;
 * }
 * }
 * </pre>
 *
 * @param <T> the element type of the list
 */
public abstract class ListConverter<T> implements AttributeConverter<List<T>, String> {

    /**
     * Shared Jackson ObjectMapper instance.
     */
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Type reference to preserve generic type information at runtime.
     */
    private final TypeReference<List<T>> typeReference;

    /**
     * Constructs a new {@code ListConverter} with the provided type reference.
     *
     * @param typeReference the Jackson {@link TypeReference} representing {@code List<T>}
     */
    protected ListConverter(TypeReference<List<T>> typeReference) {
        this.typeReference = typeReference;
    }

    /**
     * Converts a list of values into its JSON string representation for storage in the database.
     *
     * @param attribute the list of values to convert (maybe {@code null})
     * @return the JSON string representation of the list, or {@code null} if the input is null
     * @throws IllegalStateException if serialization fails
     */
    @Override
    public String convertToDatabaseColumn(List<T> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return MAPPER.writeValueAsString(attribute);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to convert list to JSON string!", ex);
        }
    }

    /**
     * Converts a JSON string from the database back into a list of values.
     *
     * @param dbData the JSON string stored in the database (maybe {@code null})
     * @return the deserialized list of values, or {@code null} if the input is null
     * @throws IllegalStateException if deserialization fails
     */
    @Override
    public List<T> convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            return MAPPER.readValue(dbData, typeReference);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to convert JSON string to list!", ex);
        }
    }
}
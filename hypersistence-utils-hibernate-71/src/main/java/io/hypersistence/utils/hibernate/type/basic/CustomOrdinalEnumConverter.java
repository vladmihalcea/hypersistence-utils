package io.hypersistence.utils.hibernate.type.basic;

import io.hypersistence.utils.common.ReflectionUtils;
import jakarta.persistence.AttributeConverter;

import java.util.HashMap;
import java.util.Map;

/**
 * Maps a Java {@link Enum} to a custom ordinal integer value.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/java-enum-custom-values/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 * @since 3.8.0
 */
public abstract class CustomOrdinalEnumConverter<T extends Enum> implements AttributeConverter<T, Integer> {

    private Map<Integer, T> customOrdinalValueToEnumMap = new HashMap<>();

    /**
     * Initialization constructor taking the Java Enum to manage.
     *
     * @param enumType Java Enum type to manage
     */
    public CustomOrdinalEnumConverter(Class<T> enumType) {
        T[] enumValues = ReflectionUtils.invokeStaticMethod(
            ReflectionUtils.getMethod(enumType, "values")
        );
        for (T enumValue : enumValues) {
            Integer customOrdinalValue = convertToDatabaseColumn(enumValue);
            customOrdinalValueToEnumMap.put(customOrdinalValue, enumValue);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public T convertToEntityAttribute(Integer ordinalValue) {
        return customOrdinalValueToEnumMap.get(ordinalValue);
    }
}

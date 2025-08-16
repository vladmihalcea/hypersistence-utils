package io.hypersistence.utils.spring.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

@Converter
public class DoubleListConverter extends ListConverter<Double> {

    public DoubleListConverter() {
        super(new TypeReference<>() {
        });
    }
}

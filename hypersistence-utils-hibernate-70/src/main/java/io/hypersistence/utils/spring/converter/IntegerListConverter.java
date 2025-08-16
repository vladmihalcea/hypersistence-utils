package io.hypersistence.utils.spring.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

@Converter
public class IntegerListConverter extends ListConverter<Integer> {

    public IntegerListConverter() {
        super(new TypeReference<>() {
        });
    }
}

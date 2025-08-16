package io.hypersistence.utils.spring.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

@Converter
public class StringListConverter extends ListConverter<String> {

    public StringListConverter() {
        super(new TypeReference<>() {
        });
    }
}

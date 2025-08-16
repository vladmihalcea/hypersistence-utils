package io.hypersistence.utils.spring.converter;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.persistence.Converter;

@Converter
public class LongListConverter extends ListConverter<Long> {

    public LongListConverter() {
        super(new TypeReference<>() {
        });
    }
}

package io.hypersistence.utils.spring.commons;

import com.fasterxml.jackson.annotation.JsonValue;

public interface BaseEnum {

    @JsonValue
    String getValue();
}

package com.vladmihalcea.hibernate.type.json.internal;


import com.fasterxml.jackson.core.type.TypeReference;

/**
 *
 * @author Fabio Grucci
 */
public class TypeReferenceJsonSerializer<T> implements JsonSerializer<T> {

    private final TypeReference<T> typeReference;

    public TypeReferenceJsonSerializer(TypeReference<T> typeReference) {
        this.typeReference = typeReference;
    }

    @Override
    public T fromString(String string) {
        return JacksonUtil.fromString(string, typeReference);
    }
}

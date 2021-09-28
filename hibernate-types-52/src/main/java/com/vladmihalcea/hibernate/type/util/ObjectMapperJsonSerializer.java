package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.Map;

/**
 * @author Vlad Mihalcea
 */
public class ObjectMapperJsonSerializer implements JsonSerializer {

    private final ObjectMapper objectMapper;

    public ObjectMapperJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public <T> T clone(T object) {
        if (object instanceof JsonNode) {
            return (T) ((JsonNode) object).deepCopy();
        }

        if (object instanceof Collection) {
            Object firstElement = findFirstNonNullElement((Collection) object);
            if (firstElement != null) {
                JavaType type = objectMapper.getTypeFactory().constructParametricType(object.getClass(), firstElement.getClass());
                return jsonClone(object, type);
            }
        }

        if (object instanceof Map) {
            Map.Entry firstEntry = this.findFirstNonNullEntry((Map) object);
            if (firstEntry != null) {
                JavaType type = objectMapper.getTypeFactory().constructParametricType(
                        object.getClass(),
                        firstEntry.getKey().getClass(), firstEntry.getValue().getClass()
                );
                return jsonClone(object, type);
            }
        }

        return jsonClone(object);
    }

    private Object findFirstNonNullElement(Collection collection) {
        for (java.lang.Object element : collection) {
            if (element != null) {
                return element;
            }
        }
        return null;
    }

    private Map.Entry findFirstNonNullEntry(Map<?, ?> map) {
        for (Map.Entry entry : map.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                return entry;
            }
        }
        return null;
    }

    private <T> T jsonClone(T object) {
        return jsonClone(object, objectMapper.constructType(object.getClass()));
    }

    private <T> T jsonClone(T object, JavaType type) {
        return objectMapper.convertValue(object, type);
    }
}
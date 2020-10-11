package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.annotation.JsonProperty;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;

import com.google.common.collect.Lists;
import org.hibernate.annotations.Type;
import org.junit.Test;

import javax.persistence.Column;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class ObjectMapperJsonSerializerTest {

    private ObjectMapperJsonSerializer serializer = new ObjectMapperJsonSerializer(new ObjectMapperWrapper());

    @Test
    public void should_clone_serializable_object() {
        Object original = new SerializableObject("value");
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_non_serializable_object() {
        Object original = new NonSerializableObject("value");
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_collection_of_serializable_object() {
        List<SerializableObject> original = new ArrayList<SerializableObject>();
        original.add(new SerializableObject("value"));
        List<SerializableObject> cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_collection_of_non_serializable_object() {
        List<NonSerializableObject> original = new ArrayList<NonSerializableObject>();
        original.add(new NonSerializableObject("value"));
        List<NonSerializableObject> cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_empty_collection() {
        List<?> original = new ArrayList();
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_non_serializable_key() {
        Map<NonSerializableObject, String> original = new HashMap<NonSerializableObject, String>();
        original.put(new NonSerializableObject("key"), "value");
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_non_serializable_value() {
        Map<String, NonSerializableObject> original = new HashMap<String, NonSerializableObject>();
        original.put("key", new NonSerializableObject("value"));
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_serializable_key_and_value() {
        Map<String, SerializableObject> original = new HashMap<String, SerializableObject>();
        original.put("key", new SerializableObject("value"));
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_with_null_value() {
        Map<String, Object> original = new HashMap<String, Object>();
        original.put("null", null);
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_non_serializable_value_with_null_value() {
        Map<String, NonSerializableObject> original = new LinkedHashMap<String, NonSerializableObject>();
        original.put("null", null);
        original.put("key", new NonSerializableObject("value"));
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_serializable_key_and_value_with_null() {
        Map<String, SerializableObject> original = new LinkedHashMap<String, SerializableObject>();
        original.put("null", null);
        original.put("key", new SerializableObject("value"));
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    private static class SerializableObject implements Serializable {
        private final String value;

        private SerializableObject(@JsonProperty("value") String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            SerializableObject that = (SerializableObject) o;

            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    private static class NonSerializableObject {
        private final String value;

        private NonSerializableObject(@JsonProperty("value") String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            NonSerializableObject that = (NonSerializableObject) o;

            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }

    @Test
    public void should_clone_serializable_complex_object() {
        Map<String, List<NestedObject>> map = new LinkedHashMap<String, List<NestedObject>>();
        map.put("key1", Lists.newArrayList(new NestedObject("name1", 5)));
        map.put("key2", Lists.newArrayList(
                new NestedObject("name1", 5),
                new NestedObject("name2", 10)
        ));
        Object original = new SerializableComplexObject(map);
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    private static class SerializableComplexObject implements Serializable {

        @Type(type = "jsonb")
        @Column(columnDefinition = "jsonb")
        private final Map<String, List<NestedObject>> value;

        private SerializableComplexObject(@JsonProperty("value") Map<String, List<NestedObject>> value) {
            this.value = value;
        }

        public Map<String, List<NestedObject>> getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            SerializableComplexObject that = (SerializableComplexObject) o;

            return value.equals(that.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value.toString();
        }
    }

    private static class NestedObject {
        public NestedObject() {
        }

        public NestedObject(String stringProperty, int intProperty) {
            this.stringProperty = stringProperty;
            this.intProperty = intProperty;
        }

        private String stringProperty;

        private int intProperty;

        public String getStringProperty() {
            return stringProperty;
        }

        public void setStringProperty(String stringProperty) {
            this.stringProperty = stringProperty;
        }

        public int getIntProperty() {
            return intProperty;
        }

        public void setIntProperty(int intProperty) {
            this.intProperty = intProperty;
        }
    }

}

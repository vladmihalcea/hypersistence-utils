package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import jakarta.persistence.Column;
import org.hibernate.annotations.Type;
import org.junit.Test;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.*;

public class ObjectMapperJsonSerializerTest {

    private ObjectMapperWrapper mapper = new ObjectMapperWrapper();

    private ObjectMapperJsonSerializer serializer = new ObjectMapperJsonSerializer();

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
        try {
            serializer.clone(original);
            fail("Should throw exception");
        } catch (Exception expected) {
            assertEquals(NonSerializableObjectException.class, expected.getClass());
        }
    }

    @Test
    public void should_clone_collection_of_serializable_object() {
        List<SerializableObject> original = new ArrayList<>();
        original.add(new SerializableObject("value"));
        List<SerializableObject> cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_collection_of_non_serializable_object() {
        List<NonSerializableObject> original = new ArrayList<>();
        original.add(new NonSerializableObject("value"));
        try {
            serializer.clone(original);
            fail("Should throw exception");
        } catch (Exception expected) {}
    }

    @Test
    public void should_clone_empty_collection() {
        List<?> original = new ArrayList<>();
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_non_serializable_key() {
        Map<NonSerializableObject, String> original = new HashMap<>();
        original.put(new NonSerializableObject("key"), "value");
        try {
            serializer.clone(original);
            fail("Should throw exception");
        } catch (Exception expected) {}
    }

    @Test
    public void should_clone_map_of_non_serializable_value() {
        Map<String, NonSerializableObject> original = new HashMap<>();
        original.put("key", new NonSerializableObject("value"));
        try {
            serializer.clone(original);
            fail("Should throw exception");
        } catch (Exception expected) {}
    }

    @Test
    public void should_clone_map_of_serializable_key_and_value() {
        Map<String, SerializableObject> original = new HashMap<>();
        original.put("key", new SerializableObject("value"));
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_with_null_value() {
        Map<String, Object> original = new HashMap<>();
        original.put("null", null);
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_non_serializable_value_with_null_value() {
        Map<String, NonSerializableObject> original = new LinkedHashMap<>();
        original.put("null", null);
        original.put("key", new NonSerializableObject("value"));
        try {
            serializer.clone(original);
            fail("Should throw exception");
        } catch (Exception expected) {}
    }

    @Test
    public void should_clone_map_of_serializable_key_and_value_with_null() {
        Map<String, SerializableObject> original = new LinkedHashMap<>();
        original.put("null", null);
        original.put("key", new SerializableObject("value"));
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_serializable_complex_object_with_serializable_nested_object() {
        Map<String, List<SerializableObject>> map = new LinkedHashMap<>();
        map.put("key1", Lists.newArrayList(new SerializableObject("name1")));
        map.put("key2", Lists.newArrayList(
            new SerializableObject("name2"),
            new SerializableObject("name3")
        ));
        Object original = new SerializableComplexObject(map);
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_serializable_complex_object_with_non_serializable_nested_object() {
        Map<String, List<NonSerializableObject>> map = new LinkedHashMap<>();
        map.put("key1", Lists.newArrayList(new NonSerializableObject("name1")));
        map.put("key2", Lists.newArrayList(
            new NonSerializableObject("name2"),
            new NonSerializableObject("name3")
        ));
        Object original = new SerializableComplexObjectWithNonSerializableNestedObject(map);
        try {
            serializer.clone(original);
            fail("Should throw exception");
        } catch (Exception expected) {}
    }

    @Test
    public void should_clone_jsonnode() {
        Object original = mapper.getObjectMapper().createArrayNode()
            .add(BigDecimal.ONE)
            .add(1.0)
            .add("string");
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_mixed_lists() {
        Map<String, List<String>> map = new LinkedHashMap<>();
        List<String> arrayList = new ArrayList<>();
        arrayList.add("arrayList");
        List<String> listOf = List.of("listOf");
        map.put("arrayList", arrayList);
        map.put("listOf", listOf);
        serializer.clone(map);
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

    private static class SerializableComplexObject implements Serializable {

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private final Map<String, List<SerializableObject>> value;

        private SerializableComplexObject(@JsonProperty("value") Map<String, List<SerializableObject>> value) {
            this.value = value;
        }

        public Map<String, List<SerializableObject>> getValue() {
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

    private static class SerializableComplexObjectWithNonSerializableNestedObject implements Serializable {

        @Type(JsonBinaryType.class)
        @Column(columnDefinition = "jsonb")
        private final Map<String, List<NonSerializableObject>> value;

        private SerializableComplexObjectWithNonSerializableNestedObject(@JsonProperty("value") Map<String, List<NonSerializableObject>> value) {
            this.value = value;
        }

        public Map<String, List<NonSerializableObject>> getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (o == null || getClass() != o.getClass())
                return false;

            SerializableComplexObjectWithNonSerializableNestedObject that = (SerializableComplexObjectWithNonSerializableNestedObject) o;

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
}

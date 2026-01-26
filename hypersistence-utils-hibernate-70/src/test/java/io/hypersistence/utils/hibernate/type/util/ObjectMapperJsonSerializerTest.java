package io.hypersistence.utils.hibernate.type.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.collect.Lists;
import io.hypersistence.utils.hibernate.type.json.JsonBinaryType;
import org.hibernate.annotations.Type;
import org.junit.Test;

import jakarta.persistence.Column;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.*;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

public class ObjectMapperJsonSerializerTest {

    private ObjectMapperWrapper mapper = new ObjectMapperWrapper();

    private ObjectMapperJsonSerializer serializer = new ObjectMapperJsonSerializer(mapper);

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
        List<NonSerializableObject> cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
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
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_map_of_non_serializable_value() {
        Map<String, NonSerializableObject> original = new HashMap<>();
        original.put("key", new NonSerializableObject("value"));
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
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
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
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
        Object cloned = serializer.clone(original);
        assertEquals(original, cloned);
        assertNotSame(original, cloned);
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

    @Test
    public void should_clone_matrix_with_non_serializable_nested_object() {
        Object original = List.of(
                List.of(new NonSerializableObject("name1")),
                List.of(new NonSerializableObject("name2"))
        );

        Object cloned = serializer.clone(original);

        assertEquals(original, cloned);
        assertNotSame(original, cloned);
    }

    @Test
    public void should_clone_matrix_with_non_serializable_complex_nested_object() {
        NonSerializableComplexObject object1 = new NonSerializableComplexObject("name1", 123);
        object1.setAttributes(Collections.singletonList("attr1"));
        object1.setNested(new NestedObject("nested1"));

        NonSerializableComplexObject object2 = new NonSerializableComplexObject("name2", 321);
        object2.setAttributes(List.of("attr2", "attr3", "attr4"));
        object2.setNested(new NestedObject("nested2"));

        List<List<NonSerializableComplexObject>> matrix = new ArrayList<>();
        matrix.add(Collections.singletonList(object1));
        matrix.add(Collections.singletonList(object2));

        Object cloned = serializer.clone(matrix);

        assertTrue(cloned instanceof List);
        List<?> outerList = (List<?>) cloned;
        assertEquals(2, outerList.size());

        Object inner1 = outerList.get(0);
        assertTrue("Inner element 1 should be a List", inner1 instanceof List);
        List<?> innerList1 = (List<?>) inner1;
        assertEquals(1, innerList1.size());

        Object element1 = innerList1.get(0);
        assertTrue(
            "Element 1 should be instance of NonSerializableComplexObject but was " + element1.getClass().getName(),
            element1 instanceof NonSerializableComplexObject
        );

        NonSerializableComplexObject clonedObject1 = (NonSerializableComplexObject) element1;
        assertEquals("name1", clonedObject1.getName());
        assertEquals(123, clonedObject1.getValue());
        assertEquals(1, clonedObject1.getAttributes().size());
        assertEquals("attr1", clonedObject1.getAttributes().get(0));
        assertEquals("nested1", clonedObject1.getNested().getDescription());

        Object inner2 = outerList.get(1);
        assertTrue("Inner element 2 should be a List", inner2 instanceof List);
        List<?> innerList2 = (List<?>) inner2;
        assertEquals(1, innerList2.size());

        Object element2 = innerList2.get(0);
        assertTrue(
            "Element 2 should be instance of NonSerializableComplexObject but was " + element2.getClass().getName(),
            element2 instanceof NonSerializableComplexObject
        );

        NonSerializableComplexObject clonedObject2 = (NonSerializableComplexObject) element2;
        assertEquals("name2", clonedObject2.getName());
        assertEquals(321, clonedObject2.getValue());
        assertEquals(3, clonedObject2.getAttributes().size());
        assertEquals("attr2", clonedObject2.getAttributes().get(0));
        assertEquals("attr3", clonedObject2.getAttributes().get(1));
        assertEquals("attr4", clonedObject2.getAttributes().get(2));
        assertEquals("nested2", clonedObject2.getNested().getDescription());
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

    private static class NonSerializableComplexObject {
        private String name;
        private int value;
        private List<String> attributes;
        private NestedObject nested;

        public NonSerializableComplexObject() {
        }

        public NonSerializableComplexObject(String name, int value) {
            this.name = name;
            this.value = value;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public List<String> getAttributes() {
            return attributes;
        }

        public void setAttributes(List<String> attributes) {
            this.attributes = attributes;
        }

        public NestedObject getNested() {
            return nested;
        }

        public void setNested(NestedObject nested) {
            this.nested = nested;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NonSerializableComplexObject that = (NonSerializableComplexObject) o;
            return value == that.value &&
                Objects.equals(name, that.name) &&
                Objects.equals(attributes, that.attributes) &&
                Objects.equals(nested, that.nested);
        }

        @Override
        public int hashCode() {
            return Objects.hash(name, value, attributes, nested);
        }
    }

    private static class NestedObject {
        private String description;

        public NestedObject() {
        }

        public NestedObject(String description) {
            this.description = description;
        }

        @Override
        public boolean equals(Object o) {
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NestedObject that = (NestedObject) o;
            return Objects.equals(description, that.description);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(description);
        }

        public String getDescription() {
            return description;
        }
    }
}

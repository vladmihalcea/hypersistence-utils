package com.vladmihalcea.hibernate.type.util;

import com.fasterxml.jackson.annotation.JsonProperty;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import org.junit.Test;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;

public class ObjectMapperJsonSerializerTest {
    private ObjectMapperJsonSerializer serializer = new ObjectMapperJsonSerializer(new ObjectMapperWrapper());

    @Test
    public void should_clone_serializable_object() {
        Object org = new SerializableObj("s");
        Object cloned = serializer.clone(org);
        assertEquals(org, cloned);
        assertNotSame(org, cloned);
    }

    @Test
    public void should_clone_non_serializable_object() {
        Object org = new NonSerializableObj("s");
        Object cloned = serializer.clone(org);
        assertEquals(org, cloned);
        assertNotSame(org, cloned);
    }

    @Test
    public void should_clone_collection_of_serializable_object() {
        Object org = Arrays.asList(new SerializableObj("s"));
        Object cloned = serializer.clone(org);
        assertEquals(org, cloned);
        assertNotSame(org, cloned);
    }

    @Test
    public void should_clone_collection_of_non_serializable_object() {
        Object org = Arrays.asList(new NonSerializableObj("s"));
        Object cloned = serializer.clone(org);
        assertEquals(org, cloned);
        assertNotSame(org, cloned);
    }

    @Test
    public void should_clone_empty_collection() {
        Object org = new ArrayList<>();
        Object cloned = serializer.clone(org);
        assertEquals(org, cloned);
        assertNotSame(org, cloned);
    }

    private static class SerializableObj implements Serializable {
        private final String s;

        private SerializableObj(String s) {
            this.s = s;
        }

        public String getS() {
            return s;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof SerializableObj))
                return false;
            SerializableObj that = (SerializableObj) o;
            return Objects.equals(s, that.s);
        }

        @Override
        public int hashCode() {
            return Objects.hash(s);
        }
    }
    private static class NonSerializableObj {
        private final String s;

        private NonSerializableObj(@JsonProperty("s") String s) {
            this.s = s;
        }

        public String getS() {
            return s;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof NonSerializableObj))
                return false;
            NonSerializableObj that = (NonSerializableObj) o;
            return Objects.equals(s, that.s);
        }

        @Override
        public int hashCode() {
            return Objects.hash(s);
        }
    }

}

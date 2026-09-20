package io.hypersistence.utils.hibernate.type.json.internal;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.hypersistence.utils.hibernate.type.util.ObjectMapperWrapper;
import org.hibernate.HibernateException;
import org.junit.Test;
import org.postgresql.util.PGobject;

import java.sql.SQLException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class JsonJavaTypeDescriptorWrapTest {

    @Test
    public void testJdbcWrapperPreservesOriginalException() throws SQLException {
        String json = "{\"name\":\"test\"}";
        PGobject value = jsonb(json);
        ObjectMapperWrapper mapper = mock(ObjectMapperWrapper.class);
        HibernateException failure = new HibernateException("Original deserialization failure");
        when(mapper.fromString(json, (java.lang.reflect.Type) Value.class)).thenThrow(failure);
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor(Value.class, mapper);

        assertSame(failure, assertThrows(HibernateException.class, () -> descriptor.wrap(value, null)));
        verify(mapper, never()).toString(value);
    }

    @Test
    public void testJsonStringPreservesOriginalException() {
        String json = "{\"name\":\"test\"}";
        ObjectMapperWrapper mapper = mock(ObjectMapperWrapper.class);
        HibernateException failure = new HibernateException("Original deserialization failure");
        when(mapper.fromString(json, (java.lang.reflect.Type) Value.class)).thenThrow(failure);
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor(Value.class, mapper);

        assertSame(failure, assertThrows(HibernateException.class, () -> descriptor.wrap(json, null)));
        verify(mapper, never()).toString(json);
    }

    @Test
    public void testMissingPolymorphicTypeIsNotReplacedByJdbcType() throws SQLException {
        String json = "{\"name\":\"test\"}";
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor(Notification.class);
        HibernateException expected = assertThrows(HibernateException.class, () -> descriptor.fromString(json));
        PGobject value = jsonb(json);

        HibernateException actual = assertThrows(HibernateException.class, () -> descriptor.wrap(value, null));

        assertEquals(expected.getCause().getMessage(), actual.getCause().getMessage());
        assertEquals(expected.getCause().getCause().getMessage(), actual.getCause().getCause().getMessage());
    }

    @Test
    public void testPojoQueryParameter() {
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor(Value.class);
        Value result = (Value) descriptor.wrap(new Value("test"), null);

        assertEquals("test", result.getName());
    }

    @Test
    public void testUnconfiguredStringParameter() {
        assertEquals("test", new JsonJavaTypeDescriptor().wrap("test", null));
    }

    private PGobject jsonb(String json) throws SQLException {
        PGobject value = new PGobject();
        value.setType("jsonb");
        value.setValue(json);
        return value;
    }

    public static final class Value {

        private final String name;

        @JsonCreator
        public Value(@JsonProperty("name") String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
    public abstract static class Notification {

        public abstract String getName();
    }
}

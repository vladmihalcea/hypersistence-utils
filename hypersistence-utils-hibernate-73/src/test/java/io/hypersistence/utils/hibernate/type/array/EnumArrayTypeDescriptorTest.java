package io.hypersistence.utils.hibernate.type.array;

import io.hypersistence.utils.hibernate.type.array.internal.EnumArrayTypeDescriptor;
import org.hibernate.usertype.DynamicParameterizedType;
import org.junit.Test;

import java.lang.annotation.Annotation;
import java.util.Properties;

import static org.junit.Assert.assertEquals;

/**
 * Unit test for Envers-style {@link DynamicParameterizedType.ParameterType} where
 * {@link DynamicParameterizedType.ParameterType#getReturnedJavaType()} is null.
 */
public class EnumArrayTypeDescriptorTest {

    public enum SampleRole {
        ROLE_ADMIN,
        ROLE_USER
    }

    @Test
    public void setParameterValuesUsesReturnedClassWhenReturnedJavaTypeIsNull() {
        EnumArrayTypeDescriptor descriptor = new EnumArrayTypeDescriptor();

        Properties parameters = new Properties();
        parameters.setProperty("sql_array_type", "user_role");
        parameters.put(DynamicParameterizedType.PARAMETER_TYPE, new ParameterTypeWithNullJavaType(SampleRole[].class));

        descriptor.setParameterValues(parameters);

        assertEquals(SampleRole[].class, descriptor.getArrayObjectClass());
    }

    private static final class ParameterTypeWithNullJavaType implements DynamicParameterizedType.ParameterType {

        private final Class<?> returnedClass;

        private ParameterTypeWithNullJavaType(Class<?> returnedClass) {
            this.returnedClass = returnedClass;
        }

        @Override
        public Class<?> getReturnedClass() {
            return returnedClass;
        }

        @Override
        public java.lang.reflect.Type getReturnedJavaType() {
            return null;
        }

        @Override
        public Annotation[] getAnnotationsMethod() {
            return new Annotation[0];
        }

        @Override
        public String getCatalog() {
            return null;
        }

        @Override
        public String getSchema() {
            return null;
        }

        @Override
        public String getTable() {
            return null;
        }

        @Override
        public boolean isPrimaryKey() {
            return false;
        }

        @Override
        public String[] getColumns() {
            return new String[0];
        }

        @Override
        public Long[] getColumnLengths() {
            return new Long[0];
        }
    }
}

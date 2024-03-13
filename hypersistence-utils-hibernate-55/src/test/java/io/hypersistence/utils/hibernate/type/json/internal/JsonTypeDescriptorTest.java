package io.hypersistence.utils.hibernate.type.json.internal;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import org.hibernate.HibernateException;
import org.hibernate.type.descriptor.WrapperOptions;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

@RunWith(MockitoJUnitRunner.class)
public class JsonTypeDescriptorTest {
    private @Mock WrapperOptions wrapperOptions;

    /**
     * If JSON serialization is used,
     * the {@link JsonTypeDescriptor#areEqual(Object, Object)} depends on the order of the elements.
     * <p>
     * If the first collection contains the all element of another collection,
     * then the two collection are equaled.
     * <p>
     * If the JSON object of the `theFirst` form would be :
     * {
     * "formFields":[1, 2, 3]
     * }
     * <p>
     * And, the JSON object of the `theSecond` form would be:
     * {
     * "formFields":[3, 2, 1]
     * }
     * <p>
     * The two JSON objects should be equal.
     */
    @Test
    public void testSetsAreEqual() {
        JsonTypeDescriptor descriptor = new JsonTypeDescriptor();

        Form theFirst = createForm(1, 2, 3);
        Form theSecond = createForm(3, 2, 1);
        assertTrue(descriptor.areEqual(theFirst, theSecond));
    }

    @Test
    public void testNullPropertyType() {
        JsonTypeDescriptor descriptor = new JsonTypeDescriptor();

        try {
            descriptor.wrap("a", null);
            fail("Should fail because the propertyType is null!");
        } catch (HibernateException expected) {
        }
    }

    @Test
    public void testCollectionPropertyTypes() {
        JsonTypeDescriptor listDescriptor = new JsonTypeDescriptor(new TestParameterizedTypeImpl(String.class, ArrayList.class));
        JsonTypeDescriptor setDescriptor = new JsonTypeDescriptor(new TestParameterizedTypeImpl(String.class, HashSet.class));
        List<String> expectedArrayListValue = new ArrayList<>();
        expectedArrayListValue.add("one");
        expectedArrayListValue.add("two");
        Set<String> expectedHashSetValue = new HashSet<>();
        expectedHashSetValue.add("one");
        expectedHashSetValue.add("two");

        assertEquals(expectedArrayListValue, listDescriptor.wrap("[\"one\",\"two\"]", wrapperOptions));
        assertEquals(expectedHashSetValue, setDescriptor.wrap("[\"one\",\"two\"]", wrapperOptions));
    }

    private Form createForm(Integer... numbers) {
        Form form = new Form();

        Set<FormField> formFields = new LinkedHashSet<>();

        Arrays.asList(numbers).forEach(o -> {
            FormField formField = new FormField();
            formField.setNumber(o);
            formFields.add(formField);
        });

        form.setFormFields(formFields);

        return form;
    }

    public static class FormField {

        private Integer number;

        public Integer getNumber() {
            return number;
        }

        public void setNumber(Integer number) {
            this.number = number;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            FormField formField = (FormField) o;
            return Objects.equals(number, formField.number);
        }

        @Override
        public int hashCode() {
            return Objects.hash(number);
        }
    }

    public static class Form extends BaseEntity {

        private Set<FormField> formFields;

        public Set<FormField> getFormFields() {
            return formFields;
        }

        public void setFormFields(Set<FormField> formFields) {
            this.formFields = formFields;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Form)) return false;
            Form form = (Form) o;
            return Objects.equals(formFields, form.formFields);
        }

        @Override
        public int hashCode() {
            return Objects.hash(formFields);
        }
    }

    private static class TestParameterizedTypeImpl implements ParameterizedType {
        private final Class<?> actualType;
        private final Class<?> rawType;

        public TestParameterizedTypeImpl(Class<?> actualType, Class<?> rawType) {
            this.actualType = actualType;
            this.rawType = rawType;
        }

        @Override
        public Type[] getActualTypeArguments() {
            return new Type[]{actualType};
        }

        @Override
        public Type getRawType() {
            return rawType;
        }

        @Override
        public Type getOwnerType() {
            return null;
        }
    }
}

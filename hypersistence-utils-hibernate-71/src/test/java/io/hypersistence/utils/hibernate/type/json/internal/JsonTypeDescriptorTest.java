package io.hypersistence.utils.hibernate.type.json.internal;

import io.hypersistence.utils.hibernate.type.model.BaseEntity;
import org.hibernate.HibernateException;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class JsonTypeDescriptorTest {

    /**
     * If JSON serialization is used,
     * the {@link JsonJavaTypeDescriptor#areEqual(Object, Object)} depends on the order of the elements.
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
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor();

        Form theFirst = createForm(1, 2, 3);
        Form theSecond = createForm(3, 2, 1);
        assertTrue(descriptor.areEqual(theFirst, theSecond));
    }

    /**
     * When the JSON object contains an explicit equals method, even when defined on the abstract super class {@link AbstractForm},
     * that equals method should be used.
     * If JSON serialization is used,
     * the {@link JsonJavaTypeDescriptor#areEqual(Object, Object)} depends on the values of the fields.
     *
     * The equals method of the {@link FormImpl} always returns true, so the two objects should be equal,
     * even when having a different value.
     */
    @Test
    public void testAbstractClassImplementationsAreEqual() {
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor();

        FormImpl firstEntity = new FormImpl("value1");
        FormImpl secondEntity = new FormImpl("value2");

        assertTrue(descriptor.areEqual(firstEntity, secondEntity));
    }

    /**
     * When the JSON object does not contain an explicit equals method, it should not use the equals method {@link Object},
     * but should use the ObjectWrapperMapper to equal the objects as JsonNodes
     */
    @Test
    public void testClassesWithoutEqualsMethodShouldEqualAsJsonNodes() {
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor();

        FormWithoutEqualsMethod firstEntity = new FormWithoutEqualsMethod("value1");
        FormWithoutEqualsMethod secondEntity = new FormWithoutEqualsMethod("value1");

        assertTrue(descriptor.areEqual(firstEntity, secondEntity));
    }

    @Test
    public void testNullPropertyType() {
        JsonJavaTypeDescriptor descriptor = new JsonJavaTypeDescriptor();

        descriptor.wrap("a", null);
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

    private static class FormImpl extends AbstractForm {
        private FormImpl(String value) {
            super(value);
        }
    }

    private static abstract class AbstractForm {
        private String value;

        private AbstractForm(String value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object o) {
            return true;
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(value);
        }
    }

    private static class FormWithoutEqualsMethod {
        private String value;

        public String getValue() {
            return value;
        }

        private FormWithoutEqualsMethod(String value) {
            this.value = value;
        }
    }
}

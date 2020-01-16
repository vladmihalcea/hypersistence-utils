package com.vladmihalcea.hibernate.type.json.internal;

import com.vladmihalcea.hibernate.type.model.Form;
import com.vladmihalcea.hibernate.type.model.FormField;
import com.vladmihalcea.hibernate.type.util.ObjectMapperWrapper;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.Assert.*;

public class JsonTypeDescriptorTest {

    private final ObjectMapperWrapper mapperWrapper = new ObjectMapperWrapper();
    private final JsonTypeDescriptor descriptor = new JsonTypeDescriptor(mapperWrapper);


    /**
     * Compare two collection is equal or not is`t depends the order of the elements.
     * If the first collection contains the all element of another collection then the two collection are equaled.
     *
     *
     * The json node of the `theFirst` form would be :
     *  {
     *      "formFields":[1, 2, 3]
     *  }
     *
     * The json node of the `theSecond` form would be:
     * {
     *   "formFields":[3, 2, 1]
     * }
     *
     * If you compare the two json node the result would not be true and that is the problem I was faced.
     *
     *
     */
    @Test
    public void unexpectedDirtyCheckingErrorTest() {
        Form theFirst  = createForm(1, 2, 3);
        Form theSecond = createForm(3, 2, 1);
        assert descriptor.areEqual(theFirst, theSecond);
    }

    /**
     * I added the lombok dependency in pom.xml
     *
     * In my case I am using lombok to generate equals and the hashcode method.
     * So I add the check to fix the problem: if the classes of the one and the another are same, then call the equals method to determined the two are equal or not.
     */
    @Test
    public void unexpectedDirtyCheckingFixedTest() {
        Form theFirst = createForm(1, 2, 3);
        Form theSecond = createForm(3, 2 ,1);
        assert descriptor.areEqualFixed(theFirst, theSecond);
    }

    private Form createForm(Integer... numbers) {
        Form form = new Form();
        form.setFormFields(createFormFields(numbers));

        return form;
    }

    private Set<FormField> createFormFields(Integer... number) {
       Set<FormField> formFields = new LinkedHashSet<>();
       Arrays.asList(number).forEach(o -> {
           FormField formField = new FormField();
           formField.setNumber(o);
           formFields.add(formField);
       });

       return formFields;
    }
}

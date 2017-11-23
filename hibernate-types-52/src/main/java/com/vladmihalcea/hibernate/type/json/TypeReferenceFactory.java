package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.core.type.TypeReference;

/**
 * @author Fabio Grucci
 */
public interface TypeReferenceFactory {

    String FACTORY_CLASS = "com.vladmihalcea.hibernate.type.json.TypeReferenceFactory.class";

    TypeReference<?> newTypeReference();

}
package com.vladmihalcea.hibernate.type.json;

import com.fasterxml.jackson.core.type.TypeReference;

public interface TypeReferenceFactory {

    String FACTORY_CLASS = "com.vladmihalcea.hibernate.type.json.TypeReferenceFactory.class";

    TypeReference<?> newTypeReference();

}
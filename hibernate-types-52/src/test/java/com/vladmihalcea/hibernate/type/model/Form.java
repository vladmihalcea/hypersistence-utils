package com.vladmihalcea.hibernate.type.model;


import lombok.Data;
import lombok.EqualsAndHashCode;

import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
public class Form extends BaseEntity{
    private Set<FormField> formFields;
}

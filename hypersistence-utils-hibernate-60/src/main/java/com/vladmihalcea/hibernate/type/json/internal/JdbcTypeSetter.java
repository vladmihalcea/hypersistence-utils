package com.vladmihalcea.hibernate.type.json.internal;

import org.hibernate.type.descriptor.jdbc.JdbcType;

/**
 * @author Vlad Mihalcea
 */
public interface JdbcTypeSetter {

    void setJdbcType(JdbcType jdbcType);
}

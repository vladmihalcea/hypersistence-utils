package com.vladmihalcea.hibernate.type.array;

import org.hibernate.dialect.PostgreSQL82Dialect;

import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQL82ArrayDialect extends PostgreSQL82Dialect {

    public PostgreSQL82ArrayDialect() {
        super();
        this.registerColumnType(Types.ARRAY, "array");
    }
}

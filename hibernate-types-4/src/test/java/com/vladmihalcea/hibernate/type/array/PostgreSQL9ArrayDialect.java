package com.vladmihalcea.hibernate.type.array;

import org.hibernate.dialect.PostgreSQL9Dialect;

import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQL9ArrayDialect extends PostgreSQL9Dialect {

    public PostgreSQL9ArrayDialect() {
        super();
        this.registerColumnType(Types.ARRAY, "array");
    }
}

package com.vladmihalcea.hibernate.type.array;

import org.hibernate.dialect.PostgreSQL95Dialect;

import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQL95ArrayDialect extends PostgreSQL95Dialect {

    public PostgreSQL95ArrayDialect() {
        super();
        this.registerColumnType(Types.ARRAY, "array");
    }
}

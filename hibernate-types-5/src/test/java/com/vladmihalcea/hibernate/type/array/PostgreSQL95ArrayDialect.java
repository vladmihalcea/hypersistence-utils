package com.vladmihalcea.hibernate.type.array;

import org.hibernate.dialect.PostgreSQL94Dialect;

import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQL95ArrayDialect extends PostgreSQL94Dialect {

    public PostgreSQL95ArrayDialect() {
        super();
        this.registerColumnType(Types.ARRAY, "array");
    }
}

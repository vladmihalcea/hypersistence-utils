package io.hypersistence.utils.hibernate.type.array;

import org.hibernate.dialect.PostgreSQL94Dialect;

import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSQL94ArrayDialect extends PostgreSQL94Dialect {

    public PostgreSQL94ArrayDialect() {
        super();
        this.registerColumnType(Types.ARRAY, "array");
    }
}

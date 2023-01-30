package io.hypersistence.utils.spring.repo.projection;

import org.hibernate.dialect.PostgreSQL10Dialect;

import java.sql.Types;

/**
 * @author Vlad Mihalcea
 */
public class PostgreSqlListDialect extends PostgreSQL10Dialect {

    public PostgreSqlListDialect() {
        super();
//        this.registerColumnType(Types.ARRAY, "array");
        this.registerHibernateType(Types.ARRAY, "array");
    }
}

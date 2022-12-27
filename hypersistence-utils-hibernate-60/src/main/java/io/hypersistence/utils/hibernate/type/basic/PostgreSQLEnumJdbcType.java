package io.hypersistence.utils.hibernate.type.basic;

import org.hibernate.dialect.PostgreSQLPGObjectJdbcType;
import org.hibernate.type.SqlTypes;

/**
 * Maps an {@link Enum} to a PostgreSQL ENUM column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class PostgreSQLEnumJdbcType extends PostgreSQLPGObjectJdbcType {

    public PostgreSQLEnumJdbcType(String enumClass) {
        super(enumClass, SqlTypes.OTHER);
    }
}

package com.vladmihalcea.hibernate.type.basic;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SessionImplementor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps an {@link Enum} to a PostgreSQL ENUM column type.
 * <p>
 * For more details about how to use it, check out <a href="https://vladmihalcea.com/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/">this article</a> on <a href="https://vladmihalcea.com/">vladmihalcea.com</a>.
 *
 * @author Vlad Mihalcea
 */
public class PostgreSQLEnumType extends org.hibernate.type.EnumType {

    public static final PostgreSQLEnumType INSTANCE = new PostgreSQLEnumType();

    public void nullSafeSet(
            PreparedStatement st,
            Object value,
            int index,
            SessionImplementor session)
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value.toString(), Types.OTHER);
        }
    }
}

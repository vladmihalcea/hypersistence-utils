package com.vladmihalcea.hibernate.type.basic;

import org.hibernate.HibernateException;
import org.hibernate.engine.spi.SharedSessionContractImplementor;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;

/**
 * Maps a Java Enum to a PostgreSQL ENUM column type.
 *
 * @see <a href="https://vladmihalcea.com/2017/09/19/the-best-way-to-map-an-enum-type-with-jpa-and-hibernate/">this article</a> for more info.
 *
 * @author Vlad Mihalcea
 */
public class PostgreSQLEnumType extends org.hibernate.type.EnumType {

    public void nullSafeSet(
            PreparedStatement st,
            Object value,
            int index,
            SharedSessionContractImplementor session)
            throws HibernateException, SQLException {
        if (value == null) {
            st.setNull(index, Types.OTHER);
        } else {
            st.setObject(index, value.toString(), Types.OTHER);
        }
    }
}
